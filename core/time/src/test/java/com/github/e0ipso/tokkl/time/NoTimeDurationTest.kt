package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The no-time duration check and the span a no-time entry stores, in the account's zone. */
class NoTimeDurationTest {

    private val format = "Enter a duration like 1:30 or 90m"
    private val tooLong = "No-time entries can be at most 24 hours"

    @Test
    fun aValidDurationComesBackSnapped() {
        assertEquals(NoTimeDuration(5400, null), noTimeDuration("90m", 15.0))
        assertEquals(NoTimeDuration(5400, null), noTimeDuration("1:30", 1.0))
        assertEquals(NoTimeDuration(1800, null), noTimeDuration("25m", 15.0))
    }

    @Test
    fun unreadableTextHasNoSecondsAndAsksForAFormat() {
        assertEquals(NoTimeDuration(null, format), noTimeDuration("soon", 15.0))
        assertEquals(NoTimeDuration(null, format), noTimeDuration("", 15.0))
        // Over the 999-hour limit of parseDuration.
        assertEquals(NoTimeDuration(null, format), noTimeDuration("1000h", 15.0))
    }

    @Test
    fun aDurationThatSnapsToZeroNamesTheResolution() {
        assertEquals(
            NoTimeDuration(0, "The duration rounds to zero at the 15-minute time resolution"),
            noTimeDuration("5m", 15.0),
        )
        assertEquals(
            NoTimeDuration(0, "The duration rounds to zero at the 1-minute time resolution"),
            noTimeDuration("20s", 1.0),
        )
    }

    @Test
    fun moreThanADayIsRefusedAfterSnapping() {
        assertEquals(NoTimeDuration(90_000, tooLong), noTimeDuration("25h", 15.0))
        assertEquals(NoTimeDuration(86_400, null), noTimeDuration("24h", 15.0))
        // 24:07 snaps down to 24:00, which is allowed; 24:08 snaps up to 24:15.
        assertEquals(NoTimeDuration(86_400, null), noTimeDuration("24:07", 15.0))
        assertEquals(NoTimeDuration(87_300, tooLong), noTimeDuration("24:08", 15.0))
    }

    @Test
    fun withoutAResolutionTheRawValueIsCheckedForZeroAndForADay() {
        assertEquals(NoTimeDuration(90, null), noTimeDuration("90s", null))
        assertEquals(NoTimeDuration(86_401, tooLong), noTimeSeconds(86_401, null))
        assertEquals(NoTimeDuration(0, format), noTimeSeconds(0, null))
    }

    @Test
    fun aDurationFieldThatCommitsSecondsGetsTheSameCheck() {
        assertEquals(NoTimeDuration(1800, null), noTimeSeconds(1500, 15.0))
        assertEquals(
            NoTimeDuration(0, "The duration rounds to zero at the 30-minute time resolution"),
            noTimeSeconds(600, 30.0),
        )
    }

    @Test
    fun aResolutionThatDoesNotDivideAnHourIsTreatedAsNone() {
        assertEquals(NoTimeDuration(600, null), noTimeSeconds(600, 7.0))
    }

    @Test
    fun theSpanStartsAtLocalMidnightAndLastsTheSnappedDuration() {
        val madrid = AccountTime(ZoneId.of("Europe/Madrid"), DayOfWeek.MONDAY, 15.0)
        val span = madrid.noTimeSpan(LocalDate.of(2026, 10, 14), 5400 + 200)
        assertEquals(Instant.parse("2026-10-13T22:00:00Z"), span.start)
        assertEquals(Instant.parse("2026-10-13T23:30:00Z"), span.stop)
    }

    @Test
    fun theSpanFollowsTheAccountZoneAcrossADstChange() {
        val madrid = AccountTime(ZoneId.of("Europe/Madrid"), DayOfWeek.MONDAY, null)
        // Madrid leaves summer time on 25 October 2026, so that day starts at UTC+2.
        val span = madrid.noTimeSpan(LocalDate.of(2026, 10, 25), 3600)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), span.start)
        assertEquals(Instant.parse("2026-10-24T23:00:00Z"), span.stop)
    }

    @Test
    fun accountTimeSnapsADurationToItsResolution() {
        assertEquals(900L, AccountTime(ZoneId.of("UTC"), DayOfWeek.MONDAY, 15.0).snapDuration(450))
        assertEquals(450L, AccountTime(ZoneId.of("UTC"), DayOfWeek.MONDAY, null).snapDuration(450))
    }
}
