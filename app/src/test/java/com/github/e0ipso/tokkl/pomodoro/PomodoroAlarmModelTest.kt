package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full-screen alarm bound to one held phase: it opens only for the phase its intent names once
 * that phase's planned end has come, closes when any device moves the session on, keeps a new
 * revision of its phase, and sends the revision it shows.
 */
class PomodoroAlarmModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val clock = MovableClock(T0.plusSeconds(1_565))
    private val held = MutableStateFlow<HeldTimerState?>(heldState(revision = 3))
    private val server = FakeServer(held)
    private val model = PomodoroAlarmModel(held, server, scope, clock)
    private val key = PomodoroPhaseKey(T0)

    @After fun closeScope() { scope.cancel() }

    @Test fun opensTheHeldPhaseItsIntentNamesWithTheServersTransitionNumberAndOvertime() {
        model.open(key)

        val ring = checkNotNull(model.state.value.ring)
        assertFalse(model.state.value.closed)
        assertEquals(key, ring.phase)
        assertEquals(PomodoroSound.WORK_TO_SHORT_REST, ring.sound)
        assertEquals(1 to 4, ring.number to ring.longRestEvery)
        assertEquals(PomodoroClock(overtime = true, seconds = 65), ring.clockAt(clock.now))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun theOvertimeIsCorrectedByTheStatesClockOffset() {
        // The server's clock read 20 s later than the phone's when the state arrived.
        held.value = heldState(revision = 3, offset = Duration.ofSeconds(20))
        model.open(key)

        assertEquals(PomodoroClock(overtime = true, seconds = 85), model.state.value.ring?.clockAt(clock.now))
    }

    @Test fun aMissingOrStaleIdentityOrAnEndNotYetReachedCannotOpen() {
        for (expected in listOf(null, PomodoroPhaseKey(T0.minusSeconds(1)), PomodoroPhaseKey(T0.plusSeconds(1_500)))) {
            model.open(expected)
            assertTrue(model.state.value.closed)
            assertNull(model.state.value.ring)
            assertNull(model.next())
            assertNull(model.end())
        }
        clock.now = T0.plusSeconds(1_499)
        model.open(key)
        assertTrue(model.state.value.closed)
        assertTrue(server.calls.isEmpty())
    }

    @Test fun withoutTheTimerApiOrASessionNothingOpens() {
        // The application hands the model no state while the server lacks the timer API.
        held.value = null
        model.open(key)
        assertTrue(model.state.value.closed)

        held.value = heldState(revision = 4, pomodoro = null)
        model.open(key)
        assertTrue(model.state.value.closed)
    }

    @Test fun itOpensOnceTheStoredStateHasLoaded() {
        val loading = MutableStateFlow<HeldTimerState?>(null)
        val loaded = CompletableDeferred<Unit>()
        val late = PomodoroAlarmModel(
            flow {
                loaded.await()
                emit(heldState(revision = 3))
            },
            FakeServer(loading), scope, clock,
        )
        late.open(key)
        assertNull(late.state.value.ring)
        assertFalse(late.state.value.closed)

        loaded.complete(Unit)

        assertEquals(key, late.state.value.ring?.phase)
    }

    @Test fun aConfirmOnAnotherDeviceOrAnEndedSessionClosesIt() {
        model.open(key)
        held.value = heldState(revision = 4, pomodoro = rest())
        assertTrue(model.state.value.closed)
        assertNull(model.state.value.ring)

        held.value = heldState(revision = 5)
        model.open(key)
        assertFalse(model.state.value.closed)
        held.value = heldState(revision = 6, pomodoro = null)
        assertTrue(model.state.value.closed)
    }

    @Test fun aNewRevisionOfItsPhaseKeepsItOpenWithThatRevision() {
        model.open(key)
        held.value = heldState(revision = 4, description = "Renamed on the web")

        assertFalse(model.state.value.closed)
        assertEquals(4L, model.state.value.ring?.revision)
        model.next()!!
        assertEquals(listOf("next 4"), server.calls)
    }

    @Test fun nextSendsTheShownRevisionOnceAndTheServersAnswerClosesIt() = runBlocking {
        model.open(key)
        server.gate = CompletableDeferred()
        val action = model.next()!!
        assertTrue(model.state.value.busy)
        assertNull(model.next())
        assertNull(model.end())

        server.gate.complete(Unit)
        action.join()

        assertEquals(listOf("next 3"), server.calls)
        assertTrue(model.state.value.closed)
        assertNull(model.state.value.problem)
    }

    @Test fun endSendsTheShownRevisionAndClosesWhenTheSessionIsGone() = runBlocking {
        model.open(key)
        model.end()!!.join()

        assertEquals(listOf("end 3"), server.calls)
        assertNull(held.value)
        assertTrue(model.state.value.closed)
    }

    @Test fun offlineLeavesTheAlarmOpenWithTheStandardMessageAndTheUserCanRetry() = runBlocking {
        model.open(key)
        server.answer = TimerActionResult.Offline
        model.next()!!.join()

        assertEquals(TimerActionResult.Offline, model.state.value.problem)
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.closed)
        assertEquals(key, model.state.value.ring?.phase)
        assertEquals(3L, held.value?.state?.revision)

        server.answer = null
        model.next()!!.join()
        assertEquals(listOf("next 3", "next 3"), server.calls)
        assertTrue(model.state.value.closed)
    }

    @Test fun aRefusalWithinTheSamePhaseStaysOpenAtTheServersRevision() = runBlocking {
        model.open(key)
        server.answer = TimerActionResult.Refused
        server.gate = CompletableDeferred()
        val action = model.end()!!
        // Another device renamed the open pomodoro: the 409's body, revision 4 of the same phase,
        // reaches the held state before the refusal returns.
        held.value = heldState(revision = 4, description = "Renamed")
        server.gate.complete(Unit)
        action.join()

        assertEquals(listOf("end 3"), server.calls)
        assertEquals(TimerActionResult.Refused, model.state.value.problem)
        assertFalse(model.state.value.closed)
        assertEquals(4L, model.state.value.ring?.revision)
    }

    @Test fun duplicateAndStaleIntentsLeaveTheAlarmOnScreen() {
        model.open(key)
        model.open(key)
        model.open(PomodoroPhaseKey(T0.minusSeconds(60)))
        model.open(null)

        assertEquals(key, model.state.value.ring?.phase)
        assertFalse(model.state.value.closed)
        assertTrue(server.calls.isEmpty())
    }

    @Test fun recreationSendsNothing() {
        model.open(key)
        repeat(3) { model.open(key) }
        held.value = held.value

        assertTrue(server.calls.isEmpty())
        assertEquals(key, model.state.value.ring?.phase)
    }
}
