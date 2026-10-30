package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.ServerTimerStates
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PomodoroWidgetActionsTest {
    private val at = Instant.parse("2026-10-29T08:00:00Z")
    private val entry = PomodoroWidgetEntry(21, EntryFields("Focus", 7, 9, listOf("one,two", "three"), true), "Tokkl", "#0086d1", "Widget")
    private val idle = PomodoroWidgetState.Idle("account-a", PomodoroDefaults(40, 7, 20, 3), entry)
    private val active = PomodoroWidgetState.Active("account-a", 17, ServerTimerStates.session(42, at), at.minusSeconds(120), at.plusSeconds(1380), false, entry)
    private val next = PomodoroWidgetTap.Phase("account-a", 17, at, false)
    private val start = PomodoroWidgetTap.Start(idle.account, idle.entry, idle.defaults)
    private val server = RecordingActions()

    @Test fun correctedEndIsAFutureCountdownBaseWithoutClamping() {
        val clock = pomodoroWidgetClock(active, at, 200_000)
        assertEquals(1_580_000L, clock.base)
        assertTrue(clock.countDown)
        assertEquals(0.08f, clock.progress, 0.0001f)
    }

    @Test fun endedPhaseCountsUpFromCorrectedEndWithAFullBar() {
        val clock = pomodoroWidgetClock(active.copy(ended = true), active.end.plusSeconds(65), 5_000_000)
        assertEquals(4_935_000L, clock.base)
        assertFalse(clock.countDown)
        assertEquals(1f, clock.progress)
    }

    @Test fun progressIsBoundedWhenTheDeviceClockMovesOrPhaseHasZeroLength() {
        assertEquals(0f, pomodoroWidgetClock(active, active.start.minusSeconds(50), 0).progress)
        assertEquals(1f, pomodoroWidgetClock(active, active.end.plusSeconds(50), 0).progress)
        assertEquals(1f, pomodoroWidgetClock(active.copy(start = active.end), at, 0).progress)
    }

    @Test fun anotherAccountOrUnavailableSurfaceSendsNothing() = runBlocking {
        for (current in listOf(PomodoroWidgetState.NoAccount, PomodoroWidgetState.Unsupported, active.copy(account = "account-b"), idle.copy(account = "account-b"))) {
            assertNull(sendPomodoroWidgetAction(next, current, server))
            assertNull(sendPomodoroWidgetAction(start, current, server))
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun phaseControlsNeverActOnANewerPhaseOrAnEndedSession() = runBlocking {
        assertNull(sendPomodoroWidgetAction(next, active.copy(session = active.session.copy(phase = active.session.phase.copy(start = at.plusSeconds(100)))), server))
        assertNull(sendPomodoroWidgetAction(next, idle, server))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun nextAndEndSendTheDisplayedRevisionEvenAfterASamePhaseEdit() = runBlocking {
        assertEquals(TimerActionResult.Done, sendPomodoroWidgetAction(next, active.copy(revision = 19), server))
        assertEquals(TimerActionResult.Done, sendPomodoroWidgetAction(next.copy(endSession = true), active, server))
        assertEquals(listOf("next:17", "end:17"), server.calls)
    }

    @Test fun idleStartKeepsAllDisplayedFieldsWorkspaceAndFourDefaults() = runBlocking {
        assertEquals(TimerActionResult.Done, sendPomodoroWidgetAction(start, idle.copy(defaults = PomodoroDefaults.DEFAULT, entry = entry.copy(workspaceId = 99)), server))
        assertEquals(Triple(21L, entry.fields, SessionLengths(40, 7, 20, 3)), server.started)
        assertEquals(listOf("start"), server.calls)
    }

    @Test fun staleIdleStartRefusesWithoutReplacingTheActiveSession() = runBlocking {
        assertEquals(TimerActionResult.Refused, sendPomodoroWidgetAction(start, active, server))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun offlineAndConflictReturnOnceWithoutReadingRetryingOrAdvancingLocally() = runBlocking {
        for (result in listOf(TimerActionResult.Offline, TimerActionResult.Refused)) {
            server.result = result
            assertEquals(result, sendPomodoroWidgetAction(next, active, server))
        }
        assertEquals(listOf("next:17", "next:17"), server.calls)
        assertEquals(17L, active.revision)
        assertFalse(active.ended)
    }

    private class RecordingActions : SessionActions {
        val calls = mutableListOf<String>()
        var started: Triple<Long, EntryFields, SessionLengths>? = null
        var result: TimerActionResult = TimerActionResult.Done
        override suspend fun read(): TimerActionResult = error("widget actions must never read first")
        override suspend fun startSession(workspaceId: Long, fields: EntryFields, lengths: SessionLengths): TimerActionResult {
            calls += "start"; started = Triple(workspaceId, fields, lengths); return result
        }
        override suspend fun nextPhase(revision: Long): TimerActionResult { calls += "next:$revision"; return result }
        override suspend fun endSession(revision: Long): TimerActionResult { calls += "end:$revision"; return result }
    }
}
