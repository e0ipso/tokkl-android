package com.github.e0ipso.tokkl.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Port of zonedToUtc and addClockMinutes in ../tokkl/packages/common/time.ts at tokkl commit
// bafe8eb953c258804bb100850a5faa475823ce91. ZonedTimeTest replays the cases from time.test.ts.

private const val DAY_MS = 86_400_000L
private const val HOUR_MS = 3_600_000L
private const val MINUTE_MS = 60_000L
private const val SECOND_MS = 1_000L

/**
 * The instant at which the wall clock in [zone] shows the given time, as zonedToUtc in Tokkl.
 *
 * It guesses the offset at the wall time read as UTC, then once more at that guess. That settles a
 * guess that landed on the other side of a clock change, and decides the two cases a wall time
 * can fall into there:
 * - a time in the spring gap, which the clock skips, lands after the gap: 02:30 in Madrid on
 *   2026-03-29 is 03:30 CEST;
 * - a time in the repeated autumn hour, which the clock shows twice, is the later one: 02:30 in
 *   Madrid on 2026-10-25 is 02:30 CET, where java.time's `ZonedDateTime.of` takes the earlier.
 *
 * As JavaScript's `Date.UTC`, fields out of their range carry over, so minute 90 is half past the
 * next hour and day 0 the last day of the month before, and a year from 0 to 99 means 1900 to
 * 1999.
 */
fun zonedToUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, zone: ZoneId): Instant {
    val wall = utcMillis(year, month, day, hour.toLong(), minute.toLong(), second.toLong())
    var guess = wall - offsetMillis(wall, zone)
    // A second pass settles guesses that landed on the other side of a DST change.
    guess = wall - offsetMillis(guess, zone)
    return Instant.ofEpochMilli(guess)
}

/**
 * Moves [instant] by [minutes] on the wall clock of [zone], as addClockMinutes in Tokkl, so 01:00
 * plus 120 is 03:00 even when the clocks change in between. A result in the spring gap lands after
 * it, and one in the repeated autumn hour is the later one, as [zonedToUtc] decides. The seconds
 * and milliseconds of [instant] stay as they are.
 */
fun addClockMinutes(instant: Instant, minutes: Int, zone: ZoneId): Instant {
    val wall = instant.atZone(zone)
    val shifted = zonedToUtc(wall.year, wall.monthValue, wall.dayOfMonth, wall.hour, wall.minute + minutes, wall.second, zone)
    return shifted.plusMillis(Math.floorMod(instant.toEpochMilli(), SECOND_MS))
}

/** JavaScript's `Date.UTC(year, month - 1, day, hour, minute, second)`, months counted from 1. */
private fun utcMillis(year: Int, month: Int, day: Int, hour: Long, minute: Long, second: Long): Long {
    val fullYear = if (year in 0..99) 1900 + year else year
    val days = LocalDate.of(fullYear, 1, 1).plusMonths(month - 1L).toEpochDay() + (day - 1L)
    return days * DAY_MS + hour * HOUR_MS + minute * MINUTE_MS + second * SECOND_MS
}

/** The offset of [zone] from UTC at [epochMillis], east positive, as timeZoneOffsetMs in Tokkl. */
private fun offsetMillis(epochMillis: Long, zone: ZoneId): Long =
    zone.rules.getOffset(Instant.ofEpochMilli(epochMillis)).totalSeconds * SECOND_MS
