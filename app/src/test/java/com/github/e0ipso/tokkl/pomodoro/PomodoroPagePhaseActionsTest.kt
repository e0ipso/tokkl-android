package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Next phase, End and the clock the page draws, against a server that refuses a revision it has moved past. */
class PomodoroPagePhaseActionsTest {
    private val fixtures = mutableListOf<PageFixture>()
    private fun page(revision: Long = 4) =
        PageFixture(heldState(revision), cachedEntry()).also { fixtures += it }

    @After
    fun stop() = fixtures.forEach { it.close() }

    @Test
    fun nextPhaseSendsTheDisplayedRevisionAndTheServersAnswerIsTheNewPhase() = runBlocking {
        val f = page(revision = 4)
        f.page.next()!!.join()
        assertEquals(listOf("next 4"), f.server.calls)
        assertEquals(com.github.e0ipso.tokkl.timerstate.PhaseKind.SHORT_REST, f.page.state.value.pomodoro!!.phase.kind)
        assertEquals(5L, f.page.state.value.shown!!.revision)
        assertNull(f.page.state.value.problem)
        assertFalse(f.page.state.value.busy)
    }

    @Test
    fun endSendsTheDisplayedRevisionAndTheFormComesBack() = runBlocking {
        val f = page(revision = 4)
        f.page.end()!!.join()
        assertEquals(listOf("end 4"), f.server.calls)
        assertNull(f.page.state.value.pomodoro)
        assertNull(f.page.state.value.problem)
    }

    @Test
    fun aTapDrawnBeforeAnotherDeviceAdvancedTheTimerCannotAdvanceItAgain() = runBlocking {
        val f = page(revision = 4)
        val drawn = f.page.state.value.shown!!
        // The user's other device moves on to the rest, then the old page's buttons are tapped.
        f.held.value = heldState(revision = 5, pomodoro = rest())
        f.page.next(drawn)!!.join()
        f.page.end(drawn)!!.join()
        assertEquals("the old revision goes out and the server refuses it", listOf("next 4", "end 4"), f.server.calls)
        assertEquals(5L, f.page.state.value.shown!!.revision)
        assertEquals(com.github.e0ipso.tokkl.timerstate.PhaseKind.SHORT_REST, f.page.state.value.pomodoro!!.phase.kind)
        assertEquals(PomodoroProblem(PomodoroAction.END, TimerActionResult.Refused), f.page.state.value.problem)
    }

    @Test
    fun aRefusalAndNoConnectionLeaveTheShownPhaseAsItWasAndSayWhy() = runBlocking {
        for (result in listOf(TimerActionResult.Refused, TimerActionResult.Offline)) {
            val f = page(revision = 4)
            f.server.answer = result
            val before = f.page.state.value.held
            f.page.next()!!.join()
            assertEquals(before, f.page.state.value.held)
            assertEquals(PomodoroProblem(PomodoroAction.NEXT, result), f.page.state.value.problem)
            assertFalse(f.page.state.value.busy)
            // The next tap clears the message.
            f.server.answer = null
            f.page.end()!!.join()
            assertNull(f.page.state.value.problem)
        }
    }

    @Test
    fun aSecondTapWhileTheFirstWaitsForTheServerSendsNothing() = runBlocking {
        val f = page()
        f.server.gate = CompletableDeferred()
        val first = f.page.next()!!
        assertTrue(f.page.state.value.busy)
        assertNull(f.page.next())
        assertNull(f.page.end())
        f.server.gate.complete(Unit)
        first.join()
        assertEquals(listOf("next 4"), f.server.calls)
    }

    @Test
    fun nothingGoesOutWithoutASessionOnScreen() {
        val f = PageFixture().also { fixtures += it }
        assertNull(f.page.next())
        assertNull(f.page.end())
        assertTrue(f.server.calls.isEmpty())
    }

    @Test
    fun anEditSentJustBeforeNextPhaseReachesTheServerFirstAndNextSendsTheRevisionItProduced() = runBlocking {
        val f = page(revision = 4)
        // The edit moves the server's revision to 5, which the read brings to the phone.
        f.server.onRead = { f.held.value = heldState(revision = 5, description = "Typed") }
        f.page.editFields(EntryFields("Typed"), ENTRY)!!.join()
        f.page.next()!!.join()
        assertEquals(listOf("send"), f.queueSends)
        assertEquals("the queue is sent, then the state is read, then Next phase goes out", listOf("read", "next 5"), f.server.calls)
        assertNull(f.page.state.value.problem)
    }

    @Test
    fun anEditSentJustBeforeEndGoesFirstToo() = runBlocking {
        val f = page(revision = 4)
        f.server.onRead = { f.held.value = heldState(revision = 5, description = "Typed") }
        f.page.editFields(EntryFields("Typed"), ENTRY)!!.join()
        f.page.end()!!.join()
        assertEquals(listOf("read", "end 5"), f.server.calls)
    }

    @Test
    fun ifAnotherDeviceMovedThePhaseWhileTheEditWasSentNothingAdvances() = runBlocking {
        val f = page(revision = 4)
        f.server.onRead = { f.held.value = heldState(revision = 6, pomodoro = rest()) }
        f.page.editFields(EntryFields("Typed"), ENTRY)!!.join()
        f.page.next()!!.join()
        assertEquals("only the read went out", listOf("read"), f.server.calls)
        assertEquals(PomodoroProblem(PomodoroAction.NEXT, TimerActionResult.Refused), f.page.state.value.problem)
    }

    @Test
    fun withoutAnEditTheQueueIsNotSentAndTheStateIsNotRead() = runBlocking {
        val f = page(revision = 4)
        f.page.next()!!.join()
        assertTrue(f.queueSends.isEmpty())
        assertEquals(listOf("next 4"), f.server.calls)
    }

    @Test
    fun theClockCountsDownOnTheServersTimeAndThenCountsOvertime() {
        val f = PageFixture(heldState(4, offset = Duration.ofSeconds(100)), cachedEntry()).also { fixtures += it }
        val held = f.page.state.value.held!!
        // 1500 s phase, the server's clock 100 s ahead of the phone: 1400 s left at the phone's T0.
        assertEquals(PomodoroClock(false, 1_400), held.phaseClockAt(T0))
        assertEquals(PomodoroClock(false, 0), held.phaseClockAt(T0.plusSeconds(1_400)))
        assertEquals(PomodoroClock(true, 1), held.phaseClockAt(T0.plusSeconds(1_401)))
        assertEquals(PomodoroClock(true, 13), held.phaseClockAt(T0.plusSeconds(1_413)))
        assertNull(heldState(1, pomodoro = null).phaseClockAt(T0))
    }

    @Test
    fun theShownValuesCarryTheServersNumberAndCycleLength() {
        val f = PageFixture(heldState(4, pomodoro = work(number = 3)), cachedEntry()).also { fixtures += it }
        val session = f.page.state.value.pomodoro!!
        assertEquals(3, session.number)
        assertEquals(4, session.longRestEvery)
        assertEquals(com.github.e0ipso.tokkl.timerstate.PhaseKind.SHORT_REST, session.upcomingPhase)
        assertEquals(PomodoroShown(4, T0, ENTRY), f.page.state.value.shown)
    }
}
