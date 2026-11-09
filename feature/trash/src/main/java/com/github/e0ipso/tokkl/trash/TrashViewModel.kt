package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.platform.PageLoads
import com.github.e0ipso.tokkl.platform.stateInPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.toServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** A change the server accepted, for the message that confirms it. */
sealed interface TrashChange {
    data object Restored : TrashChange

    data object Purged : TrashChange

    /** The trash is empty now, after [count] entries went for good. */
    data class Emptied(val count: Int) : TrashChange
}

/**
 * What the trash page shows.
 *
 * @property entries the deleted entries from the server, or null until they load.
 * @property loadProblem why [entries] did not load, or why the server could not be reached for
 *   the last change. While it is set, nothing can be changed.
 * @property confirmingEmpty the question whether to empty the trash is open.
 * @property sending a change is on its way to the server.
 * @property changeProblem why the server refused the last change, or null.
 * @property done the change the server last accepted, until the page has said so.
 */
@Immutable
data class TrashState(
    val entries: List<TrashEntry>? = null,
    val loading: Boolean = false,
    val loadProblem: ServerProblem? = null,
    val confirmingEmpty: Boolean = false,
    val sending: Boolean = false,
    val changeProblem: ServerProblem? = null,
    val done: TrashChange? = null,
) {
    /** Whether a change can go to the server now, as far as the page knows. */
    val canChange: Boolean get() = entries != null && loadProblem == null && !sending
}

/**
 * The trash page: what the server sent and what is open over it in [trash], whether the phone is
 * [offline], and the account [settings] that its times, dates and durations follow.
 */
@Immutable
data class TrashPageState(
    val trash: TrashState = TrashState(),
    val offline: Boolean = false,
    val settings: AccountSettings? = null,
) {
    /** Whether the page says that the trash needs the server. */
    val serverNeeded: Boolean get() = offline || trash.loadProblem == ServerProblem.Unreachable

    /** Whether a change can go to the server now. */
    val canChange: Boolean get() = trash.canChange && !offline

    /** The deleted entries once loaded, and none while the server is needed: the phone keeps no copy. */
    val entries: List<TrashEntry>? get() = trash.entries.takeUnless { serverNeeded }

    /** Whether a notice shows above the list. */
    val hasNotices: Boolean
        get() = serverNeeded || trash.loadProblem != null || (trash.loading && trash.entries == null) ||
            (trash.changeProblem != null && !trash.confirmingEmpty)
}

/**
 * The trash page's state (docs/PRD.md H7): the deleted entries from `GET /api/app/trash`, loaded
 * each time the page opens and each time the app comes back to it, and the restores and purges the
 * page sends at once. A load keeps the list and the question open over it until the answer comes;
 * they go with the view model, when the page's route leaves the back stack. Nothing is queued, so
 * offline nothing changes, as on the catalog pages.
 * Each change goes through [changes], which after a restore reloads the offline cache, so the
 * history shows the entry again.
 *
 * A server without the trash answers the list with a 404, or with something that is not a list.
 * Then the page tells the [capabilities], on [io], which hide the page, rather than the page
 * failing.
 *
 * Call every function on the main thread.
 *
 * @param repository the account's trash on the server. Runs on [io].
 * @param changes sends each change in the application's scope.
 * @param settings the account settings, for the times, dates and durations the page shows.
 * @param offline whether the phone is offline, when nothing can change.
 * @param app the app's trips to the background and the network's returns, which load the page again
 *   while it shows ([stateInPage]).
 */
class TrashViewModel(
    private val repository: TrashRepository,
    private val changes: ServerChanges,
    private val capabilities: CapabilitiesSource,
    scope: CoroutineScope,
    private val io: CoroutineContext,
    settings: StateFlow<AccountSettings?>,
    private val offline: StateFlow<Boolean>,
    app: AppForeground,
) : ViewModel(scope) {

    private val mutableState = MutableStateFlow(TrashState())

    /** The trash and what is open over it, before the network and the settings join them in [page]. */
    internal val state: StateFlow<TrashState> = mutableState.asStateFlow()

    /** Whether the page has shown, so it has asked for the trash. */
    private var opened = false
    private var loading: Job? = null

    /** What the page shows, while it reads it. */
    val page: StateFlow<TrashPageState> = combine(state, offline, settings, ::TrashPageState).stateInPage(
        viewModelScope,
        TrashPageState(state.value, offline.value, settings.value),
        app,
        object : PageLoads {
            /** The page shows: loads the trash. */
            override fun shown() {
                opened = true
                load()
            }

            /** Back from the background: the trash may have changed meanwhile. */
            override fun returned() = retry()

            /** The network came back. Loads again when the server could not be reached. */
            override fun reconnected() {
                if (opened && state.value.loadProblem != null) load()
            }
        },
    )

    /** Loads the trash again, as after a failure. */
    fun retry() {
        if (opened) load()
    }

    /** The page has said that [TrashState.done] happened. */
    fun doneShown() {
        mutableState.update { it.copy(done = null) }
    }

    /** Opens the question whether to empty the trash, while there is something to empty. */
    fun askToEmpty() {
        val current = state.value
        if (opened && current.canChange && !current.entries.isNullOrEmpty()) {
            mutableState.update { it.copy(confirmingEmpty = true, changeProblem = null) }
        }
    }

    /** Closes the question, which keeps the trash as it is. */
    fun dismiss() {
        mutableState.update { it.copy(confirmingEmpty = false, changeProblem = null) }
    }

    /** Puts entry [id] back among the time entries. */
    fun restore(id: EntryId) {
        send(
            request = { it.restore(id) },
            apply = { entries, _ -> entries.filterNot { it.id == id } },
            done = { TrashChange.Restored },
            restored = true,
        )
    }

    /** Deletes entry [id] for good, with no question, as the web app does. */
    fun purge(id: EntryId) {
        send(
            request = { it.purge(id) },
            apply = { entries, _ -> entries.filterNot { it.id == id } },
            done = { TrashChange.Purged },
        )
    }

    /** Empties the trash, once the question is open. */
    fun empty() {
        if (!state.value.confirmingEmpty) return
        send(
            request = { it.purgeAll() },
            apply = { _, _ -> emptyList() },
            done = { TrashChange.Emptied(it) },
        )
    }

    /**
     * Sends [request], unless a change cannot go now. Once the server accepts it, [apply] changes
     * the list, the question closes and [done] says what happened. A refusal keeps the question
     * open with the reason, and a 404, an entry no longer in the trash, loads the list again. An
     * unreachable server turns every action off until a load gets through. Once sent, the change
     * and the cache refresh after a restore outlive the view model.
     */
    private fun <R> send(
        request: suspend (TrashRepository) -> ApiResult<R>,
        apply: (List<TrashEntry>, R) -> List<TrashEntry>,
        done: (R) -> TrashChange,
        restored: Boolean = false,
    ) {
        if (!opened || !state.value.canChange) return
        mutableState.update { it.copy(sending = true, changeProblem = null) }
        val sent = changes.send(refresh = restored) { request(repository) }
        viewModelScope.launch {
            when (val answer = sent.await()) {
                is ApiResult.Success -> mutableState.update { current ->
                    current.copy(
                        entries = current.entries?.let { apply(it, answer.value) },
                        sending = false,
                        confirmingEmpty = false,
                        done = done(answer.value),
                    )
                }

                is ApiResult.Failure -> {
                    val problem = answer.error.toServerProblem()
                    mutableState.update { current ->
                        if (problem == ServerProblem.Unreachable) {
                            current.copy(sending = false, loadProblem = problem)
                        } else {
                            current.copy(sending = false, changeProblem = problem)
                        }
                    }
                    if ((answer.error as? ApiError.UnexpectedStatus)?.status == NOT_FOUND) load()
                }
            }
        }
    }

    private fun load() {
        loading?.cancel()
        mutableState.update { it.copy(loading = true) }
        loading = viewModelScope.launch {
            // A cancelled load cancels its request and drops its answer.
            val answer = withContext(io) {
                repository.list().also {
                    if (it is ApiResult.Failure && it.error.meansNoTrash()) capabilities.trashMissing()
                }
            }
            when (answer) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(entries = answer.value, loading = false, loadProblem = null)
                }

                is ApiResult.Failure -> mutableState.update {
                    it.copy(loading = false, loadProblem = answer.error.toServerProblem())
                }
            }
        }
    }

    private companion object {
        const val NOT_FOUND = 404
    }
}

/** A 404, or a 2xx that is not a list, as the capability probe reads them: the server has no trash. */
private fun ApiError.meansNoTrash(): Boolean =
    (this is ApiError.UnexpectedStatus && status == HttpStatus.NOT_FOUND) || this is ApiError.InvalidResponse
