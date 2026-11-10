package com.github.e0ipso.tokkl.history

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.entryfields.noTimeEntry
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Instant
import java.time.LocalDate

/**
 * A stopped entry as the history shows it.
 *
 * @property timing the server's times for an entry the server has, or for one with a change it
 *   has not received, the times the server will store, snapped on the phone. A no-time entry's
 *   [EntryTiming.AllDay] carries only its duration, so the history shows no clock times for it.
 * @property cached the phone holds the entry in its two weeks, so the menu's actions can change
 *   it. The older days that the server sent, and the days the cache dropped since, are not on the
 *   phone, and an entry from before the two weeks that the cache holds only until its next refresh
 *   counts as theirs. The entry form edits both kinds.
 * @property projectId the project's id, which the grouping of similar entries compares.
 * @property taskId the task's id, as [projectId].
 */
@Immutable
data class HistoryEntry(
    val id: EntryId,
    val description: String,
    val project: String?,
    val projectColor: String?,
    val task: String?,
    val tags: List<String>,
    val billable: Boolean,
    val timing: EntryTiming.Ended,
    val unsent: Boolean,
    val cached: Boolean = true,
    val projectId: ProjectId? = null,
    val taskId: TaskId? = null,
) {
    val start: Instant get() = timing.start

    val stop: Instant get() = timing.stop

    /** Whole seconds from [start] to [stop], as entrySeconds in the web app's lib/settings.ts. */
    val seconds: Long get() = maxOf(0L, Math.floorDiv(stop.toEpochMilli() - start.toEpochMilli(), 1000L))
}

/**
 * One day of the history in the account's timezone, with its entries newest first, and the
 * [rows] that show them: each entry on its own, or similar ones grouped as [rowsForDay] groups
 * them.
 */
@Immutable
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
 * A no-time entry goes under its stored day ([EntryTiming.AllDay.dayIn]), never the day of its start, which a
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
    older: List<TimeEntry>,
    projects: List<Project>,
    tasks: List<Task>,
    time: AccountTime,
    collapse: Boolean = true,
    windowStart: LocalDate? = null,
): List<HistoryDay> {
    val cachedIds = cached.mapTo(HashSet()) { it.entry.id }
    // An entry edited between two pages can come in both; the later page has its newer values.
    val olderOnly = older.associateBy { it.id }.values.filter { it.id !in cachedIds }
    val inWindow = { entry: TimeEntry -> windowStart == null || entry.dayIn(time) >= windowStart }
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
                val timing = row.entry.timing
                val date = if (timing is EntryTiming.AllDay) {
                    timing.dayIn(time.zone)
                } else {
                    time.local(shown.start).toLocalDate()
                }
                date to shown
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

private class Row(val entry: TimeEntry, val unsent: Boolean, val cached: Boolean)

/** The day of an entry as the cache holds it: a no-time entry's stored day, else the day of its start. */
private fun TimeEntry.dayIn(time: AccountTime): LocalDate = when (val timing = timing) {
    is EntryTiming.AllDay -> timing.dayIn(time.zone)
    else -> time.local(start).toLocalDate()
}

/** The entry as the history shows it, or null while it runs. */
private fun TimeEntry.shown(
    unsent: Boolean,
    cached: Boolean,
    time: AccountTime,
    projects: Map<ProjectId, Project>,
    tasks: Map<TaskId, Task>,
): HistoryEntry? {
    val ended = timing as? EntryTiming.Ended ?: return null
    // A no-time entry never goes through snapUnsent: its span is its day's midnight plus the snapped duration.
    val shown = when {
        !unsent -> ended

        ended is EntryTiming.AllDay -> time.noTimeEntry(
            ended.dayIn(time.zone),
            maxOf(0L, Math.floorDiv(ended.stop.toEpochMilli() - ended.start.toEpochMilli(), 1000L)),
            stored = ended.day,
        )

        // A rejected pending span keeps its actual times and unsent state for correction.
        else -> time.snapUnsent(ended.start, ended.stop)?.let { EntryTiming.Stopped(it.start, it.stop) } ?: ended
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
        timing = shown,
        unsent = unsent,
        cached = cached,
        projectId = projectId,
        taskId = taskId,
    )
}

private val NEWEST_FIRST = compareByDescending<HistoryEntry> { it.start }.thenByDescending { it.id.value }
