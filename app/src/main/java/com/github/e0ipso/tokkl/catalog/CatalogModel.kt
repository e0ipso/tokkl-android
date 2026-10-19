package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** What a catalog page's screen tells its model about the page's life, through [CatalogPageEffects]. */
interface CatalogPageModel {
    /** The page opened. */
    fun open()

    /** The page went, not for a configuration change. */
    fun close()

    /** The network came back. */
    fun reconnected()

    /** The app went to the background with the page open, not for a configuration change. */
    fun wentToBackground()

    /** The app came back to the foreground with the page open. */
    fun cameToForeground()
}

/**
 * The state of a page that manages one kind of the account's items on the server: projects,
 * clients or tags (docs/PRD.md P2 to P4). It loads them with [fetch] each time the page opens and
 * each time the app comes back to the foreground with the page open, and sends each change at
 * once. Nothing is queued: offline, or with the server out of reach, nothing changes. After each
 * change the server accepts, [refreshCache] reloads the offline cache, so the picker, the history
 * and the reports show it.
 *
 * Call every function on the main thread.
 *
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param workspaceId the account's default workspace from the stored settings, or null when they
 *   do not have it.
 * @param refreshCache starts a refresh of the cache that reads the server after the change.
 * @param fetch reads the page's data from the workspace. Runs on [io].
 */
class CatalogModel<D, O>(
    private val client: () -> TogglApiClient?,
    private val workspaceId: () -> Long?,
    private val refreshCache: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val fetch: (TogglApiClient, Long) -> ApiResult<D>,
) : CatalogPageModel {

    private val mutableState = MutableStateFlow(CatalogState<D, O>())
    val state: StateFlow<CatalogState<D, O>> = mutableState.asStateFlow()

    private var opened = false
    private var loading: Job? = null

    /** The app went to the background with the page open, so what it lists may be old. */
    private var backgrounded = false

    /** The workspace the data came from, once a load found it. */
    private var workspace: Long? = null

    /** The workspace the page manages: the one the data came from, or else the stored one, or null. */
    fun workspace(): Long? = workspace ?: workspaceId()

    /** Loads the data. Does nothing while the page is open, so turning the phone sends no request. */
    override fun open() {
        if (opened) return
        opened = true
        load()
    }

    /** Loads the data again, as after a failure. */
    fun retry() {
        if (opened) load()
    }

    /** The network came back. Loads again when the server could not be reached. */
    override fun reconnected() {
        if (opened && state.value.loadProblem != null) load()
    }

    /**
     * The app went to the background with the page open. Call it when the activity stops, but not
     * for a configuration change, which is no trip to the background.
     */
    override fun wentToBackground() {
        if (opened) backgrounded = true
    }

    /**
     * The app came back to the foreground. After [wentToBackground], loads the data again, since it
     * may have changed on the server meanwhile. The list keeps what it shows until the answer
     * comes, and an open form or question stays as it is.
     */
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
     * Sends [request] to the workspace, unless a change cannot go now. Once the server accepts it,
     * [apply] puts the answer into the data, the overlay closes and [done] says what happened, and
     * the cache refreshes. A refusal keeps the overlay open with the reason. An unreachable server
     * turns every action off until a load gets through.
     */
    fun <R> send(request: (TogglApiClient, Long) -> ApiResult<R>, apply: (D, R) -> D, done: (R) -> CatalogChange) {
        val workspace = workspace ?: return
        if (!opened || !state.value.canChange) return
        mutableState.update { it.copy(sending = true, changeProblem = null) }
        scope.launch {
            val answer = withContext(io) {
                val api = client() ?: return@withContext ApiResult.Failure(ApiError.AuthFailed)
                request(api, workspace)
            }
            // The page closed while the request ran. What the server accepted still reaches the cache.
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
            // A cancelled load drops its answer: withContext throws once it returns.
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

    private class Loaded<D>(val workspace: Long, val data: D) : Answer<D>

    private class NotLoaded(val problem: ServerProblem) : Answer<Nothing>

    private fun read(stored: Long?): Answer<D> {
        val api = client() ?: return NotLoaded(ServerProblem.AuthFailed)
        val workspace = when (val answer = managedWorkspace(api, stored)) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> return NotLoaded(answer.error.toServerProblem())
        }
        return when (val answer = fetch(api, workspace)) {
            is ApiResult.Success -> Loaded(workspace, answer.value)
            is ApiResult.Failure -> NotLoaded(answer.error.toServerProblem())
        }
    }
}
