package com.github.e0ipso.tokkl.calendar

import androidx.compose.ui.geometry.Offset
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Where a touch lands in the calendar's grid, and what a swipe and the week strip's scroll do. */
class CalendarGeometryTest {

    @Test
    fun aSwipeMovesADayOnlyWhenItGoesFarAndMostlySideways() {
        assertEquals(1L, swipeSteps(dx = -60f, dy = 10f, minDistance = 50f))
        assertEquals(-1L, swipeSteps(dx = 60f, dy = -10f, minDistance = 50f))
        // Too short.
        assertNull(swipeSteps(dx = -49f, dy = 0f, minDistance = 50f))
        // As far down as sideways is a scroll of the grid.
        assertNull(swipeSteps(dx = -60f, dy = 60f, minDistance = 50f))
        // Exactly 1.5 times further sideways still counts, as in the web app.
        assertEquals(1L, swipeSteps(dx = -60f, dy = 40f, minDistance = 50f))
    }

    @Test
    fun theStripScrollsOnlyAsFarAsItTakesToShowTheChosenDayWhole() {
        // Days 50 wide after a 4 inset, in a viewport of 200 scrolled to 100.
        assertNull(
            "day 3 shows whole",
            stripScrollToShow(index = 3, dayWidth = 50, inset = 4, scrolled = 100, viewport = 200),
        )
        assertEquals(
            "day 1 starts before the viewport",
            50,
            stripScrollToShow(index = 1, dayWidth = 50, inset = 4, scrolled = 100, viewport = 200),
        )
        assertEquals(
            "day 5 ends after it, with the inset",
            108,
            stripScrollToShow(index = 5, dayWidth = 50, inset = 4, scrolled = 100, viewport = 200),
        )
    }

    @Test
    fun aPressOnEmptySpaceCreatesAnEntryAtTheNearestStep() {
        val target = geometry.targetAt(Offset(50f, 128f), WIDTH, emptyList(), DAY, ZoneOffset.UTC, step = 15)
        assertEquals(DragTarget.Empty(minute = 135), target)
    }

    @Test
    fun aPressOnABlockMovesItAndOnItsOwnEdgesResizesThatEnd() {
        val block = block(9 * 60, 11 * 60)
        val blocks = listOf(block)
        fun kindAt(y: Float) = (
            geometry.targetAt(
                Offset(50f, y),
                WIDTH,
                blocks,
                DAY,
                ZoneOffset.UTC,
                step = 15,
            ) as DragTarget.OnBlock
            ).kind
        assertEquals(DragKind.RESIZE_START, kindAt(9 * 60 + 10f))
        assertEquals(DragKind.MOVE, kindAt(10 * 60f))
        assertEquals(DragKind.RESIZE_END, kindAt(11 * 60 - 10f))
    }

    @Test
    fun aBlockCutOffAtMidnightHasNoEdgeToResizeThere() {
        // 22:00 the day before to 01:00: the block starts at the top of this day's column.
        val entry = entry(
            DAY.minusDays(1).atTime(22, 0).toInstant(ZoneOffset.UTC),
            DAY.atTime(1, 0).toInstant(ZoneOffset.UTC),
        )
        val blocks = listOf(CalendarBlock(entry, DAY, top = 0.0, bottom = 60.0, lane = 0, lanes = 1))
        val target = geometry.targetAt(
            Offset(50f, 2f),
            WIDTH,
            blocks,
            DAY,
            ZoneOffset.UTC,
            step = 15,
        ) as DragTarget.OnBlock
        assertEquals(DragKind.MOVE, target.kind)
    }

    @Test
    fun aPressInAnotherLaneOrInTheGapBetweenLanesMissesTheBlock() {
        val left = block(9 * 60, 10 * 60, lane = 0, lanes = 2)
        assertEquals(
            left,
            (
                geometry.targetAt(
                    Offset(25f, 570f),
                    WIDTH,
                    listOf(left),
                    DAY,
                    ZoneOffset.UTC,
                    15,
                ) as DragTarget.OnBlock
                ).block,
        )
        assertEquals(
            DragTarget.Empty(570),
            geometry.targetAt(Offset(75f, 570f), WIDTH, listOf(left), DAY, ZoneOffset.UTC, 15),
        )
        // The lane is 50 wide with a gap of 2 at each side, so 49 is in the gap.
        assertEquals(
            DragTarget.Empty(570),
            geometry.targetAt(Offset(49f, 570f), WIDTH, listOf(left), DAY, ZoneOffset.UTC, 15),
        )
    }

    @Test
    fun theRunningEntrysBlockTakesNoDrag() {
        val running = CalendarBlock(
            entry(DAY.atTime(9, 0).toInstant(ZoneOffset.UTC), stop = null),
            DAY,
            540.0,
            600.0,
            0,
            1,
        )
        assertNull(geometry.targetAt(Offset(50f, 570f), WIDTH, listOf(running), DAY, ZoneOffset.UTC, 15))
    }

    @Test
    fun aShortBlockIsAtLeastItsMinimumHeightAndItsEdgesTakeAThirdEach() {
        // A 1-minute block is drawn 12 tall: 0 to 12 below 09:00. Each edge takes 4 pixels.
        val short = block(9 * 60, 9 * 60 + 1)
        fun kindAt(y: Float) = (
            geometry.targetAt(
                Offset(50f, y),
                WIDTH,
                listOf(short),
                DAY,
                ZoneOffset.UTC,
                15,
            ) as DragTarget.OnBlock
            ).kind
        assertEquals(DragKind.RESIZE_START, kindAt(540f + 3f))
        assertEquals(DragKind.MOVE, kindAt(540f + 6f))
        assertEquals(DragKind.RESIZE_END, kindAt(540f + 9f))
    }

    private fun block(top: Int, bottom: Int, lane: Int = 0, lanes: Int = 1): CalendarBlock {
        val start = DAY.atStartOfDay().plusMinutes(top.toLong()).toInstant(ZoneOffset.UTC)
        val stop = DAY.atStartOfDay().plusMinutes(bottom.toLong()).toInstant(ZoneOffset.UTC)
        return CalendarBlock(entry(start, stop), DAY, top.toDouble(), bottom.toDouble(), lane, lanes)
    }

    private fun entry(start: Instant, stop: Instant?) = CalendarEntry(
        id = EntryId(1),
        description = "Work",
        project = null,
        projectColor = null,
        task = null,
        timing = EntryTiming.of(start, stop, allDay = false, day = null),
        unsent = false,
    )

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 3, 10)

        /** A column 100 pixels wide, a pixel a minute, 2 pixels of gap, blocks at least 12 tall and edges 15 deep. */
        const val WIDTH = 100f
        val geometry = BlockGeometry(hour = 60f, gap = 2f, minHeight = 12f, edge = 15f)
    }
}
