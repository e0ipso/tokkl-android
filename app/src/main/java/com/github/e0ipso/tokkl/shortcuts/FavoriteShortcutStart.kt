package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.favorites.fields

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
 * (TimerModel.startFrom): in the account's default workspace [workspaceId], from the cache, so it
 * works offline, and a running entry stops at the new one's start. [start] is
 * `ChangeQueue.start`, which reads the tap time first.
 *
 * @param available whether the server keeps favorites, from the capabilities.
 */
suspend fun startFavoriteShortcut(
    id: String,
    available: Boolean,
    favorites: List<CachedFavorite>,
    projects: List<CachedProject>,
    workspaceId: Long?,
    names: FavoriteNames,
    start: suspend (workspaceId: Long, fields: EntryFields) -> Long,
): ShortcutStart {
    val favorite = favoriteForShortcut(id, favorites)?.takeIf { available } ?: return ShortcutStart.Gone
    if (workspaceId == null) return ShortcutStart.Gone
    val fields = favorite.fields()
    start(workspaceId, fields)
    return ShortcutStart.Started(favoriteName(fields, projects, names))
}
