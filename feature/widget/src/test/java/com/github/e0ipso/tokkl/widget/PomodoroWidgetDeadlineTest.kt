package com.github.e0ipso.tokkl.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The Pomodoro widget's redraw at a phase's end, in virtual time from the epoch. */
class PomodoroWidgetDeadlineTest {
    @Test fun aFractionalMillisecondCannotRedrawBeforeThePhaseEnds() = runTest {
        val end = Instant.ofEpochSecond(0, 1)
        val draws = follow(MutableStateFlow(end))
        runCurrent()
        assertEquals("the phase draws at once", 1, draws.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("round up, so the redraw comes a whole millisecond later", 2, draws.size)
        assertFalse("the ended state is true at the redraw", draws.last().isBefore(end))
    }

    @Test fun phaseEndRedrawDoesNotDependOnAnAlarmOrAnotherServerEvent() = runTest {
        val draws = follow(MutableStateFlow(Instant.ofEpochMilli(1_000)))
        advanceTimeBy(999)
        runCurrent()
        assertEquals("nothing redraws before the end", 1, draws.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("the end redraws", listOf(Instant.EPOCH, Instant.ofEpochMilli(1_000)), draws)
        advanceUntilIdle()
        assertEquals("one phase has one deadline, and nothing redraws again", 2, draws.size)
    }

    @Test fun aNewPhaseCancelsThePreviousDeadline() = runTest {
        val ends = MutableStateFlow<Instant?>(Instant.ofEpochMilli(1_000))
        val draws = follow(ends)
        advanceTimeBy(500)
        ends.value = Instant.ofEpochMilli(3_000)
        runCurrent()
        assertEquals("the new phase draws at once", 2, draws.size)
        advanceTimeBy(500)
        runCurrent()
        assertEquals("the old phase cannot redraw", 2, draws.size)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("the new phase redraws at its end", Instant.ofEpochMilli(3_000), draws.last())
        assertEquals(3, draws.size)
    }

    @Test fun sessionEndCancelsTheDeadlineAndPastEndsNeedNoFutureWork() = runTest {
        val ends = MutableStateFlow<Instant?>(Instant.ofEpochMilli(1_000))
        val draws = follow(ends)
        runCurrent()
        ends.value = null
        runCurrent()
        assertEquals("the end of the session draws", 2, draws.size)
        advanceUntilIdle()
        assertEquals("idle has no deadline", 2, draws.size)
        advanceTimeBy(10_000)
        ends.value = Instant.EPOCH
        runCurrent()
        assertEquals("an overdue restored phase draws at once", 3, draws.size)
        val now = testScheduler.currentTime
        advanceUntilIdle()
        assertEquals("and needs no future wait", now, testScheduler.currentTime)
        assertEquals(3, draws.size)
    }

    /** Collects the redraws of [ends] until the test ends, each as the virtual time it came at. */
    private fun TestScope.follow(ends: MutableStateFlow<out Instant?>): List<Instant> {
        val clock = VirtualClock(this)
        val draws = mutableListOf<Instant>()
        backgroundScope.launch { pomodoroWidgetDeadlineChanges(ends, clock).collect { draws += clock.instant() } }
        return draws
    }

    /** The test's virtual time, as milliseconds since the epoch. */
    private class VirtualClock(private val scope: TestScope) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(scope.testScheduler.currentTime)
    }
}
