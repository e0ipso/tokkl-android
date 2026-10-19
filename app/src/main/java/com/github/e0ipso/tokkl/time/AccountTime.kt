package com.github.e0ipso.tokkl.time

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Times in the account's timezone, the one zone the app shows and reads times in (docs/PRD.md
 * S7). Screens take the zone, the current time, today's date and week boundaries from here and
 * never from the device: no `ZoneId.systemDefault()`, no `TimeZone.getDefault()` and no `now()`
 * without a clock. [com.github.e0ipso.tokkl.session.AccountSettings.time] builds one.
 *
 * @param clock where the current instant comes from. Its own zone does not matter.
 */
class AccountTime(
    val zone: ZoneId,
    val firstDayOfWeek: DayOfWeek,
    private val timeResolutionMinutes: Double?,
    clock: Clock = Clock.systemUTC(),
) {

    /** The clock in the account's zone, for java.time's `now(clock)` functions. */
    val clock: Clock = clock.withZone(zone)

    fun now(): ZonedDateTime = ZonedDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    /** [instant] on the account's wall clock, for display. */
    fun local(instant: Instant): ZonedDateTime = instant.atZone(zone)

    /**
     * [minutes] after midnight on [date] on the account's wall clock, as instantAt in the web app's
     * lib/entryTime.ts. [zonedToUtc] decides a time in the spring gap or the repeated autumn hour.
     */
    fun instantAt(date: LocalDate, minutes: Int): Instant =
        zonedToUtc(date.year, date.monthValue, date.dayOfMonth, Math.floorDiv(minutes, 60), minutes % 60, 0, zone)

    /** Minutes after midnight on the account's wall clock at [instant], as minutesOfDay in the web app's lib/settings.ts. */
    fun minutesOfDay(instant: Instant): Int = local(instant).let { it.hour * 60 + it.minute }

    /** The first day of the week that holds [date], as startOfWeek in ../tokkl/packages/common/time.ts. */
    fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /**
     * The span the server will store for a stopped entry it has not received yet. This is
     * [snapUnsent][com.github.e0ipso.tokkl.time.snapUnsent] in the account's zone with the
     * account's time resolution. Without a resolution the span comes back unchanged. Never pass
     * the running entry.
     */
    fun snapUnsent(start: Instant, stop: Instant): SnappedSpan =
        snapUnsent(start, stop, timeResolutionMinutes, zone)
}
