package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.noTimeDay
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Duration
import java.time.LocalDate

/**
 * The entries that touch the week from [weekStart] in [time]'s zone: [cached] from the phone, with
 * queued changes applied, and [server], what the server sent for the week. An entry in both shows
 * as cached. A stopped entry with a change the server has not received shows the times the server
 * will store, as in the history.
 */
internal fun weekEntries(
    cached: List<EntryWithSendState>,
    server: List<CachedTimeEntry>,
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
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
        // A no-time entry belongs to its stored day, not to the instant of a midnight that a timezone change left stale.
        .filter { if (it.allDay) it.day in days else it.start < to && (it.stop == null || it.stop > from) }
}

/** The entry with the times the server stores once it gets it: a stopped entry's span snapped. */
internal fun CachedTimeEntry.stored(time: AccountTime): CachedTimeEntry {
    if (allDay) {
        val span = stop?.let { time.noTimeSpan(noTimeDay(time.zone), Duration.between(start, it).seconds) } ?: return this
        return copy(start = span.start, stop = span.stop)
    }
    val stop = stop?.takeIf { !it.isBefore(start) } ?: return this
    val span = time.snapUnsent(start, stop) ?: return this
    return copy(start = span.start, stop = span.stop)
}

private fun CachedTimeEntry.shown(
    unsent: Boolean,
    time: AccountTime,
    projects: Map<Long, CachedProject>,
    tasks: Map<Long, CachedTask>,
): CalendarEntry {
    // A no-time entry is on its stored day, from the midnight of the account's zone, with the
    // duration the server will store. The instant it was stored at may be stale after a timezone change.
    val noTimeDay = noTimeDay(time.zone).takeIf { allDay }
    // The server keeps a running entry's start as sent, so only a stopped one is snapped.
    // A rejected pending span keeps its actual times and unsent state for correction.
    val span = stop?.takeIf { unsent }?.let {
        if (noTimeDay != null) time.noTimeSpan(noTimeDay, Duration.between(start, it).seconds) else time.snapUnsent(start, it)
    }
    val project = projectId?.let(projects::get)
    return CalendarEntry(
        id = id,
        description = description,
        project = project?.name,
        projectColor = project?.color,
        task = taskId?.let(tasks::get)?.name,
        start = span?.start ?: start,
        stop = span?.stop ?: stop,
        unsent = unsent,
        allDay = allDay,
        day = noTimeDay,
    )
}
