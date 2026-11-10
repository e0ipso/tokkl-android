package com.github.e0ipso.tokkl.catalog

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.flatMap
import com.github.e0ipso.tokkl.api.fold
import com.github.e0ipso.tokkl.api.getOrElse
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.PageLoads
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.signedIn
import com.github.e0ipso.tokkl.session.toServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The server behind a page that manages one kind of the account's items: which workspace the page
 * manages, and what it lists there. Each feature's repository adds the changes its page sends.
 */
interface CatalogRepository<D> {

    /** The workspace the page manages: [stored] from the account settings, or the server's default. */
    suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId>

    suspend fun load(workspace: WorkspaceId): ApiResult<D>
}

/**
 * A change the server accepted on a page of the account's items, for the message that confirms
 * it. [name] is the item's name after the change.
 */
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
 * @property loadProblem why [data] did not load, or why the server could not be reached for the
 *   last change. While it is set, nothing can be changed.
 * @property changeProblem why the server refused the last change, or null.
 * @property done the change the server last accepted, until the page has said so.
 */
@Immutable
data class CatalogState<D, O, C>(
    val data: D? = null,
    val loading: Boolean = false,
    val loadProblem: ServerProblem? = null,
    val overlay: O? = null,
    val sending: Boolean = false,
    val changeProblem: ServerProblem? = null,
    val done: C? = null,
) {
    /** Whether a change can go to the server now, as far as the page knows. Offline it cannot either. */
    val canChange: Boolean get() = data != null && loadProblem == null && !sending
}

/**
 * The state of a page that manages one kind of the account's items on the server: projects,
 * clients or tags (docs/PRD.md P2 to P4). It loads them from [repository] each time the page shows
 * and each time the app comes back to the foreground with the page on screen, as its view model's
 * stateInPage tells it through [PageLoads], and sends each change at once. A load keeps the list
 * and the form or question open over it until the answer comes. Nothing is queued: offline, or
 * with the server out of reach, nothing changes. Each change goes through [changes], which
 * refreshes the offline cache after the server accepts it, so the picker, the history and the
 * reports show it.
 *
 * Call every function on the main thread.
 *
 * @param restored the form or question a page kept in its saved state, open over the list from the
 *   start, as after a new process restored the page.
 * @param reloadWhenMissing whether a change the server refuses with a 404, as for an item no longer
 *   there, loads the list again.
 */
class CatalogModel<D, O, C>(
    private val repository: CatalogRepository<D>,
    private val settings: AccountSettingsSource,
    private val changes: ServerChanges,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    restored: O? = null,
    private val reloadWhenMissing: Boolean = false,
) : PageLoads {

    private val mutableState = MutableStateFlow(CatalogState<D, O, C>(overlay = restored))
    val state: StateFlow<CatalogState<D, O, C>> = mutableState.asStateFlow()

    /** Whether the list is open: from [shown] until [close], a configuration change included. */
    var opened: Boolean = false
        private set

    private var loading: Job? = null
    private var reconnects = 0

    /**
     * Counts the lists this model opened: [close] moves it on, so an answer to a change sent
     * before then, as for another project's tasks, finds a later list and changes nothing in it.
     */
    private var opening = 0

    /**
     * Counts the changes the server accepted while the list was open. A load that started before
     * one of them may have read the list from before it, so [load] drops such an answer and reads
     * the list again.
     */
    private var accepted = 0

    /**
     * Counts the loads that put a list on the page. A load that lands while a change is on its way
     * may have read the list with that change already in it, so [send] then reads the list again
     * instead of putting the answer into it.
     */
    private var landed = 0

    private var workspace: WorkspaceId? = null

    /** The workspace the page manages: the one the data came from, or else the stored one, or null. */
    fun workspace(): WorkspaceId? = workspace ?: settings.defaultWorkspaceId()

    /**
     * A page that shows again after a trip to the background keeps what it shows until the
     * answer comes, and an open form or question stays as it is.
     */
    override fun shown() {
        opened = true
        load()
    }

    fun retry() {
        if (opened) load()
    }

    /** The app came back to the foreground with the page on screen: the data may have changed on the server. */
    override fun returned() = retry()

    /** A network change also covers a request that has not reported its failure yet. */
    override fun reconnected() {
        reconnects++
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
        opening++
        opened = false
        workspace = null
        mutableState.value = CatalogState()
    }

    fun show(overlay: O) {
        if (opened && workspace != null && state.value.canChange) {
            mutableState.update { it.copy(overlay = overlay, changeProblem = null) }
        }
    }

    /**
     * Changes the form or question open over the list, such as what its fields hold, also while
     * no change can go to the server. Does nothing while none is open.
     */
    fun change(edit: (O) -> O) {
        mutableState.update { it.copy(overlay = it.overlay?.let(edit)) }
    }

    fun dismiss() {
        mutableState.update { it.copy(overlay = null, changeProblem = null) }
    }

    fun doneShown() {
        mutableState.update { it.copy(done = null) }
    }

    /**
     * Once the server accepts [request], the cache refreshes, unless [refresh] is false, and while
     * the list is open [apply] puts the answer into the data, the overlay closes and [done] says
     * what happened. When a load put a list on the page while the request ran, that list may
     * already hold the change, so the page reads the list again instead of applying the answer to
     * it. A refusal keeps the overlay open with the reason. An unreachable server turns every
     * action off until a load gets through.
     * Once sent, the change and the refresh after it outlive the page's scope. An answer that comes
     * after the list closed, also when it opened again since, as for another project, changes
     * nothing on the page.
     */
    fun <R> send(
        request: suspend (workspace: WorkspaceId) -> ApiResult<R>,
        apply: (D, R) -> D,
        done: (R) -> C,
        refresh: Boolean = true,
    ) {
        val workspace = workspace ?: return
        if (!opened || !state.value.canChange) return
        mutableState.update { it.copy(sending = true, changeProblem = null) }
        val sentFrom = opening
        val landedBefore = landed
        val sent = changes.send(refresh) { request(workspace) }
        scope.launch {
            val answer = sent.await()
            // The list closed while the request ran. What the server accepted still reaches the cache.
            if (!opened || opening != sentFrom) return@launch
            when (answer) {
                is ApiResult.Success -> took(answer.value, fresh = landed == landedBefore, apply, done)
                is ApiResult.Failure -> refused(answer.error)
            }
        }
    }

    /**
     * The server accepted a change. A list that landed while it ran may already hold it, so the
     * page reads the list again instead of applying the answer to a list that is not [fresh].
     */
    private fun <R> took(answer: R, fresh: Boolean, apply: (D, R) -> D, done: (R) -> C) {
        accepted++
        mutableState.update { current ->
            current.copy(
                data = if (fresh) current.data?.let { apply(it, answer) } else current.data,
                sending = false,
                overlay = null,
                done = done(answer),
            )
        }
        if (!fresh && loading?.isActive != true) load()
    }

    private fun refused(error: ApiError) {
        val problem = error.toServerProblem()
        mutableState.update { current ->
            if (problem == ServerProblem.Unreachable) {
                current.copy(sending = false, loadProblem = problem)
            } else {
                current.copy(sending = false, changeProblem = problem)
            }
        }
        if (reloadWhenMissing && (error as? ApiError.UnexpectedStatus)?.status == HttpStatus.NOT_FOUND) load()
    }

    private fun load() {
        loading?.cancel()
        mutableState.update { it.copy(loading = true) }
        val stored = settings.defaultWorkspaceId()
        val acceptedBefore = accepted
        val reconnectsBefore = reconnects
        loading = scope.launch {
            // A cancelled load cancels its requests and drops its answer.
            val answer = withContext(io) { read(stored) }
            if (accepted != acceptedBefore) {
                // The server accepted a change while this load ran: its answer may predate it.
                loading = null
                load()
                return@launch
            }
            when (answer) {
                is Loaded -> {
                    landed++
                    workspace = answer.workspace
                    mutableState.update { it.copy(data = answer.data, loading = false, loadProblem = null) }
                }

                is NotLoaded -> {
                    mutableState.update { it.copy(loading = false, loadProblem = answer.problem) }
                    if (opened && answer.problem == ServerProblem.Unreachable && reconnects != reconnectsBefore) load()
                }
            }
        }
    }

    private sealed interface Answer<out D>

    private class Loaded<D>(val workspace: WorkspaceId, val data: D) : Answer<D>

    private class NotLoaded(val problem: ServerProblem) : Answer<Nothing>

    private suspend fun read(stored: WorkspaceId?): Answer<D> {
        val workspace = repository.workspace(stored).getOrElse { return NotLoaded(it.error.toServerProblem()) }
        return repository.load(workspace).fold({ Loaded(workspace, it) }, { NotLoaded(it.toServerProblem()) })
    }
}

/**
 * The workspace whose projects, clients and tags the pages manage: the account's default
 * workspace, [stored] with the account settings, or from `GET /api/v9/me` when the stored
 * settings predate it.
 */
suspend fun managedWorkspace(api: TokklApi, stored: WorkspaceId?): ApiResult<WorkspaceId> {
    if (stored != null) return ApiResult.Success(stored)
    return api.account.defaultWorkspace().flatMap { me ->
        me.defaultWorkspaceId?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("/me has no default_workspace_id")))
    }
}

suspend fun ApiClientProvider.managedWorkspace(stored: WorkspaceId?): ApiResult<WorkspaceId> =
    signedIn { managedWorkspace(it, stored) }
