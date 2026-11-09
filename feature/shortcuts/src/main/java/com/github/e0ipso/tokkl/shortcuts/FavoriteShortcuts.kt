package com.github.e0ipso.tokkl.shortcuts

import android.content.Context
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.fields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.security.MessageDigest

/**
 * One favorite as a launcher shortcut (docs/PRD.md N6).
 *
 * @param id names the favorite by its fields, not its place in the list, so a launcher that
 *   ranks or pins the shortcut keeps the same favorite when the list is reordered.
 * @param shortLabel the favorite's name, as the Timer page shows it.
 * @param longLabel the name and the project, as TalkBack reads the Timer page's favorite.
 */
data class FavoriteShortcut(val id: String, val shortLabel: String, val longLabel: String)

/** The fixed words in a favorite's label, from the string resources. */
class FavoriteNames(val untitled: String, val withProject: (name: String, project: String) -> String)

/**
 * The [favorites] as launcher shortcuts, in their order, at most [limit] of them, or none when
 * the server keeps no favorites. Favorites with the same fields give one shortcut, since they
 * start the same entry.
 */
fun favoriteShortcuts(
    available: Boolean,
    favorites: List<Favorite>,
    projects: List<Project>,
    limit: Int,
    names: FavoriteNames,
): List<FavoriteShortcut> {
    if (!available) return emptyList()
    return favorites.map { it.fields() }
        .distinctBy(::favoriteShortcutId)
        .take(limit)
        .map { fields -> fields.shortcut(projects, names) }
}

/** The favorite among [favorites] that the shortcut [id] starts, or null when the list has none. */
fun favoriteForShortcut(id: String, favorites: List<Favorite>): Favorite? =
    favorites.firstOrNull { favoriteShortcutId(it.fields()) == id }

/**
 * The name of the favorite with [fields]: the description, else the project, else Untitled, as TimerPage.tsx names it.
 */
fun favoriteName(fields: EntryFields, projects: List<Project>, names: FavoriteNames): String =
    fields.description.ifEmpty { projectName(fields, projects) ?: names.untitled }

/** A SHA-256 hash of [fields], so the id the launcher keeps holds no description or name. */
fun favoriteShortcutId(fields: EntryFields): String {
    val text = listOf(
        fields.description,
        fields.projectId?.toString().orEmpty(),
        fields.taskId?.toString().orEmpty(),
        fields.tags.joinToString("") { field(it) },
        fields.billable.toString(),
    ).joinToString("") { field(it) }
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return ID_PREFIX + digest.joinToString("") { "%02x".format(it) }
}

private fun EntryFields.shortcut(projects: List<Project>, names: FavoriteNames): FavoriteShortcut {
    val name = favoriteName(this, projects, names)
    val project = projectName(this, projects)?.takeIf { description.isNotEmpty() }
    return FavoriteShortcut(
        id = favoriteShortcutId(this),
        shortLabel = name,
        longLabel = project?.let { names.withProject(name, it) } ?: name,
    )
}

private fun projectName(fields: EntryFields, projects: List<Project>): String? =
    fields.projectId?.let { id -> projects.firstOrNull { it.id == id }?.name }

/** Each value with its length first, so no two lists of values give the same text. */
private fun field(value: String): String = "${value.length}:$value"

private const val ID_PREFIX = "favorite-"

/**
 * The launcher shortcuts of the cached favorites as they, the projects and the capabilities change.
 * The lists start once the capabilities have loaded.
 */
internal fun favoriteShortcutLists(
    context: Context,
    launcher: LauncherShortcuts,
    capabilities: CapabilitiesSource,
    favorites: Flow<List<Favorite>>,
    projects: Flow<List<Project>>,
): Flow<List<FavoriteShortcut>> =
    combine(capabilities.loaded, favorites, projects) { flags, cachedFavorites, cachedProjects ->
        favoriteShortcuts(flags.favorites, cachedFavorites, cachedProjects, launcher.limit, favoriteNames(context))
    }
