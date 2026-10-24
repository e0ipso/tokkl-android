package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.DurationFormat
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Today and this week under the timer. The clock is an argument, so none of these read the
 * device's zone. Tokyo is UTC+9 with no daylight-saving transition.
 */
class TimerTotalsTest {

    @Test
    fun anEmptyDayFormatsZeroInTheAccountDurationFormat() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val totals = timerPeriodTotals(emptyList(), tokyo(DayOfWeek.MONDAY), now)

        assertEquals(TimerPeriodTotals(0, 0), totals)
        assertEquals("0:00:00", totals.formatted(settings(DurationFormat.IMPROVED)).today)
        assertEquals("0:00:00", totals.formatted(settings(DurationFormat.IMPROVED)).week)
        assertEquals("0 sec", totals.formatted(settings(DurationFormat.CLASSIC)).today)
        assertEquals("0.00 h", totals.formatted(settings(DurationFormat.DECIMAL)).week)
    }

    @Test
    fun tokyoTodayIsNotTheUtcDay() {
        // 16:00Z is 01:00 on the 24th in Tokyo and still the 23rd in UTC.
        val now = Instant.parse("2026-10-23T16:00:00Z")
        val yesterdayInTokyo = entry(1, "2026-10-23T14:00:00Z", "2026-10-23T14:01:00Z")
        val todayInTokyo = entry(2, "2026-10-23T15:30:00Z", "2026-10-23T15:31:00Z")
        val entries = listOf(yesterdayInTokyo, todayInTokyo)

        val totals = timerPeriodTotals(entries, tokyo(DayOfWeek.MONDAY), now)
        val throughSettings = timerPeriodTotals(entries, settings(DurationFormat.IMPROVED), now)

        assertEquals("the 23rd in Tokyo is not today", 60L, totals.todaySeconds)
        assertEquals(120L, totals.weekSeconds)
        assertEquals(totals, throughSettings)
    }

    @Test
    fun aSundayStartIncludesTheSundayAMondayStartLeavesOut() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val sunday = entry(1, "2026-10-17T15:30:00Z", "2026-10-17T15:31:00Z")

        assertEquals(60L, timerPeriodTotals(listOf(sunday), tokyo(DayOfWeek.SUNDAY), now).weekSeconds)
        assertEquals(0L, timerPeriodTotals(listOf(sunday), tokyo(DayOfWeek.MONDAY), now).weekSeconds)
        assertEquals(0L, timerPeriodTotals(listOf(sunday), tokyo(DayOfWeek.SUNDAY), now).todaySeconds)
    }

    @Test
    fun secondsFloorAtWholeSecondsAndABackwardSpanCountsAsZero() {
        val start = Instant.parse("2026-10-24T01:00:00Z")
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val one = entry(1, start.toString(), start.plusMillis(1_999).toString())
        val none = entry(2, start.plusSeconds(10).toString(), start.plusSeconds(10).plusMillis(999).toString())
        val backward = entry(3, start.plusSeconds(20).toString(), start.plusSeconds(20).minusMillis(1).toString())

        val totals = timerPeriodTotals(listOf(one, none, backward), tokyo(DayOfWeek.MONDAY), now)

        assertEquals(1L, totals.todaySeconds)

        val submillisecond = entry(4, start.plusNanos(999_999).toString(), start.plusSeconds(1).toString())
        assertEquals("floor elapsed time before truncating timestamps", 0L,
            timerPeriodTotals(listOf(submillisecond), tokyo(DayOfWeek.MONDAY), now).todaySeconds)
    }

    @Test
    fun aRunningEntryCountsOnceUpToNowAndKeepsTicking() {
        val start = Instant.parse("2026-10-24T02:00:00Z")
        val running = entry(7, start.toString(), stop = null)
        val earlier = timerPeriodTotals(listOf(running), tokyo(DayOfWeek.MONDAY), start.plusMillis(1_500))
        val later = timerPeriodTotals(listOf(running), tokyo(DayOfWeek.MONDAY), start.plusSeconds(40))

        assertEquals(1L, earlier.todaySeconds)
        assertEquals(1L, earlier.weekSeconds)
        assertEquals(40L, later.todaySeconds)
    }

    @Test
    fun pauseSegmentsCountOnceAndNotAgainAsTheChainTotal() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val stopped = entry(1, "2026-10-24T01:00:00Z", "2026-10-24T01:10:00Z")
        val running = entry(2, now.minusSeconds(100).toString(), stop = null)
        val resumed = timerPeriodTotals(listOf(stopped, running), tokyo(DayOfWeek.MONDAY), now)

        // The running clock would show the 600 stored seconds plus these 100. The totals do not.
        assertEquals(700L, resumed.todaySeconds)
        assertEquals(700L, resumed.weekSeconds)

        val paused = listOf(stopped, entry(3, "2026-10-24T01:20:00Z", "2026-10-24T01:26:40Z"))
        assertEquals(1_000L, timerPeriodTotals(paused, tokyo(DayOfWeek.MONDAY), now).todaySeconds)
    }

    @Test
    fun anOvernightEntryStaysOnItsStartDayIncludingWhileItRuns() {
        val now = Instant.parse("2026-10-23T16:00:00Z")
        val running = entry(1, "2026-10-23T14:00:00Z", stop = null)
        val stopped = entry(2, "2026-10-23T12:00:00Z", "2026-10-23T15:00:00Z")

        val totals = timerPeriodTotals(listOf(running, stopped), tokyo(DayOfWeek.MONDAY), now)

        assertEquals("both start on the 23rd in Tokyo", 0L, totals.todaySeconds)
        assertEquals(2 * 3600L + 3 * 3600L, totals.weekSeconds)
    }

    @Test
    fun daylightSavingUsesElapsedTime() {
        val zone = ZoneId.of("America/New_York")
        val springStart = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone)
        val springStop = ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, zone)
        val springNow = ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, zone).toInstant()
        val spring = timerPeriodTotals(
            listOf(entry(1, springStart.toInstant().toString(), springStop.toInstant().toString())),
            newYork(),
            springNow,
        )

        assertEquals(3_600L, Duration.between(springStart.toInstant(), springStop.toInstant()).seconds)
        assertEquals(3_600L, spring.todaySeconds)

        val fallStart = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, zone).withEarlierOffsetAtOverlap()
        val fallStop = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, zone).withLaterOffsetAtOverlap()
        val fallNow = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, zone).toInstant()
        val fall = timerPeriodTotals(
            listOf(entry(2, fallStart.toInstant().toString(), fallStop.toInstant().toString())),
            newYork(),
            fallNow,
        )

        assertEquals(3_600L, Duration.between(fallStart.toInstant(), fallStop.toInstant()).seconds)
        assertEquals(3_600L, fall.todaySeconds)
    }

    @Test
    fun aLaterStartDayIsOutsideTodayAndThisWeek() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val today = entry(1, "2026-10-24T01:00:00Z", "2026-10-24T01:01:00Z")
        val tomorrow = entry(2, "2026-10-24T15:30:00Z", "2026-10-24T16:30:00Z")

        val totals = timerPeriodTotals(listOf(today, tomorrow), tokyo(DayOfWeek.MONDAY), now)

        assertEquals(60L, totals.todaySeconds)
        assertEquals(60L, totals.weekSeconds)
    }

    @Test
    fun anOlderWeekDoesNotMoveTheReferenceOffToday() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val lastWeek = entry(1, "2026-10-10T03:00:00Z", "2026-10-10T04:00:00Z")
        val today = entry(2, "2026-10-24T01:00:00Z", "2026-10-24T01:01:00Z")

        val totals = timerPeriodTotals(listOf(lastWeek, today), tokyo(DayOfWeek.MONDAY), now)

        assertEquals(60L, totals.todaySeconds)
        assertEquals(60L, totals.weekSeconds)
    }

    @Test
    fun theAccountClockDoesNotDecideTheDay() {
        val stale = AccountTime(
            TOKYO,
            DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
            clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val today = entry(1, "2026-10-24T02:00:00Z", "2026-10-24T02:01:00Z")

        assertEquals(60L, timerPeriodTotals(listOf(today), stale, now).todaySeconds)
    }

    @Test
    fun offlineEditsAreTheCachedRowsTheQueueLeavesBehind() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val time = tokyo(DayOfWeek.MONDAY)
        val started = entry(4, now.minusSeconds(30).toString(), stop = null)

        assertEquals(0L, timerPeriodTotals(emptyList(), time, now).todaySeconds)
        assertEquals(30L, timerPeriodTotals(listOf(started), time, now).todaySeconds)

        val stopped = started.copy(stop = now.minusSeconds(10))
        assertEquals(20L, timerPeriodTotals(listOf(stopped), time, now.plusSeconds(100)).todaySeconds)

        val edited = stopped.copy(stop = stopped.start.plusSeconds(50))
        assertEquals(50L, timerPeriodTotals(listOf(edited), time, now).todaySeconds)
        assertEquals(0L, timerPeriodTotals(emptyList(), time, now).todaySeconds)
        assertEquals(50L, timerPeriodTotals(listOf(edited), time, now).todaySeconds)
    }

    @Test
    fun aRemappedEntryCountsOnceAndKeepsTheLocalRow() {
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val time = tokyo(DayOfWeek.MONDAY)
        val local = entry(-4, "2026-10-24T01:00:00Z", "2026-10-24T01:00:40Z")
        val server = local.copy(id = 80, stop = local.start.plusSeconds(10))
        val map = mapOf(local.id to server.id)

        assertEquals(40L, timerPeriodTotals(listOf(local), time, now).todaySeconds)
        assertEquals(10L, timerPeriodTotals(listOf(server), time, now).todaySeconds)
        assertEquals(40L, timerPeriodTotals(listOf(server, local), time, now, map).todaySeconds)
        assertEquals(40L, timerPeriodTotals(listOf(local, server), time, now, map).weekSeconds)

        val twin = local.copy(stop = local.start.plusSeconds(5))
        assertEquals(40L, timerPeriodTotals(listOf(local, twin), time, now).todaySeconds)
        val otherLocal = entry(-5, "2026-10-24T02:00:00Z", "2026-10-24T02:00:15Z")
        val collapsed = timerPeriodTotals(
            listOf(local, otherLocal),
            time,
            now,
            mapOf(local.id to 80L, otherLocal.id to 80L),
        )
        assertEquals(40L, collapsed.todaySeconds)
    }

    @Test
    fun anUnresolvedAccountZoneHasNoTotals() {
        val settings = AccountSettings("GMT+5", DayOfWeek.MONDAY, timeResolutionMinutes = null)
        val now = Instant.parse("2026-10-24T03:00:00Z")
        val entry = entry(1, "2026-10-24T01:00:00Z", "2026-10-24T02:00:00Z")

        assertNull(settings.time())
        assertNull(timerPeriodTotals(listOf(entry), settings, now))
    }

    @Test
    fun midnightAndTheWeekRollWithTheAccountClock() {
        val saturdayNight = Instant.parse("2026-10-24T14:59:00Z")
        val sundayMorning = Instant.parse("2026-10-24T15:01:00Z")
        val saturday = entry(1, "2026-10-24T03:00:00Z", "2026-10-24T03:01:00Z")

        val beforeSunday = timerPeriodTotals(listOf(saturday), tokyo(DayOfWeek.SUNDAY), saturdayNight)
        val afterSunday = timerPeriodTotals(listOf(saturday), tokyo(DayOfWeek.SUNDAY), sundayMorning)
        val mondayWeek = timerPeriodTotals(listOf(saturday), tokyo(DayOfWeek.MONDAY), sundayMorning)

        assertEquals(TimerPeriodTotals(60, 60), beforeSunday)
        assertEquals(TimerPeriodTotals(0, 0), afterSunday)
        assertEquals(TimerPeriodTotals(0, 60), mondayWeek)
    }

    @Test
    fun theNextReadIsASecondWhileRunningAndAccountMidnightWhileIdle() {
        val ticking = Instant.parse("2026-10-24T14:59:30.400Z")
        val idle = Instant.parse("2026-10-24T14:59:30Z")

        assertEquals(600L, nextTotalsDelay(ticking, tokyo(DayOfWeek.MONDAY), hasRunning = true))
        assertEquals(30_000L, nextTotalsDelay(idle, tokyo(DayOfWeek.MONDAY), hasRunning = false))
    }

    @Test
    fun accountMidnightUsesElapsedTimeAcrossTheSpringForward() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, zone).toInstant()
        val midnight = LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant()
        val wait = nextTotalsDelay(now, newYork(), hasRunning = false)

        assertEquals(Duration.between(now, midnight).toMillis(), wait)
        assertEquals(22L * 3_600 * 1_000, wait)
    }

    private fun tokyo(weekStart: DayOfWeek) = AccountTime(TOKYO, weekStart, timeResolutionMinutes = null, clock = CLOCK)

    private fun newYork() = AccountTime(ZoneId.of("America/New_York"), DayOfWeek.SUNDAY, timeResolutionMinutes = null, clock = CLOCK)

    private fun settings(format: DurationFormat) =
        AccountSettings("Asia/Tokyo", DayOfWeek.MONDAY, timeResolutionMinutes = null, durationFormat = format)

    private fun entry(id: Long, start: String, stop: String?) = CachedTimeEntry(
        id = id,
        workspaceId = 1,
        projectId = null,
        taskId = null,
        description = "Entry $id",
        start = Instant.parse(start),
        stop = stop?.let(Instant::parse),
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private companion object {
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
