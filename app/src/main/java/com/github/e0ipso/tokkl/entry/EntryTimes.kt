package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.parseTimeOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// resolveRange ports resolveRange in ../tokkl/packages/web/src/lib/entryTime.ts at tokkl commit
// c8dd0fd9bf5f9f67ccbf59079dacbf43c94c480f, and snappedSeconds ports snappedSeconds in
// ../tokkl/packages/web/src/lib/settings.ts at tokkl commit e3f15ff628d88a476b77e51ef77c9172903d6db4.

/** A stopped entry's span. */
data class TimeRange(val start: Instant, val stop: Instant)

/**
 * The span of a start and an end typed for [day], in the account's timezone, or null when either
 * cannot be read. The end always lands at or after the start, on the next calendar day when it is
 * earlier on the clock, so 23:00 to 01:00 crosses midnight, as in Toggl's manual mode. For
 * 12-hour users a start without AM or PM is the one nearer to [nowMinutes], and an end without
 * them the first one after the start, so 11pm to "1" is 1 AM.
 */
fun resolveRange(
    day: LocalDate,
    startText: String,
    endText: String,
    time: AccountTime,
    twelveHour: Boolean,
    nowMinutes: Int,
): TimeRange? {
    val startMinutes = parseTimeOfDay(startText, twelveHour = twelveHour, reference = nowMinutes) ?: return null
    val endMinutes = parseTimeOfDay(endText, twelveHour = twelveHour, after = startMinutes) ?: return null
    val start = time.instantAt(day, startMinutes)
    val endDay = if (endMinutes < startMinutes) day.plusDays(1) else day
    return TimeRange(start, time.instantAt(endDay, endMinutes))
}

/**
 * The seconds the server will store for a stopped entry from [start] to [stop]: both cut to whole
 * seconds, then snapped to the account's time resolution. Zero for a rejected short span or
 * when the stop is not after the start. This is display only; saves validate the nullable span.
 */
fun snappedSeconds(start: Instant, stop: Instant, time: AccountTime): Long {
    val from = start.truncatedTo(ChronoUnit.SECONDS)
    val to = stop.truncatedTo(ChronoUnit.SECONDS)
    if (!to.isAfter(from)) return 0
    val span = time.snapUnsent(from, to) ?: return 0
    return span.stop.epochSecond - span.start.epochSecond
}
