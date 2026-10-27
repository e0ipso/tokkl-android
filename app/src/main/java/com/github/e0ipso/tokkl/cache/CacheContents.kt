package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Instant

/** Every cached row. */
data class CacheContents(
    val timeEntries: List<CachedTimeEntry> = emptyList(),
    val projects: List<CachedProject> = emptyList(),
    val clients: List<CachedClient> = emptyList(),
    val tags: List<CachedTag> = emptyList(),
    val tasks: List<CachedTask> = emptyList(),
    val favorites: List<CachedFavorite> = emptyList(),
)

/** Entry rows and queued entry IDs captured atomically before a refresh reads the server. */
data class EntryRefreshStart(val entries: List<CachedTimeEntry>, val pendingIds: Set<Long>)

/**
 * What one refresh read from the server. A null list was not read, because its request failed or
 * the server does not offer it, and the cache keeps those rows as they are.
 *
 * @property history the days of history the refresh covers, or null when the phone does not know
 *   the account's timezone. Then the refresh reads no entries and prunes none.
 */
class ServerSnapshot(
    val history: HistoryWindow? = null,
    val timeEntries: List<CachedTimeEntry>? = null,
    val projects: List<CachedProject>? = null,
    val clients: List<CachedClient>? = null,
    val tags: List<CachedTag>? = null,
    val tasks: List<CachedTask>? = null,
    val favorites: List<CachedFavorite>? = null,
)

/**
 * The cached history: entries that start from [start] up to [end], which is the end of today.
 * [start] is the start of the day [DAYS] - 1 days before today, so the cache holds today and the
 * 13 days before it, counted in the account's timezone.
 */
data class HistoryWindow(val start: Instant, val end: Instant) {

    companion object {
        const val DAYS = 14L

        /** The window that ends with today in [time]'s zone. */
        fun endingToday(time: AccountTime): HistoryWindow {
            val today = time.today()
            return HistoryWindow(
                start = today.minusDays(DAYS - 1).atStartOfDay(time.zone).toInstant(),
                end = today.plusDays(1).atStartOfDay(time.zone).toInstant(),
            )
        }
    }
}

/**
 * The cache after [server]'s refresh. The server's rows replace the cached ones, and a cached row
 * the server no longer sends goes, with these exceptions:
 *
 * - An entry that starts before the history window is pruned, also when the entries were not read.
 * - An archived project or a done task stays while a cached entry or favorite uses it, so the
 *   entry still shows it. The server's values replace it as for any other row.
 * - A project, task or tag the server no longer sends stays, with the values it had, while a
 *   cached entry or favorite uses it.
 * - A list the refresh did not read stays as it was.
 * - An entry changed since [before], including additions, removals and ID remaps, keeps its
 *   current cached state. IDs pending at [before] stay protected even when sending left their
 *   already-overlaid row unchanged or absent. A later uncontended refresh takes server state.
 * - Each of the [pending] changes the server has not received applies on top, in order, also to
 *   an entry from before the history window, so the user keeps seeing it until it is sent. The
 *   projects, tasks and tags it uses stay as for any cached entry.
 */
fun CacheContents.refreshedWith(
    server: ServerSnapshot,
    pending: List<PendingChange> = emptyList(),
    before: EntryRefreshStart? = null,
): CacheContents {
    val historyStart = server.history?.start
    val protectedIds = if (before == null) emptySet() else {
        val previous = before.entries.associateBy { it.id }
        val current = timeEntries.associateBy { it.id }
        before.pendingIds + (previous.keys + current.keys).filter { previous[it] != current[it] }
    }
    val refreshedEntries = (server.timeEntries ?: timeEntries)
        .filter { historyStart == null || it.start >= historyStart }
        .filterNot { it.id in protectedIds }
    val entries = (refreshedEntries + timeEntries.filter { it.id in protectedIds })
        .distinctBy { it.id }
        .withPending(pending)
    val favorites = (server.favorites ?: favorites).distinctBy { it.position }
    val usedProjects = (entries.mapNotNull { it.projectId } + favorites.mapNotNull { it.projectId }).toSet()
    val usedTasks = (entries.mapNotNull { it.taskId } + favorites.mapNotNull { it.taskId }).toSet()
    val usedTags = entries.flatMap { it.tagIds }.toSet()
    return CacheContents(
        timeEntries = entries,
        projects = replaced(projects, server.projects, CachedProject::id, { it.id in usedProjects }) { it.active },
        clients = server.clients?.distinctBy { it.id } ?: clients,
        tags = replaced(tags, server.tags, CachedTag::id, { it.id in usedTags }) { true },
        tasks = replaced(tasks, server.tasks, CachedTask::id, { it.id in usedTasks }) { it.active },
        favorites = favorites,
    )
}

/**
 * The [server] rows that [keep] accepts or that are [used], and the [cached] rows the server no
 * longer sends that are [used]. [cached] as it is when the server's list was not read.
 */
private fun <T> replaced(
    cached: List<T>,
    server: List<T>?,
    id: (T) -> Long,
    used: (T) -> Boolean,
    keep: (T) -> Boolean,
): List<T> {
    if (server == null) return cached
    val fresh = server.distinctBy(id)
    val sent = fresh.mapTo(HashSet(), id)
    return fresh.filter { keep(it) || used(it) } + cached.filter { id(it) !in sent && used(it) }
}
