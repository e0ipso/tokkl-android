package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** What a tap on a favorite shortcut did. */
sealed interface ShortcutStart {

    /** A timer runs with the favorite's fields, on the phone, and the queue holds the start. */
    data class Started(val name: String) : ShortcutStart

    /**
     * Nothing started: the favorites no longer hold the shortcut's, the server keeps no
     * favorites, or no account is signed in. A launcher keeps a shortcut the user pinned to the
     * home screen after the app removed it.
     */
    data object Gone : ShortcutStart
}

/**
 * Starts the favorite that the shortcut [id] names, as a tap on it on the Timer page does
 * (TimerViewModel.startFrom): in the account's default workspace [workspaceId], from the cache, so it
 * works offline, and a running entry stops at the new one's start. [start] is
 * `ChangeQueue.start`, which reads the tap time first.
 *
 * @param available whether the server keeps favorites, from the capabilities.
 */
suspend fun startFavoriteShortcut(
    id: String,
    available: Boolean,
    favorites: List<Favorite>,
    projects: List<Project>,
    workspaceId: WorkspaceId?,
    names: FavoriteNames,
    start: suspend (workspaceId: WorkspaceId, fields: EntryFields) -> EntryId,
): ShortcutStart {
    val favorite = favoriteForShortcut(id, favorites)?.takeIf { available } ?: return ShortcutStart.Gone
    if (workspaceId == null) return ShortcutStart.Gone
    val fields = favorite.fields()
    start(workspaceId, fields)
    return ShortcutStart.Started(favoriteName(fields, projects, names))
}

/**
 * What a tap on a favorite's shortcut starts, read when the tap comes, as [startFavoriteShortcut]
 * takes it: whether the server keeps favorites, the cached favorites and projects, the account's
 * default workspace, and the queue's Start.
 */
class FavoriteShortcutStarts(
    private val available: suspend () -> Boolean,
    private val favorites: Flow<List<Favorite>>,
    private val projects: Flow<List<Project>>,
    private val workspaceId: suspend () -> WorkspaceId?,
    private val changes: RunningEntryChanges,
) {

    /** Starts the favorite the shortcut [id] names, with names in [names]. Blocks on disk I/O. */
    suspend fun start(id: String, names: FavoriteNames): ShortcutStart = startFavoriteShortcut(
        id = id,
        available = available(),
        favorites = favorites.first(),
        projects = projects.first(),
        workspaceId = workspaceId(),
        names = names,
        start = changes::start,
    )
}
