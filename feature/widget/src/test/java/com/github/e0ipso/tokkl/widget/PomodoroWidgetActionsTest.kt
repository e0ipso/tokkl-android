package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.ServerTimerStates
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PomodoroWidgetActionsTest {
    private val at = Instant.parse("2026-10-29T08:00:00Z")
    private val entry = PomodoroWidgetEntry(
        WorkspaceId(21),
        EntryFields("Focus", ProjectId(7), TaskId(9), listOf("one,two", "three"), true),
        "Tokkl",
        "#0086d1",
        "Widget",
    )
    private val idle = PomodoroWidgetState.Idle("account-a", PomodoroDefaults(40, 7, 20, 3), entry)
    private val active = PomodoroWidgetState.Active(
        "account-a",
        17,
        checkNotNull(ServerTimerStates.held(17, at, pomodoro = ServerTimerStates.session(EntryId(42), at)).pomodoro),
        at.minusSeconds(120),
        at.plusSeconds(1380),
        false,
        entry,
    )
    private val next = PomodoroWidgetTap.Phase("account-a", 17, at, false)
    private val start = PomodoroWidgetTap.Start(idle.account, idle.entry, idle.defaults)
    private val held = MutableStateFlow<HeldTimerState?>(ServerTimerStates.held(5, at))
    private val server = RecordingActions(held)
    private val queueSends = mutableListOf<String>()
    private var queued = false
    private val revision = PomodoroWidgetStartRevision(held, { queued }, { queueSends += "send" })

    private suspend fun send(tap: PomodoroWidgetTap, current: PomodoroWidgetState) =
        sendPomodoroWidgetAction(tap, current, server, revision)

    @Test fun theWorkSendsTheTapTheWidgetDrew() {
        val started = start.toData().pomodoroWidgetTap() as PomodoroWidgetTap.Start
        assertEquals(start.account, started.account)
        assertEquals(entry.workspaceId, started.entry.workspaceId)
        assertEquals(entry.fields, started.entry.fields)
        assertEquals(idle.defaults, started.defaults)

        val end = next.copy(endSession = true)
        assertEquals(next, next.toData().pomodoroWidgetTap())
        assertEquals(end, end.toData().pomodoroWidgetTap())
    }

    @Test fun correctedEndIsAFutureCountdownBaseWithoutClamping() {
        val clock = pomodoroWidgetClock(active, at, 200_000)
        assertEquals(1_580_000L, clock.base)
        assertTrue(clock.countDown)
    }

    @Test fun endedPhaseCountsUpFromCorrectedEnd() {
        val clock = pomodoroWidgetClock(active.copy(ended = true), active.end.plusSeconds(65), 5_000_000)
        assertEquals(4_935_000L, clock.base)
        assertFalse(clock.countDown)
    }

    @Test fun anotherAccountOrUnavailableSurfaceSendsNothing() = runTest {
        for (current in listOf(
            PomodoroWidgetState.NoAccount,
            PomodoroWidgetState.Unsupported,
            active.copy(account = "account-b"),
            idle.copy(account = "account-b"),
        )) {
            assertNull(send(next, current))
            assertNull(send(start, current))
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun phaseControlsNeverActOnANewerPhaseOrAnEndedSession() = runTest {
        assertNull(
            send(
                next,
                active.copy(
                    session = active.session.copy(phase = active.session.phase.copy(start = at.plusSeconds(100))),
                ),
            ),
        )
        assertNull(send(next, idle))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun nextAndEndSendTheDisplayedRevisionEvenAfterASamePhaseEdit() = runTest {
        assertEquals(TimerActionResult.Done, send(next, active.copy(revision = 19)))
        assertEquals(TimerActionResult.Done, send(next.copy(endSession = true), active))
        assertEquals(listOf("next:17", "end:17"), server.calls)
    }

    @Test fun idleStartKeepsAllDisplayedFieldsWorkspaceAndFourDefaultsAndSendsTheHeldRevision() = runTest {
        val otherWorkspace = entry.copy(workspaceId = WorkspaceId(99))
        val drawnLater = idle.copy(defaults = PomodoroDefaults.DEFAULT, entry = otherWorkspace)
        assertEquals(TimerActionResult.Done, send(start, drawnLater))
        assertEquals(Triple(WorkspaceId(21), entry.fields, SessionLengths(40, 7, 20, 3)), server.started)
        assertEquals(listOf("start:5"), server.calls)
        assertTrue(queueSends.isEmpty())
    }

    @Test fun staleIdleStartRefusesWithoutReplacingTheActiveSession() = runTest {
        assertEquals(TimerActionResult.Refused, send(start, active))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun startReadsTheTimerFirstWhenNoStateIsHeldOrTheQueueHoldsChanges() = runTest {
        held.value = null
        server.onRead = { held.value = ServerTimerStates.held(8, at) }
        assertEquals(TimerActionResult.Done, send(start, idle))
        assertEquals(listOf("read", "start:8"), server.calls)
        assertTrue(queueSends.isEmpty())

        server.calls.clear()
        queued = true
        server.onRead = { held.value = ServerTimerStates.held(9, at) }
        assertEquals(TimerActionResult.Done, send(start, idle))
        assertEquals(listOf("send"), queueSends)
        assertEquals(listOf("read", "start:9"), server.calls)
    }

    @Test fun aSessionThatShowsByTheReadRefusesStart() = runTest {
        held.value = null
        server.onRead = {
            held.value = ServerTimerStates.held(8, at, pomodoro = ServerTimerStates.session(EntryId(42), at))
        }
        assertEquals(TimerActionResult.Refused, send(start, idle))
        assertEquals(listOf("read"), server.calls)
    }

    @Test fun offlineAndConflictReturnOnceWithoutReadingRetryingOrAdvancingLocally() = runTest {
        for (result in listOf(TimerActionResult.Offline, TimerActionResult.Refused)) {
            server.result = result
            assertEquals(result, send(next, active))
        }
        assertEquals(listOf("next:17", "next:17"), server.calls)
        assertEquals(17L, active.revision)
        assertFalse(active.ended)
    }

    private class RecordingActions(private val held: MutableStateFlow<HeldTimerState?>) : SessionActions {
        val calls = mutableListOf<String>()
        var started: Triple<WorkspaceId, EntryFields, SessionLengths>? = null
        var result: TimerActionResult = TimerActionResult.Done

        /** What a read brings to the phone. */
        var onRead: () -> Unit = { error("nothing here reads") }

        override suspend fun read(): TimerActionResult {
            calls += "read"
            onRead()
            return TimerActionResult.Done
        }
        override suspend fun startSession(
            workspaceId: WorkspaceId,
            fields: EntryFields,
            lengths: SessionLengths,
            revision: Long,
        ): TimerActionResult {
            calls += "start:$revision"
            started = Triple(workspaceId, fields, lengths)
            return result
        }
        override suspend fun nextPhase(revision: Long): TimerActionResult {
            calls += "next:$revision"
            return result
        }
        override suspend fun endSession(revision: Long): TimerActionResult {
            calls += "end:$revision"
            return result
        }
    }
}
