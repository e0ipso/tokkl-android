package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.entryfields.noTimeEntry
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.LocalDate

/**
 * The entries that touch the week from [weekStart] in [time]'s zone: [cached] from the phone, with
 * queued changes applied, and [server], what the server sent for the week. An entry in both shows
 * as cached. A stopped entry with a change the server has not received shows the times the server
 * will store, as in the history.
 */
internal fun weekEntries(
    cached: List<EntryWithSendState>,
    server: List<TimeEntry>,
    projects: List<Project>,
    tasks: List<Task>,
    time: AccountTime,
    weekStart: LocalDate,
): List<CalendarEntry> {
    val from = weekStart.atStartOfDay(time.zone).toInstant()
    val to = weekStart.plusDays(7).atStartOfDay(time.zone).toInstant()
    val days = weekStart..weekStart.plusDays(6)
    val cachedIds = cached.mapTo(HashSet()) { it.entry.id }
    val rows = cached.map { it.entry to it.unsent } + server.filter { it.id !in cachedIds }.map { it to false }
    val projectsById = projects.associateBy { it.id }
    val tasksById = tasks.associateBy { it.id }
    return rows
        .map { (entry, unsent) -> entry.shown(unsent, time, projectsById, tasksById) }
        // A no-time entry belongs to its stored day, not to the instant of a midnight that a timezone change left
        // stale.
        .filter { shown ->
            when (val timing = shown.timing) {
                is EntryTiming.AllDay -> timing.day in days
                else -> shown.start < to && shown.stop.let { it == null || it > from }
            }
        }
}

private fun TimeEntry.shown(
    unsent: Boolean,
    time: AccountTime,
    projects: Map<ProjectId, Project>,
    tasks: Map<TaskId, Task>,
): CalendarEntry {
    // A no-time entry is on its stored day, from the midnight of the account's zone, with the
    // duration the server will store. The instant it was stored at may be stale after a timezone change.
    // The server keeps a running entry's start as sent, so only a stopped one is snapped.
    // A rejected pending span keeps its actual times and unsent state for correction.
    val shown = when (val timing = timing) {
        is EntryTiming.Running -> timing

        is EntryTiming.AllDay -> {
            val day = timing.dayIn(time.zone)
            if (unsent) time.noTimeEntry(day, timing.duration.seconds) else timing.copy(day = day)
        }

        is EntryTiming.Stopped -> timing.takeIf { unsent }?.let { time.snapUnsent(it.start, it.stop) }
            ?.let { EntryTiming.Stopped(it.start, it.stop) } ?: timing
    }
    val project = projectId?.let(projects::get)
    return CalendarEntry(
        id = id,
        description = description,
        project = project?.name,
        projectColor = project?.color,
        task = taskId?.let(tasks::get)?.name,
        timing = shown,
        unsent = unsent,
    )
}
