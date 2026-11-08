package com.github.e0ipso.tokkl.catalog

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.PageLoads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** A change the server accepted, for the message that confirms it. [name] is the item's name after the change. */
sealed interface CatalogChange {
    val name: String

    data class Created(override val name: String) : CatalogChange

    data class Saved(override val name: String) : CatalogChange

    data class Archived(override val name: String) : CatalogChange

    data class Restored(override val name: String) : CatalogChange

    data class Renamed(override val name: String) : CatalogChange

    data class Deleted(override val name: String) : CatalogChange
}

/**
 * What a page that manages projects, clients or tags shows.
 *
 * @property data what the page loaded from the server, or null until it loads.
 * @property loadProblem why [data] did not load, or why the server could not be reached for the
 *   last change. While it is set, nothing can be changed.
 * @property overlay the form or question open over the list, or null.
 * @property sending a change is on its way to the server.
 * @property changeProblem why the server refused the last change, or null.
 * @property done the change the server last accepted, until the page has said so.
 */
@Immutable
data class CatalogState<D, O>(
    val data: D? = null,
    val loading: Boolean = false,
    val loadProblem: ServerProblem? = null,
    val overlay: O? = null,
    val sending: Boolean = false,
    val changeProblem: ServerProblem? = null,
    val done: CatalogChange? = null,
) {
    /** Whether a change can go to the server now, as far as the page knows. Offline it cannot either. */
    val canChange: Boolean get() = data != null && loadProblem == null && !sending
}

/**
 * The state of a page that manages one kind of the account's items on the server: projects,
 * clients or tags (docs/PRD.md P2 to P4). It loads them from [repository] each time the page shows
 * and each time the app comes back to the foreground with the page on screen, as its view model's
 * stateInPage tells it through [PageLoads], and sends each change at once. A load keeps the list
 * and the form or question open over it until the answer comes. They go with the view model, when
 * the page's route leaves the back stack. Nothing is queued: offline, or with the server out of
 * reach, nothing changes. After each change the server accepts, [refreshCache] reloads the offline
 * cache, so the picker, the history and the reports show it.
 *
 * Call every function on the main thread.
 *
 * @param repository finds the workspace and reads the page's data from it. Runs on [io].
 * @param workspaceId the account's default workspace from the stored settings, or null when they
 *   do not have it.
 * @param refreshCache starts a refresh of the cache that reads the server after the change.
 */
class CatalogModel<D, O>(
    private val repository: CatalogRepository<D>,
    private val workspaceId: () -> WorkspaceId?,
    private val refreshCache: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : PageLoads {

    private val mutableState = MutableStateFlow(CatalogState<D, O>())
    val state: StateFlow<CatalogState<D, O>> = mutableState.asStateFlow()

    /** Whether the list is open: from [shown] until [close], a configuration change included. */
    var opened: Boolean = false
        private set

    private var loading: Job? = null

    /** The workspace the data came from, once a load found it. */
    private var workspace: WorkspaceId? = null

    /** The workspace the page manages: the one the data came from, or else the stored one, or null. */
    fun workspace(): WorkspaceId? = workspace ?: workspaceId()

    /**
     * The page shows: loads the data. A page that shows again after a trip to the background keeps
     * what it shows until the answer comes, and an open form or question stays as it is.
     */
    override fun shown() {
        opened = true
        load()
    }

    /** Loads the data again, as after a failure. */
    fun retry() {
        if (opened) load()
    }

    /** The app came back to the foreground with the page on screen: the data may have changed on the server. */
    override fun returned() = retry()

    /** The network came back. Loads again when the server could not be reached. */
    override fun reconnected() {
        if (opened && state.value.loadProblem != null) load()
    }

    /**
     * The list closed, as the task list does when its project's form closes: stops waiting and
     * forgets what it loaded. A change already sent still lands. A page's list never closes: it
     * goes with its view model.
     */
    fun close() {
        loading?.cancel()
        loading = null
        opened = false
        workspace = null
        mutableState.value = CatalogState()
    }

    /** Opens [overlay] over the list, while a change can go to the server. */
    fun show(overlay: O) {
        if (opened && workspace != null && state.value.canChange) {
            mutableState.update { it.copy(overlay = overlay, changeProblem = null) }
        }
    }

    /** Closes the form or question over the list. */
    fun dismiss() {
        mutableState.update { it.copy(overlay = null, changeProblem = null) }
    }

    /** The page has said that [CatalogState.done] happened. */
    fun doneShown() {
        mutableState.update { it.copy(done = null) }
    }

    /**
     * Sends [request] to the workspace, unless a change cannot go now. It runs on [io]. Once the server accepts it,
     * [apply] puts the answer into the data, the overlay closes and [done] says what happened, and
     * the cache refreshes. A refusal keeps the overlay open with the reason. An unreachable server
     * turns every action off until a load gets through. Once sent, the change reaches the server
     * also when the page's scope is cancelled meanwhile.
     */
    fun <R> send(
        request: suspend (workspace: WorkspaceId) -> ApiResult<R>,
        apply: (D, R) -> D,
        done: (R) -> CatalogChange,
    ) {
        val workspace = workspace ?: return
        if (!opened || !state.value.canChange) return
        mutableState.update { it.copy(sending = true, changeProblem = null) }
        scope.launch {
            val answer = withContext(io + NonCancellable) { request(workspace) }
            // The list closed while the request ran. What the server accepted still reaches the cache.
            if (!opened) {
                if (answer is ApiResult.Success) refreshCache()
                return@launch
            }
            when (answer) {
                is ApiResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            data = current.data?.let { apply(it, answer.value) },
                            sending = false,
                            overlay = null,
                            done = done(answer.value),
                        )
                    }
                    refreshCache()
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
                }
            }
        }
    }

    private fun load() {
        loading?.cancel()
        mutableState.update { it.copy(loading = true) }
        val stored = workspaceId()
        loading = scope.launch {
            // A cancelled load cancels its requests and drops its answer.
            val answer = withContext(io) { read(stored) }
            when (answer) {
                is Loaded -> {
                    workspace = answer.workspace
                    mutableState.update { it.copy(data = answer.data, loading = false, loadProblem = null) }
                }

                is NotLoaded -> mutableState.update { it.copy(loading = false, loadProblem = answer.problem) }
            }
        }
    }

    private sealed interface Answer<out D>

    private class Loaded<D>(val workspace: WorkspaceId, val data: D) : Answer<D>

    private class NotLoaded(val problem: ServerProblem) : Answer<Nothing>

    private suspend fun read(stored: WorkspaceId?): Answer<D> {
        val workspace = when (val answer = repository.workspace(stored)) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return NotLoaded(answer.error.toServerProblem())
        }
        return when (val answer = repository.load(workspace)) {
            is ApiResult.Success -> Loaded(workspace, answer.value)
            is ApiResult.Failure -> NotLoaded(answer.error.toServerProblem())
        }
    }
}
