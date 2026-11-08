package com.github.e0ipso.tokkl.favorites

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.toServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** One favorite as the Timer page shows it: what a tap starts, and its project's and task's names. */
@Immutable
data class FavoriteItem(val fields: EntryFields, val labels: EntryLabels)

/**
 * The timer templates page: the templates in [favorites], what the form's picker offers in
 * [picker], whether the phone is [offline], and the account's default workspace, null until the
 * settings have it.
 */
@Immutable
data class TemplatesPageState(
    val favorites: FavoritesState = FavoritesState(),
    val picker: PickerSource = PickerSource(),
    val offline: Boolean = false,
    val workspaceId: WorkspaceId? = null,
)

/** Why the last change to the favorites did not happen. */
sealed interface FavoritesProblem {

    /** The list already holds [MAX_FAVORITES]. */
    data object Full : FavoritesProblem

    /** The server did not store the change. */
    data class Server(val problem: ServerProblem) : FavoritesProblem
}

/**
 * Timer templates as the app shows them.
 *
 * @property available the server keeps Tokkl's favorites key. Without it, as on Toggl Track, the
 *   timer shows no template control and More leaves out the management page.
 * @property saving a change is on its way to the server, so no other change starts.
 * @property problem why the last change did not happen, until the next change.
 */
@Immutable
data class FavoritesState(
    val available: Boolean = false,
    val favorites: List<FavoriteItem> = emptyList(),
    val saving: Boolean = false,
    val problem: FavoritesProblem? = null,
) {
    /** No favorite can be added. */
    val full: Boolean get() = favorites.size >= MAX_FAVORITES
}

/**
 * Tokkl's timer templates (docs/PRD.md T5): up to [MAX_FAVORITES] saved entries the user starts
 * in one tap, the same favorites the web app stores, from the cache, so they show and start
 * offline. Adding, editing, removing and moving one needs the server: [save] stores the whole
 * list there and then in the cache, and until it answers, no other change starts. A change the
 * server does not store leaves the list as it was and says why.
 *
 * Starting a template is the timer's: TimerViewModel.startFrom.
 *
 * @param available whether the server keeps Tokkl's favorites, from the capabilities.
 * @param store posts the list and caches it. It runs on [io], and the save reaches the server and
 *   the cache also when the view model is cleared meanwhile.
 * @param picker the cached projects, clients, tasks and tags, which the templates page's picker offers.
 * @param offline whether the phone is offline, when no change can reach the server.
 * @param workspaceId the account's default workspace, whose projects the templates page offers.
 */
class FavoritesViewModel(
    favorites: Flow<List<Favorite>>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    available: Flow<Boolean>,
    private val store: suspend (List<EntryFields>) -> ApiResult<Unit>,
    scope: CoroutineScope,
    private val io: CoroutineContext,
    picker: Flow<PickerSource>,
    offline: StateFlow<Boolean>,
    workspaceId: Flow<WorkspaceId?>,
) : ViewModel(scope) {

    private val local = MutableStateFlow(Local())

    val state: StateFlow<FavoritesState> = combine(
        available,
        favorites,
        projects,
        tasks,
        local,
    ) { shown, rows, projectRows, taskRows, edits ->
        FavoritesState(
            available = shown,
            favorites = rows.map { row ->
                row.fields().let {
                    FavoriteItem(
                        it,
                        entryLabels(it, projectRows, taskRows),
                    )
                }
            },
            saving = edits.saving,
            problem = edits.problem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesState())

    /** What the timer templates page shows: [state], the picker's rows, the network and the workspace. */
    val page: StateFlow<TemplatesPageState> = combine(state, picker, offline, workspaceId, ::TemplatesPageState)
        .stateIn(viewModelScope, whileShown, TemplatesPageState(offline = offline.value))

    /**
     * Adds [fields] at the end of the list, as the web app's Pin as favorite does, unless it is
     * full. [onDone] gets null once the server has stored the list, or why nothing was pinned.
     * Without the capability, or while another change is saving, nothing happens and [onDone]
     * is not called.
     */
    fun add(fields: EntryFields, onDone: (FavoritesProblem?) -> Unit = {}) =
        change(onDone) { list -> if (list.size >= MAX_FAVORITES) null else list + fields }

    /** Replaces the template at [index] with [fields]. [onDone] hears how the save went. */
    fun replace(index: Int, fields: EntryFields, onDone: (FavoritesProblem?) -> Unit = {}) = change(onDone) { list ->
        if (index !in list.indices) list else list.toMutableList().also { it[index] = fields }
    }

    /** Removes the template at [index]. */
    fun remove(index: Int) = change { list -> list.filterIndexed { at, _ -> at != index } }

    /** Moves the favorite at [index] one place earlier, or with [later], one place later. */
    fun move(index: Int, later: Boolean) = change { list ->
        val other = if (later) index + 1 else index - 1
        if (index !in list.indices || other !in list.indices) {
            list
        } else {
            list.toMutableList().also {
                it[index] = list[other]
                it[other] = list[index]
            }
        }
    }

    /** Forgets the last change's problem. */
    fun dismissProblem() = local.update { it.copy(problem = null) }

    /**
     * Saves what [edit] makes of the shown list, or says the list is full when it returns null,
     * and tells [onDone] how it went. Call it on the main thread.
     */
    private fun change(onDone: (FavoritesProblem?) -> Unit = {}, edit: (List<EntryFields>) -> List<EntryFields>?) {
        val shown = state.value
        if (!shown.available || local.value.saving) return
        val list = shown.favorites.map { it.fields }
        val next = edit(list)
        if (next == null) {
            local.update { it.copy(problem = FavoritesProblem.Full) }
            onDone(FavoritesProblem.Full)
            return
        }
        if (next == list) {
            onDone(null)
            return
        }
        local.value = Local(saving = true)
        viewModelScope.launch {
            val result = withContext(io + NonCancellable) { store(next) }
            val problem = (result as? ApiResult.Failure)?.let { FavoritesProblem.Server(it.error.toServerProblem()) }
            local.value = Local(problem = problem)
            onDone(problem)
        }
    }

    private data class Local(val saving: Boolean = false, val problem: FavoritesProblem? = null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
