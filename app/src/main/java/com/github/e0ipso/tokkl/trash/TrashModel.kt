package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.catalog.CatalogPageModel
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.toServerProblem
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * The trash page's state (docs/PRD.md H7): the deleted entries from `GET /api/app/trash`, loaded
 * each time the page opens and each time the app comes back to it, and the restores and purges the
 * page sends at once. Nothing is queued, so offline nothing changes, as on the catalog pages.
 * After a restore, [refreshCache] reloads the offline cache, so the history shows the entry again.
 *
 * A server without the trash answers the list with a 404, or with something that is not a list.
 * Then [trashMissing], on [io], tells the capabilities, which hide the page, rather than the page
 * failing.
 *
 * Call every function on the main thread.
 *
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param settings the account settings, for the times, dates and durations the page shows.
 */
class TrashModel(
    private val client: () -> TogglApiClient?,
    private val refreshCache: () -> Unit,
    private val trashMissing: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    val settings: StateFlow<AccountSettings?> = MutableStateFlow(null),
) : CatalogPageModel {

    private val mutableState = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = mutableState.asStateFlow()

    private var opened = false
    private var loading: Job? = null

    /** The app went to the background with the page open, so the list may be old. */
    private var backgrounded = false

    /** Loads the trash. Does nothing while the page is open, so turning the phone sends no request. */
    override fun open() {
        if (opened) return
        opened = true
        load()
    }

    /** Loads the trash again, as after a failure. */
    fun retry() {
        if (opened) load()
    }

    /** The network came back. Loads again when the server could not be reached. */
    override fun reconnected() {
        if (opened && state.value.loadProblem != null) load()
    }

    override fun wentToBackground() {
        if (opened) backgrounded = true
    }

    /** After [wentToBackground], loads the trash again, which may have changed meanwhile. */
    override fun cameToForeground() {
        if (!backgrounded) return
        backgrounded = false
        load()
    }

    /** Closes the page: stops waiting and forgets what it loaded. A change already sent still lands. */
    override fun close() {
        loading?.cancel()
        loading = null
        opened = false
        backgrounded = false
        mutableState.value = TrashState()
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
    fun restore(id: Long) {
        send(
            request = { it.restore(id) },
            apply = { entries, _ -> entries.filterNot { it.id == id } },
            done = { TrashChange.Restored },
            restored = true,
        )
    }

    /** Deletes entry [id] for good, with no question, as the web app does. */
    fun purge(id: Long) {
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
            done = { TrashChange.Emptied(it.deleted) },
        )
    }

    /**
     * Sends [request], unless a change cannot go now. Once the server accepts it, [apply] changes
     * the list, the question closes and [done] says what happened. A refusal keeps the question
     * open with the reason, and a 404, an entry no longer in the trash, loads the list again. An
     * unreachable server turns every action off until a load gets through.
     */
    private fun <R> send(
        request: (TrashServer) -> ApiResult<R>,
        apply: (List<TrashEntry>, R) -> List<TrashEntry>,
        done: (R) -> TrashChange,
        restored: Boolean = false,
    ) {
        if (!opened || !state.value.canChange) return
        mutableState.update { it.copy(sending = true, changeProblem = null) }
        scope.launch {
            val answer = withContext(io) {
                val api = client() ?: return@withContext ApiResult.Failure(ApiError.AuthFailed)
                request(TrashServer(api))
            }
            if (answer is ApiResult.Success && restored) refreshCache()
            // The page closed while the request ran.
            if (!opened) return@launch
            when (answer) {
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
        loading = scope.launch {
            // A cancelled load drops its answer: withContext throws once it returns.
            val answer = withContext(io) {
                val api = client() ?: return@withContext ApiResult.Failure(ApiError.AuthFailed)
                TrashServer(api).list().also { if (it is ApiResult.Failure && it.error.meansNoTrash()) trashMissing() }
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
    (this is ApiError.UnexpectedStatus && status == 404) || this is ApiError.InvalidResponse
