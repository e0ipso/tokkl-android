package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerPause
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import com.github.e0ipso.tokkl.timerstate.TimerPomodoroPhase
import com.github.e0ipso.tokkl.timerstate.TimerState
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Timer page and the full-screen timer show for the cache's running entry and the
 * server's timer state: the pause chain's stored seconds, its clone on the server's clock, and
 * where Pause is offered.
 */
class TimerControlsTest {

    private val start: Instant = Instant.parse("2026-10-23T09:00:00Z")
    private val kept = EntryFields("Write the PRD", projectId = 7, taskId = 9, tags = listOf("docs"), billable = true)

    @Test
    fun aRunningEntryOutsideAChainShowsItsOwnTimeAndOffersPause() {
        val running = entry(id = 42)

        val shown = serverTimerControls(EntryWithSendState(running, unsent = true), held(revision = 3), timerApi = true)

        assertEquals(
            TimerControls.Running(running, unsent = true, accumulatedSeconds = 0, target = TimerTarget.server(entryId = 42), pausable = true),
            shown,
        )
    }

    @Test
    fun theClonesTimeIsTheChainsSecondsPlusItsOwnOnTheServersClock() {
        val clone = entry(id = 55)
        // The server's clock runs 20 s ahead of the phone's, and the server set the clone's start.
        val state = held(revision = 8, pause = pause(seconds = 600, cloneEntryId = 55), offset = Duration.ofSeconds(20))

        val shown = serverTimerControls(EntryWithSendState(clone, unsent = false), state, timerApi = true) as TimerControls.Running

        assertEquals(600L, shown.accumulatedSeconds)
        assertEquals(Duration.ofSeconds(20), shown.clockOffset)
        assertEquals(TimerTarget.server(entryId = 55), shown.target)
        assertEquals("the stored seconds plus the clone's on the server's clock", 600L + 90 + 20, shown.seconds(start.plusSeconds(90)))
    }

    @Test
    fun aRunningEntryThatIsNotTheCloneWinsAndAddsNothing() {
        // A Start queued on the phone that the server has not had yet shows over its chain.
        val started = entry(id = -9)

        val overPaused = serverTimerControls(EntryWithSendState(started, unsent = true), held(revision = 8, pause = pause(600, cloneEntryId = null)), timerApi = true)
        val overClone = serverTimerControls(EntryWithSendState(started, unsent = true), held(revision = 9, pause = pause(600, cloneEntryId = 55)), timerApi = true)

        val expected = TimerControls.Running(started, unsent = true, accumulatedSeconds = 0, target = TimerTarget.server(entryId = -9), pausable = true)
        assertEquals("a timer started while paused", expected, overPaused)
        assertEquals("a timer that replaced the clone", expected, overClone)
    }

    @Test
    fun aPausedChainShowsTheServersFrozenSecondsAndWhatResumeStarts() {
        val shown = serverTimerControls(running = null, held(revision = 12, pause = pause(seconds = 3_725, cloneEntryId = null)), timerApi = true)

        assertEquals(
            TimerControls.Paused(seconds = 3_725, workspaceId = 3, fields = kept, target = TimerTarget.server(revision = 12)),
            shown,
        )
    }

    @Test
    fun aPausedTargetFollowsTheRevisionSoAnOlderOneActsOnNothing() {
        val before = serverTimerControls(null, held(revision = 12, pause = pause(600, cloneEntryId = null)), timerApi = true) as TimerControls.Paused
        val renamed = serverTimerControls(null, held(revision = 13, pause = pause(600, cloneEntryId = null)), timerApi = true) as TimerControls.Paused

        assertEquals(TimerTarget.server(revision = 12), before.target)
        assertEquals(TimerTarget.server(revision = 13), renamed.target)
    }

    @Test
    fun aChainWhoseCloneNoLongerRunsInTheCacheShowsNothing() {
        // The clone's Stop is queued on the phone, and the server has not ended the chain yet.
        val shown = serverTimerControls(running = null, held(revision = 9, pause = pause(600, cloneEntryId = 55)), timerApi = true)

        assertEquals(TimerControls.Idle, shown)
    }

    @Test
    fun withNoTimerAndNoChainTheControlsAreIdle() {
        assertEquals(TimerControls.Idle, serverTimerControls(running = null, held(revision = 0), timerApi = true))
        assertEquals(TimerControls.Idle, serverTimerControls(running = null, held = null, timerApi = true))
    }

    @Test
    fun withoutTheTimerApiNoChainShowsAndPauseIsNotOffered() {
        val paused = held(revision = 4, pause = pause(600, cloneEntryId = null))
        val clone = held(revision = 5, pause = pause(600, cloneEntryId = 42))

        assertEquals("a held pause counts for nothing", TimerControls.Idle, serverTimerControls(null, paused, timerApi = false))
        val running = serverTimerControls(EntryWithSendState(entry(42), unsent = false), clone, timerApi = false) as TimerControls.Running
        assertEquals("nor does a held clone's time", 0L, running.accumulatedSeconds)
        assertEquals(Duration.ZERO, running.clockOffset)
        assertFalse(running.pausable)
    }

    @Test
    fun aPomodoroSessionHidesPause() {
        val session = held(revision = 6, pomodoro = pomodoro(entryId = 42))

        val running = serverTimerControls(EntryWithSendState(entry(42), unsent = false), session, timerApi = true) as TimerControls.Running

        assertFalse("the server refuses Pause during a session", running.pausable)
        assertTrue(
            "outside a session it shows",
            (serverTimerControls(EntryWithSendState(entry(42), unsent = false), held(revision = 7), timerApi = true) as TimerControls.Running).pausable,
        )
    }

    @Test
    fun theClockNeverCountsBackBeforeTheClonesStart() {
        val state = held(revision = 8, pause = pause(600, cloneEntryId = 55), offset = Duration.ofSeconds(-40))
        val shown = serverTimerControls(EntryWithSendState(entry(55), unsent = false), state, timerApi = true) as TimerControls.Running

        assertEquals(600L, shown.seconds(start.plusSeconds(30)))
    }

    private fun entry(id: Long) = CachedTimeEntry(
        id = id,
        workspaceId = 3,
        projectId = kept.projectId,
        taskId = kept.taskId,
        description = kept.description,
        start = start,
        stop = null,
        billable = kept.billable,
        tags = kept.tags,
        tagIds = emptyList(),
    )

    /** A state the phone received at [start] while the server's clock read [offset] later. */
    private fun held(
        revision: Long,
        pause: TimerPause? = null,
        pomodoro: TimerPomodoro? = null,
        offset: Duration = Duration.ZERO,
    ): HeldTimerState {
        val running = pause?.cloneEntryId?.let { id ->
            ServerTimeEntry(id = id, workspaceId = 3, description = kept.description, start = start.toString())
        }
        return HeldTimerState(TimerState(revision, start.plus(offset), running, pause, pomodoro, discardedPomodoro = null), receivedAt = start)
    }

    private fun pause(seconds: Long, cloneEntryId: Long?) = TimerPause(
        paused = cloneEntryId == null,
        seconds = seconds,
        workspaceId = 3,
        description = kept.description,
        projectId = kept.projectId,
        taskId = kept.taskId,
        tagIds = listOf(11),
        tags = kept.tags,
        billable = kept.billable,
        cloneEntryId = cloneEntryId,
    )

    private fun pomodoro(entryId: Long) = TimerPomodoro(
        workMinutes = 25,
        shortRestMinutes = 5,
        longRestMinutes = 15,
        longRestEvery = 4,
        phase = TimerPomodoroPhase(PhaseKind.WORK, start, completed = 0, plannedEnd = start.plusSeconds(1_500)),
        upcomingPhase = PhaseKind.SHORT_REST,
        transition = PhaseTransition.WORK_TO_SHORT_REST,
        number = 1,
        entryId = entryId,
        pomodoroStart = start,
    )
}
