package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.entry.TimeRange
import com.github.e0ipso.tokkl.entry.snappedInstant
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.addClockMinutes
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar's drag arithmetic. The first tests replay the cases of calendarDay.test.ts in
 * ../tokkl/packages/web/src/lib, at tokkl commit a3b30926b235c404b86e1784a9b4881b55755ed1; the
 * rest check the spans a drop saves, as finishDrag in the web app's WeekCalendar.tsx saves them.
 */
class CalendarDragTest {

    @Test
    fun dragStepIsTheSmallestMultipleOfTheResolutionThatIsAtLeast15Minutes() {
        val steps = mapOf(1 to 15, 2 to 16, 3 to 15, 4 to 16, 5 to 15, 6 to 18, 10 to 20, 12 to 24, 15 to 15, 20 to 20, 30 to 30, 60 to 60)
        for ((resolution, step) in steps) {
            assertEquals("resolution $resolution", step, dragStep(resolution))
            assertEquals("resolution $resolution", 0, step % resolution)
        }
    }

    @Test
    fun droppedMinuteKeepsAnOnGridBoundaryOnTheGridAndLandsAnOffGridOneOnIt() {
        // At the default resolution of 5 the step is 15, and an on-grid block moves by whole steps.
        assertEquals(9 * 60 + 15.0, droppedMinute(9 * 60.0, dragStep(5), 5), 0.0)
        assertEquals(9 * 60 + 20.0, droppedMinute(9 * 60 + 7.0, dragStep(5), 5), 0.0)
        // At 12, the bottom of 09:00-09:12 goes one 24-minute step to 09:36.
        assertEquals(9 * 60 + 36.0, droppedMinute(9 * 60 + 12.0, dragStep(12), 12), 0.0)
        // An imported 09:07 start moved two steps reaches 09:55 and lands on 10:00; one step back
        // reaches 08:43 and lands on 08:48.
        assertEquals(10 * 60.0, droppedMinute(9 * 60 + 7.0, 2 * dragStep(12), 12), 0.0)
        assertEquals(8 * 60 + 48.0, droppedMinute(9 * 60 + 7.0, -dragStep(12), 12), 0.0)
        // Halfway between two grid points the later one wins, as on the server.
        assertEquals(36.0, droppedMinute(6.0, 24, 12), 0.0)
        // A boundary that has not moved stays where it is, on the grid or not.
        assertEquals(9 * 60 + 7.0, droppedMinute(9 * 60 + 7.0, 0, 12), 0.0)
    }

    @Test
    fun droppedMinuteShowsTheClockMinuteThatSnappedInstantDropsTheBoundaryOn() {
        // Wednesday 2026-06-17 in Madrid, from an on-grid 09:00 and from an imported 09:07:30.
        for (from in listOf("2026-06-17T07:00:00Z", "2026-06-17T07:07:30Z")) {
            val boundary = Instant.parse(from)
            for (resolution in listOf(1, 2, 5, 6, 12, 20, 60)) {
                val time = AccountTime(MADRID, DayOfWeek.MONDAY, resolution.toDouble())
                for (steps in listOf(-2, -1, 1, 3)) {
                    val delta = steps * dragStep(resolution)
                    val dropped = snappedInstant(addClockMinutes(boundary, delta, MADRID), time)
                    assertEquals(
                        "$from, resolution $resolution, $steps steps",
                        clockMinutes(dropped),
                        droppedMinute(clockMinutes(boundary), delta, resolution),
                        0.0,
                    )
                }
            }
        }
    }

    @Test
    fun clampDayMoveKeepsAMovedBlockInsideTheSelectedDay() {
        // 09:00 to 10:00.
        assertEquals(-60, clampDayMove(-60, 540.0, 600.0))
        assertEquals(180, clampDayMove(180, 540.0, 600.0))
        assertEquals(-540, clampDayMove(-600, 540.0, 600.0))
        assertEquals(840, clampDayMove(900, 540.0, 600.0))
        // A block already at an edge stays put when dragged past it.
        assertEquals(0, clampDayMove(-30, 0.0, 60.0))
        assertEquals(0, clampDayMove(30, 1380.0, 1440.0))
    }

    @Test
    fun clampDayMovePinsTheEdgesOfBlocksCutOffAtMidnight() {
        // 23:00 on the 17th to 01:00 on the 18th, Madrid time.
        val start = Instant.parse("2026-10-17T21:00:00Z")
        val stop = Instant.parse("2026-10-17T23:00:00Z")
        // On the 18th it started the day before, so it can move later but not earlier.
        val fromYesterday = clockSpanOnDay(start, stop, LocalDate.of(2026, 10, 18), MADRID)!!
        assertEquals(0.0 to 60.0, fromYesterday)
        assertEquals(0, clampDayMove(-15, fromYesterday.first, fromYesterday.second))
        assertEquals(60, clampDayMove(60, fromYesterday.first, fromYesterday.second))
        assertEquals(1380, clampDayMove(2000, fromYesterday.first, fromYesterday.second))
        // On the 17th it runs into the next day, so it can move earlier but not later.
        val intoTomorrow = clockSpanOnDay(start, stop, LocalDate.of(2026, 10, 17), MADRID)!!
        assertEquals(1380.0 to 1440.0, intoTomorrow)
        assertEquals(0, clampDayMove(15, intoTomorrow.first, intoTomorrow.second))
        assertEquals(-60, clampDayMove(-60, intoTomorrow.first, intoTomorrow.second))
        assertEquals(-1380, clampDayMove(-2000, intoTomorrow.first, intoTomorrow.second))
        // A block covering the whole day can't move at all.
        assertEquals(0, clampDayMove(-45, 0.0, 1440.0))
        assertEquals(0, clampDayMove(45, 0.0, 1440.0))
    }

    @Test
    fun clampDayMoveNeverPushesABlockAgainstTheDrag() {
        // Layout makes every block at least 5 minutes tall, so one at 23:58 reaches past 24:00. An
        // unmoved drag must still be 0, or a plain click would move the entry.
        assertEquals(0, clampDayMove(0, 1438.0, 1443.0))
        assertEquals(0, clampDayMove(15, 1438.0, 1443.0))
        assertEquals(-15, clampDayMove(-15, 1438.0, 1443.0))
    }

    /** Moves go in whole clock minutes, so a block with seconds stops at the last minute that keeps it on the day. */
    @Test
    fun clampDayMoveStopsABlockWithSecondsInsideTheDay() {
        // 09:00:30 to 10:00:30.
        assertEquals(-540, clampDayMove(-600, 540.5, 600.5))
        assertEquals(839, clampDayMove(900, 540.5, 600.5))
    }

    @Test
    fun fitMoveInDayPullsAMoveBackSoTheEntryEndsAtMidnightOnTheMadridAutumnClockChange() {
        // Sunday 2026-10-25 lasts 25 hours in Madrid: the day ends at 23:00Z, not 22:00Z.
        val dayEnd = startOfDay(LocalDate.of(2026, 10, 26))
        assertEquals(Duration.ofHours(25), Duration.between(startOfDay(LocalDate.of(2026, 10, 25)), dayEnd))
        // 00:00 CEST to 04:00 CET: 240 clock minutes, but five elapsed hours.
        val start = Instant.parse("2026-10-24T22:00:00Z")
        val stop = Instant.parse("2026-10-25T03:00:00Z")
        val duration = Duration.between(start, stop)
        assertEquals(Duration.ofHours(5), duration)
        assertEquals(0.0 to 240.0, clockSpanOnDay(start, stop, LocalDate.of(2026, 10, 25), MADRID))

        // 1200 clock minutes down passes clampDayMove, but 20:00 plus five hours is 01:00 on Monday.
        val movedStart = addClockMinutes(start, 1200, MADRID)
        assertEquals(Instant.parse("2026-10-25T19:00:00Z"), movedStart)
        assertTrue(movedStart + duration > dayEnd)
        val fitted = fitMoveInDay(movedStart, duration, stop, dayEnd)
        assertEquals(Instant.parse("2026-10-25T18:00:00Z"), fitted)
        assertEquals(dayEnd, fitted + duration)
        // The pulled-back start is still later than where the entry came from.
        assertTrue(fitted > start)
    }

    @Test
    fun fitMoveInDayLeavesAMoveAloneWhenTheEntryStillEndsOnTheShownDay() {
        val dayEnd = startOfDay(LocalDate.of(2026, 10, 26))
        val start = Instant.parse("2026-10-24T22:00:00Z")
        val stop = Instant.parse("2026-10-25T03:00:00Z")
        val duration = Duration.between(start, stop)
        // 600 clock minutes down is 10:00 to 15:00 CET.
        val tenOClock = addClockMinutes(start, 600, MADRID)
        assertEquals(tenOClock, fitMoveInDay(tenOClock, duration, stop, dayEnd))
        // A move that ends exactly at midnight already fits.
        val flush = dayEnd - duration
        assertEquals(flush, fitMoveInDay(flush, duration, stop, dayEnd))
        // Moving earlier never needs a correction.
        val earlier = addClockMinutes(start, -60, MADRID)
        assertEquals(earlier, fitMoveInDay(earlier, duration, stop, dayEnd))
    }

    @Test
    fun fitMoveInDayLeavesAnEntryThatAlreadyRanPastMidnightAlone() {
        // 23:00 CEST on Saturday the 24th to 04:00 CET on Sunday the 25th, shown on Saturday.
        val dayEnd = startOfDay(LocalDate.of(2026, 10, 25))
        val start = Instant.parse("2026-10-24T21:00:00Z")
        val stop = Instant.parse("2026-10-25T03:00:00Z")
        val duration = Duration.between(start, stop)
        assertEquals(1380.0 to 1440.0, clockSpanOnDay(start, stop, LocalDate.of(2026, 10, 24), MADRID))
        assertTrue(stop > dayEnd)
        // Saturday only lets it move earlier; it still ends on Sunday and must not be pulled back.
        val earlier = addClockMinutes(start, -60, MADRID)
        assertTrue(earlier + duration > dayEnd)
        assertEquals(earlier, fitMoveInDay(earlier, duration, stop, dayEnd))
    }

    @Test
    fun snapToStepRoundsToWholeStepsOnTheDay() {
        assertEquals(540, snapToStep(547.4, 15))
        assertEquals(555, snapToStep(547.5, 15))
        assertEquals(0, snapToStep(-20.0, 15))
        assertEquals(1440, snapToStep(1500.0, 15))
        assertEquals(552, snapToStep(560.0, 24))
    }

    /** A create keeps at least a step, stays on the day and ends by 23:59, as the web app's create drag. */
    @Test
    fun aCreateDragMakesAnEntryOfAtLeastAStepOnTheDay() {
        val time = AccountTime(MADRID, DayOfWeek.MONDAY, 5.0)
        assertEquals(range("2026-06-17T07:00:00Z", "2026-06-17T08:30:00Z"), createdRange(JUNE_17, 540, 630, 15, time))
        // Dragged upwards.
        assertEquals(range("2026-06-17T07:00:00Z", "2026-06-17T08:30:00Z"), createdRange(JUNE_17, 630, 540, 15, time))
        // A drag down to midnight ends at 23:59, and one that starts there keeps a step on the day.
        assertEquals(range("2026-06-17T20:00:00Z", "2026-06-17T21:59:00Z"), createdRange(JUNE_17, 1320, 1440, 15, time))
        assertEquals(range("2026-06-17T21:45:00Z", "2026-06-17T21:59:00Z"), createdRange(JUNE_17, 1440, 1425, 15, time))
        assertNull(createdRange(JUNE_17, 600, 600, 15, time))
    }

    @Test
    fun aMoveKeepsTheDurationAndLandsTheStartOnTheGrid() {
        val time = AccountTime(MADRID, DayOfWeek.MONDAY, 5.0)
        // 09:00 to 10:00, an hour later.
        assertEquals(
            range("2026-06-17T08:00:00Z", "2026-06-17T09:00:00Z"),
            movedRange(Instant.parse("2026-06-17T07:00:00Z"), Instant.parse("2026-06-17T08:00:00Z"), 60, JUNE_17, dayMode = false, time),
        )
        // An imported 09:07:30 to 09:37:30, a step earlier: the start lands on 08:55, the duration stays.
        assertEquals(
            range("2026-06-17T06:55:00Z", "2026-06-17T07:25:00Z"),
            movedRange(Instant.parse("2026-06-17T07:07:30Z"), Instant.parse("2026-06-17T07:37:30Z"), -15, JUNE_17, dayMode = false, time),
        )
    }

    /** In the day view a move on the 25-hour autumn day still ends on it, as fitMoveInDay says. */
    @Test
    fun aDayModeMoveEndsByMidnightOnTheAutumnClockChange() {
        val time = AccountTime(MADRID, DayOfWeek.MONDAY, 5.0)
        val start = Instant.parse("2026-10-24T22:00:00Z")
        val stop = Instant.parse("2026-10-25T03:00:00Z")
        val day = LocalDate.of(2026, 10, 25)
        assertEquals(range("2026-10-25T18:00:00Z", "2026-10-25T23:00:00Z"), movedRange(start, stop, 1200, day, dayMode = true, time))
        // The week view lets it cross midnight.
        assertEquals(range("2026-10-25T19:00:00Z", "2026-10-26T00:00:00Z"), movedRange(start, stop, 1200, day, dayMode = false, time))
    }

    @Test
    fun aResizeMovesOneEndAndKeepsAStep() {
        val time = AccountTime(MADRID, DayOfWeek.MONDAY, 5.0)
        val start = Instant.parse("2026-06-17T07:00:00Z")
        val stop = Instant.parse("2026-06-17T08:00:00Z")
        assertEquals(range("2026-06-17T07:00:00Z", "2026-06-17T08:30:00Z"), endResizedRange(start, stop, 30, 15, time))
        assertEquals(range("2026-06-17T07:00:00Z", "2026-06-17T07:15:00Z"), endResizedRange(start, stop, -120, 15, time))
        assertEquals(range("2026-06-17T06:15:00Z", "2026-06-17T08:00:00Z"), startResizedRange(start, stop, -45, 15, time))
        assertEquals(range("2026-06-17T07:45:00Z", "2026-06-17T08:00:00Z"), startResizedRange(start, stop, 120, 15, time))
    }

    /** The server moves an imported start to the grid, so an end resize sends that start too, as the web app does. */
    @Test
    fun anEndResizeSendsAnOffGridStartOnTheGrid() {
        val time = AccountTime(MADRID, DayOfWeek.MONDAY, 5.0)
        val start = Instant.parse("2026-06-17T07:07:30Z")
        val stop = Instant.parse("2026-06-17T07:37:30Z")
        assertEquals(range("2026-06-17T07:10:00Z", "2026-06-17T07:55:00Z"), endResizedRange(start, stop, 15, 15, time))
    }

    private fun clockMinutes(instant: Instant): Double =
        instant.atZone(MADRID).let { it.hour * 60 + it.minute + it.second / 60.0 }

    private fun startOfDay(day: LocalDate): Instant = day.atStartOfDay(MADRID).toInstant()

    private fun range(start: String, stop: String) = TimeRange(Instant.parse(start), Instant.parse(stop))

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val JUNE_17: LocalDate = LocalDate.of(2026, 6, 17)
    }
}
