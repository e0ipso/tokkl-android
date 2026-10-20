package com.github.e0ipso.tokkl.favorites

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.toServerProblem
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.entryLabels
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One favorite as the Timer page shows it: what a tap starts, and its project's and task's names. */
data class FavoriteItem(val fields: EntryFields, val labels: EntryLabels)

/** Why the last change to the favorites did not happen. */
sealed interface FavoritesProblem {

    /** The list already holds [MAX_FAVORITES]. */
    data object Full : FavoritesProblem

    /** The server did not store the change. */
    data class Server(val problem: ServerProblem) : FavoritesProblem
}

/**
 * The favorites on the Timer page.
 *
 * @property available the server keeps Tokkl's favorites. Without them, as on Toggl Track, the
 *   page shows no favorites control at all.
 * @property saving a change is on its way to the server, so no other change starts.
 * @property problem why the last change did not happen, until the next change.
 */
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
 * Tokkl's favorites (docs/PRD.md T5): up to [MAX_FAVORITES] entries the user starts in one tap,
 * the same ones the web app shows, from the cache, so they show and start offline. Adding,
 * removing and moving one needs the server: [save] stores the whole list there and then in the
 * cache, and until it answers, no other change starts. A change the server does not store
 * leaves the list as it was and says why.
 *
 * Starting a favorite is the timer's: TimerModel.startFrom.
 *
 * @param available whether the server keeps Tokkl's favorites, from the capabilities.
 * @param save posts the list and caches it, blocking, so it runs on [io].
 */
class FavoritesModel(
    favorites: Flow<List<CachedFavorite>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    available: Flow<Boolean>,
    private val save: (List<EntryFields>) -> ApiResult<Unit>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    private val local = MutableStateFlow(Local())

    val state: StateFlow<FavoritesState> = combine(available, favorites, projects, tasks, local) { shown, rows, projectRows, taskRows, edits ->
        FavoritesState(
            available = shown,
            favorites = rows.map { row -> row.fields().let { FavoriteItem(it, entryLabels(it, projectRows, taskRows)) } },
            saving = edits.saving,
            problem = edits.problem,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesState())

    /** Adds [fields] at the end of the list, as the web app's Pin as favorite does, unless it is full. */
    fun add(fields: EntryFields) = change { list -> if (list.size >= MAX_FAVORITES) null else list + fields }

    /** Removes the favorite at [index]. */
    fun remove(index: Int) = change { list -> list.filterIndexed { at, _ -> at != index } }

    /** Moves the favorite at [index] one place earlier, or with [later], one place later. */
    fun move(index: Int, later: Boolean) = change { list ->
        val other = if (later) index + 1 else index - 1
        if (index !in list.indices || other !in list.indices) {
            list
        } else {
            list.toMutableList().also { it[index] = list[other]; it[other] = list[index] }
        }
    }

    /** Forgets the last change's problem. */
    fun dismissProblem() = local.update { it.copy(problem = null) }

    /**
     * Saves what [edit] makes of the shown list, or says the list is full when it returns null.
     * Call it on the main thread.
     */
    private fun change(edit: (List<EntryFields>) -> List<EntryFields>?) {
        val shown = state.value
        if (!shown.available || local.value.saving) return
        val list = shown.favorites.map { it.fields }
        val next = edit(list)
        if (next == null) {
            local.update { it.copy(problem = FavoritesProblem.Full) }
            return
        }
        if (next == list) return
        local.value = Local(saving = true)
        scope.launch {
            val result = withContext(io) { save(next) }
            local.value = Local(problem = (result as? ApiResult.Failure)?.let { FavoritesProblem.Server(it.error.toServerProblem()) })
        }
    }

    private data class Local(val saving: Boolean = false, val problem: FavoritesProblem? = null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
