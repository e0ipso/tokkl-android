package com.github.e0ipso.tokkl.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How the flat start, stop, no-time flag and day of the cache's rows and the saved state read. */
class EntryTimingTest {

    @Test
    fun aRowWithoutAStopRunsWhateverItsNoTimeFlagSays() {
        assertEquals(EntryTiming.Running(START), EntryTiming.of(START, null, allDay = false, day = null))
        assertEquals(EntryTiming.Running(START), EntryTiming.of(START, null, allDay = true, day = DAY))
    }

    @Test
    fun aTimedRowKeepsNoDay() {
        assertEquals(EntryTiming.Stopped(START, STOP), EntryTiming.of(START, STOP, allDay = false, day = DAY))
    }

    @Test
    fun aNoTimeRowKeepsItsDayAndDuration() {
        val timing = EntryTiming.of(START, STOP, allDay = true, day = DAY)

        assertEquals(EntryTiming.AllDay(DAY, START, Duration.ofMinutes(90)), timing)
        assertEquals(STOP, (timing as EntryTiming.AllDay).stop)
    }

    /** The server leaves the day out: its midnight start names it in the account's timezone. */
    @Test
    fun aNoTimeEntryWithoutADayTakesTheDateOfItsMidnightInTheAccountZone() {
        val fromServer = EntryTiming.AllDay(null, START, Duration.ofMinutes(90))

        assertEquals(LocalDate.of(2026, 10, 14), fromServer.dayIn(ZoneId.of("Europe/Madrid")))
        assertEquals(LocalDate.of(2026, 10, 13), fromServer.dayIn(ZoneId.of("UTC")))
        assertEquals("a stored day wins", DAY, fromServer.copy(day = DAY).dayIn(ZoneId.of("UTC")))
    }

    private companion object {
        /** Midnight of 14 October 2026 in Madrid. */
        val START: Instant = Instant.parse("2026-10-13T22:00:00Z")
        val STOP: Instant = Instant.parse("2026-10-13T23:30:00Z")
        val DAY: LocalDate = LocalDate.of(2026, 10, 14)
    }
}
