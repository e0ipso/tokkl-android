package com.github.e0ipso.tokkl.widget

import org.junit.Assert.*
import org.junit.Test

class TimerWidgetLayoutTest {
    @Test fun sizesRequireBothDimensionsAndKeepTemplatesOnlyOnLargeCards() {
        assertEquals(TimerWidgetSize.STRIP, timerWidgetSize(110f, 40f))
        assertEquals(TimerWidgetSize.STRIP, timerWidgetSize(196f, 100f))
        assertEquals(TimerWidgetSize.MEDIUM, timerWidgetSize(298f, 222f))
        assertEquals(TimerWidgetSize.LARGE, timerWidgetSize(375f, 338f))
        assertEquals(TimerWidgetSize.STRIP, timerWidgetSize(375f, 100f))
        assertEquals(TimerWidgetSize.MEDIUM, timerWidgetSize(298f, 500f))
    }

    @Test fun largeTextDropsProjectRowsBeforeTemplates() {
        val regular = timerWidgetProjectRows(338f, TimerWidgetSize.LARGE, 1f, active = true, templates = true)
        val enlarged = timerWidgetProjectRows(338f, TimerWidgetSize.LARGE, 2f, active = true, templates = true)
        assertTrue(regular > 0)
        assertEquals(0, enlarged)
        assertTrue(timerWidgetProjectRows(338f, TimerWidgetSize.LARGE, 1f, true, false) > regular)
        assertEquals(0, timerWidgetProjectRows(40f, TimerWidgetSize.STRIP, 2f, true, false))
    }

    @Test fun moreThanFiveProjectsFoldIntoOtherWithoutLosingTimeOrRunningMarker() {
        val projects = (1L..7).map { WidgetDayProject(it, "Project $it", "#0086D1", it * 60, "$it min", it == 7L) }
        val segments = timerWidgetSegments(projects)
        assertEquals(5, segments.size)
        assertEquals(projects.take(4), segments.take(4))
        val other = segments.last()
        assertNull(other.projectId)
        assertNull(other.project)
        assertEquals(18 * 60L, other.stoppedSeconds)
        assertTrue(other.running)
        assertEquals(projects.sumOf { it.stoppedSeconds }, segments.sumOf { it.stoppedSeconds })
        assertEquals(projects.take(5), timerWidgetSegments(projects.take(5)))
        assertTrue(timerWidgetSegments(emptyList()).isEmpty())
    }
}
