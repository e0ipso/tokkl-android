package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.entry.TimeRange
import com.github.e0ipso.tokkl.entry.snappedInstant
import com.github.e0ipso.tokkl.entry.snappedSeconds
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.addClockMinutes
import com.github.e0ipso.tokkl.time.isTimeResolution
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

// dragStep, droppedMinute, clampDayMove and fitMoveInDay port the functions of those names in
// ../tokkl/packages/web/src/lib/calendarDay.ts, and createdRange, movedRange and the resized
// ranges port finishDrag in ../tokkl/packages/web/src/components/WeekCalendar.tsx, at tokkl
// commit a3b30926b235c404b86e1784a9b4881b55755ed1. CalendarDragTest replays the cases from
// calendarDay.test.ts.

/** Calendar drags never step by less than this many minutes. */
private const val MIN_DRAG_STEP = 15

/**
 * The step, in minutes, that calendar drags move, resize and create entries in, and the shortest
 * block they leave: the smallest multiple of the time resolution that is at least 15 minutes.
 * Resolutions of 1, 3, 5 and 15 keep 15, 12 gives 24 and 20 gives 20, so a boundary on the
 * resolution grid stays on it after any number of steps.
 */
fun dragStep(resolution: Int): Int = (MIN_DRAG_STEP + resolution - 1) / resolution * resolution

/**
 * The time resolution that drags snap to, as the web app's settings read it: Tokkl's
 * `time_resolution_minutes`, or 1 on a server without it, such as Toggl Track.
 */
val AccountSettings.dragResolution: Int
    get() = timeResolutionMinutes?.takeIf(::isTimeResolution)?.toInt() ?: 1

/**
 * Where a dragged boundary at clock minute [minute] lands after moving [delta] minutes: the
 * nearest multiple of [resolution], a tie going later, as [snappedInstant] drops it. An entry
 * imported off the grid lands on it at the first step. A boundary that has not moved stays put.
 */
fun droppedMinute(minute: Double, delta: Int, resolution: Int): Double {
    if (delta == 0) return minute
    // Math.round, as JavaScript's, takes a tie up; kotlin.math.round would take it to even.
    return Math.round((minute + delta) / resolution).toDouble() * resolution
}

/**
 * Clamps a day-mode move so the block from clock minute [top] to [bottom] stays on the shown day.
 * A block cut off at midnight starts at 0 or ends at [DAY_MINUTES], so one that started the day
 * before cannot move earlier and one that runs into the next day cannot move later. The clamp can
 * shorten a move but never reverses it. A block with seconds stops at the last whole minute that
 * keeps it on the day, since moves go in whole clock minutes.
 */
fun clampDayMove(delta: Int, top: Double, bottom: Double): Int {
    val earliest = minOf(0, ceil(-top).toInt())
    val latest = maxOf(0, floor(DAY_MINUTES - bottom).toInt())
    return minOf(maxOf(delta, earliest), latest)
}

/**
 * The start to save for a day-mode move. [clampDayMove] works in clock minutes, but a moved entry
 * keeps its elapsed duration, and on the 25-hour autumn day an entry lasts longer than the clock
 * minutes it covers, so a move the clamp allows can still end after [dayEnd], the first instant
 * of the next day. The start then comes back so the entry ends exactly at the day's end. An entry
 * whose [originalStop] was already past the day's end is left alone.
 */
fun fitMoveInDay(movedStart: Instant, duration: Duration, originalStop: Instant, dayEnd: Instant): Instant {
    if (originalStop > dayEnd || movedStart + duration <= dayEnd) return movedStart
    return dayEnd - duration
}

/** A clock minute rounded to whole drag steps of [step], kept on the day, as snap in WeekCalendar.tsx. */
fun snapToStep(minutes: Double, step: Int): Int =
    (Math.round(minutes / step) * step).toInt().coerceIn(0, DAY_MINUTES)

/**
 * The entry a drag on empty space from clock minute [fromMinute] to [toMinute] on [day] creates,
 * or null for a drag that never grew. Both ends are whole drag steps of [step] from midnight, so
 * they are on the resolution grid. The entry is at least a step long, ends by 23:59 and stays on
 * the day.
 */
fun createdRange(day: LocalDate, fromMinute: Int, toMinute: Int, step: Int, time: AccountTime): TimeRange? {
    if (fromMinute == toMinute) return null
    val from = minOf(minOf(fromMinute, toMinute), DAY_MINUTES - step)
    val to = maxOf(fromMinute, toMinute, from + step)
    return TimeRange(time.instantAt(day, from), time.instantAt(day, minOf(to, DAY_MINUTES - 1)))
}

/**
 * Where the stopped entry from [start] to [stop] goes after a move of [deltaMinutes] clock
 * minutes. Blocks are drawn on the account's clock, so the move goes by clock minutes and not by
 * elapsed time. The start then lands on the nearest point of the resolution grid, as the preview
 * shows it, and the entry keeps its elapsed duration. In [dayMode], shown on [day] alone, the
 * stop the server will store stays on that day, also on the 25-hour autumn day.
 */
fun movedRange(start: Instant, stop: Instant, deltaMinutes: Int, day: LocalDate, dayMode: Boolean, time: AccountTime): TimeRange {
    var movedStart = droppedAt(start, deltaMinutes, time)
    if (dayMode) {
        val dayEnd = day.plusDays(1).atStartOfDay(time.zone).toInstant()
        val stored = Duration.ofSeconds(snappedSeconds(start, stop, time))
        movedStart = fitMoveInDay(movedStart, stored, stop, dayEnd)
    }
    return TimeRange(movedStart, movedStart + Duration.between(start, stop))
}

/**
 * The stopped entry from [start] to [stop] after its end moves [deltaMinutes] clock minutes, at
 * least [step] minutes after the start. The server moves an off-grid start to the grid and rounds
 * the duration up from there, so the start goes to the grid too, which keeps the end where it was
 * dropped.
 */
fun endResizedRange(start: Instant, stop: Instant, deltaMinutes: Int, step: Int, time: AccountTime): TimeRange {
    val gridStart = snappedInstant(start, time)
    return TimeRange(gridStart, maxOf(gridStart + Duration.ofMinutes(step.toLong()), droppedAt(stop, deltaMinutes, time)))
}

/**
 * The stopped entry from [start] to [stop] after its start moves [deltaMinutes] clock minutes, at
 * least [step] minutes before the end, which stays. The web app resizes only the end; this
 * mirrors that resize for the start.
 */
fun startResizedRange(start: Instant, stop: Instant, deltaMinutes: Int, step: Int, time: AccountTime): TimeRange {
    val latest = snappedInstant(stop - Duration.ofMinutes(step.toLong()), time)
    return TimeRange(minOf(droppedAt(start, deltaMinutes, time), latest), stop)
}

/** [at] moved [deltaMinutes] on the account's clock, then dropped on the resolution grid. */
private fun droppedAt(at: Instant, deltaMinutes: Int, time: AccountTime): Instant =
    snappedInstant(addClockMinutes(at, deltaMinutes, time.zone), time)
