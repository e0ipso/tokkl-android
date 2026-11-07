package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.noTimeDay
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Instant
import java.time.LocalDate

/**
 * A stopped entry as the history shows it.
 *
 * @property start the server's start for an entry the server has, or for one with a change it
 *   has not received, the start the server will store, snapped on the phone.
 * @property stop as [start].
 * @property unsent the queue holds a change to the entry that the server has not received.
 * @property cached the phone holds the entry in its two weeks, so the menu's actions can change
 *   it. The older days that the server sent, and the days the cache dropped since, are not on the
 *   phone, and an entry from before the two weeks that the cache holds only until its next refresh
 *   counts as theirs. The entry form edits both kinds.
 * @property projectId the project's id, which the grouping of similar entries compares.
 * @property taskId the task's id, as [projectId].
 * @property allDay the entry is a no-time entry: it has a duration and a day, and its [start] and
 *   [stop] only carry the duration, so the history shows no clock times for it.
 */
data class HistoryEntry(
    val id: Long,
    val description: String,
    val project: String?,
    val projectColor: String?,
    val task: String?,
    val tags: List<String>,
    val billable: Boolean,
    val start: Instant,
    val stop: Instant,
    val unsent: Boolean,
    val cached: Boolean = true,
    val projectId: Long? = null,
    val taskId: Long? = null,
    val allDay: Boolean = false,
) {
    /** Whole seconds from [start] to [stop], as entrySeconds in the web app's lib/settings.ts. */
    val seconds: Long get() = maxOf(0L, Math.floorDiv(stop.toEpochMilli() - start.toEpochMilli(), 1000L))
}

/**
 * One day of the history in the account's timezone, with its entries newest first, and the
 * [rows] that show them: each entry on its own, or similar ones grouped as [rowsForDay] groups
 * them.
 */
data class HistoryDay(
    val date: LocalDate,
    val entries: List<HistoryEntry>,
    val rows: List<HistoryRow> = rowsForDay(entries, collapse = false),
) {
    val totalSeconds: Long get() = entries.sumOf { it.seconds }
}

/**
 * The history's days, newest first, as EntryList.tsx in the web app groups them: each stopped
 * entry goes under the day of its start in the account's timezone, and the running entry stays
 * out, since the timer shows it. [cached] comes from the phone, with queued changes applied, and
 * [older] holds the days before the cache: those the cache has dropped since the older days
 * loaded, then the pages from the server, oldest page last. An entry in both shows as cached, and
 * one in two pages as the later page has it.
 *
 * Stopped entries show the times the server returned, snapped or not. An entry with a change the
 * server has not received shows [AccountTime.snapUnsent] of its times instead, the times the
 * server will store, so they do not change when it answers. That snapped start decides its day.
 *
 * A no-time entry goes under its stored day ([noTimeDay]), never the day of its start, which a
 * timezone change leaves stale. Unsent, it shows [AccountTime.noTimeSpan] of its duration, midnight
 * plus the snapped duration, and never [AccountTime.snapUnsent], which would snap its midnight
 * start to a grid point.
 *
 * With [collapse], the account's `collapseTimeEntries` preference, similar entries of a day share
 * a row, as the web app groups them.
 *
 * A cached entry that starts before [windowStart], the first day of the cache's two weeks, shows as
 * not cached: the cache holds it only until a refresh drops it, as after an edit of an older entry.
 */
fun historyDays(
    cached: List<EntryWithSendState>,
    older: List<CachedTimeEntry>,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    time: AccountTime,
    collapse: Boolean = true,
    windowStart: LocalDate? = null,
): List<HistoryDay> {
    val cachedIds = cached.mapTo(HashSet()) { it.entry.id }
    // An entry edited between two pages can come in both; the later page has its newer values.
    val olderOnly = older.associateBy { it.id }.values.filter { it.id !in cachedIds }
    val inWindow = { entry: CachedTimeEntry -> windowStart == null || entry.dayIn(time) >= windowStart }
    val rows = cached.map {
        Row(
            it.entry,
            it.unsent,
            cached = inWindow(it.entry),
        )
    } + olderOnly.map { Row(it, unsent = false, cached = false) }
    val projectsById = projects.associateBy { it.id }
    val tasksById = tasks.associateBy { it.id }
    return rows
        .mapNotNull { row ->
            row.entry.shown(
                row.unsent,
                row.cached,
                time,
                projectsById,
                tasksById,
            )?.let { shown ->
                (
                    if (row.entry.allDay) {
                        row.entry.dayIn(
                            time,
                        )
                    } else {
                        time.local(shown.start).toLocalDate()
                    }
                    ) to shown
            }
        }
        .groupBy({ it.first }, { it.second })
        .map { (date, entries) ->
            entries.sortedWith(
                NEWEST_FIRST,
            ).let { HistoryDay(date, it, rowsForDay(it, collapse)) }
        }
        .sortedByDescending { it.date }
}

/** An entry the history shows, from the phone's cache or from the older days. */
private class Row(val entry: CachedTimeEntry, val unsent: Boolean, val cached: Boolean)

/** The day of an entry as the cache holds it: a no-time entry's stored day, else the day of its start. */
private fun CachedTimeEntry.dayIn(time: AccountTime): LocalDate =
    if (allDay) noTimeDay(time.zone) else time.local(start).toLocalDate()

/** The entry as the history shows it, or null while it runs. */
private fun CachedTimeEntry.shown(
    unsent: Boolean,
    cached: Boolean,
    time: AccountTime,
    projects: Map<Long, CachedProject>,
    tasks: Map<Long, CachedTask>,
): HistoryEntry? {
    val stop = stop ?: return null
    // A no-time entry never goes through snapUnsent: its span is its day's midnight plus the snapped duration.
    val span = when {
        !unsent -> null

        allDay -> time.noTimeSpan(
            noTimeDay(time.zone),
            maxOf(0L, Math.floorDiv(stop.toEpochMilli() - start.toEpochMilli(), 1000L)),
        )

        // A rejected pending span keeps its actual times and unsent state for correction.
        else -> time.snapUnsent(start, stop)
    }
    val project = projectId?.let(projects::get)
    return HistoryEntry(
        id = id,
        description = description,
        project = project?.name,
        projectColor = project?.color,
        task = taskId?.let(tasks::get)?.name,
        tags = tags,
        billable = billable,
        start = span?.start ?: start,
        stop = span?.stop ?: stop,
        unsent = unsent,
        cached = cached,
        projectId = projectId,
        taskId = taskId,
        allDay = allDay,
    )
}

private val NEWEST_FIRST = compareByDescending<HistoryEntry> { it.start }.thenByDescending { it.id }
