package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.TimerPause
import com.github.e0ipso.tokkl.api.TimerPomodoro
import com.github.e0ipso.tokkl.api.TimerPomodoroPhase
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.model.PhaseTransition
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * What the Timer page and the full-screen timer show for the cache's running entry and the
 * server's timer state: the pause chain's stored seconds, its clone on the server's clock, and
 * where Pause is offered.
 */
class TimerControlsTest {

    private val start: Instant = Instant.parse("2026-10-23T09:00:00Z")
    private val kept = EntryFields(
        "Write the PRD",
        projectId = ProjectId(7),
        taskId = TaskId(9),
        tags = listOf("docs"),
        billable = true,
    )

    /** A PendingIntent, a work request or a widget action keeps a target flat and reads it back as it was. */
    @Test
    fun aFlatTargetReadsBackAsTheTargetItKept() {
        for (target in listOf(TimerTarget.Running(EntryId(-3), ACCOUNT), TimerTarget.Paused(12, ACCOUNT))) {
            assertEquals(target, timerTarget(target.account, target.flatRevision, target.flatEntryId))
        }
        assertNull("one an older version kept names no account", timerTarget(null, 12, 0))
        assertNull(timerTarget("", 0, 42))
    }

    @Test
    fun aRunningEntryOutsideAChainShowsItsOwnTimeAndOffersPause() {
        val running = entry(id = 42)

        val shown = serverTimerControls(
            EntryWithSendState(running, unsent = true),
            held(revision = 3),
            timerApi = true,
            account = ACCOUNT,
        )

        assertEquals(
            TimerControls.Running(
                running,
                unsent = true,
                accumulatedSeconds = 0,
                target = TimerTarget.Running(EntryId(42), ACCOUNT),
                pausable = true,
            ),
            shown,
        )
    }

    @Test
    fun theClonesTimeIsTheChainsSecondsPlusItsOwnOnTheServersClock() {
        val clone = entry(id = 55)
        // The server's clock runs 20 s ahead of the phone's, and the server set the clone's start.
        val state = held(
            revision = 8,
            pause = pause(seconds = 600, cloneEntryId = EntryId(55)),
            offset = Duration.ofSeconds(20),
        )

        val shown = serverTimerControls(
            EntryWithSendState(clone, unsent = false),
            state,
            timerApi = true,
            account = ACCOUNT,
        ) as TimerControls.Running

        assertEquals(600L, shown.accumulatedSeconds)
        assertEquals(Duration.ofSeconds(20), shown.clockOffset)
        assertEquals(TimerTarget.Running(EntryId(55), ACCOUNT), shown.target)
        assertEquals(
            "the stored seconds plus the clone's on the server's clock",
            600L + 90 + 20,
            shown.seconds(start.plusSeconds(90)),
        )
    }

    @Test
    fun aRunningEntryThatIsNotTheCloneWinsAndAddsNothing() {
        // A Start queued on the phone that the server has not had yet shows over its chain.
        val started = entry(id = -9)

        val overPaused = serverTimerControls(
            EntryWithSendState(started, unsent = true),
            held(revision = 8, pause = pause(600, cloneEntryId = null)),
            timerApi = true,
            account = ACCOUNT,
        )
        val overClone = serverTimerControls(
            EntryWithSendState(started, unsent = true),
            held(revision = 9, pause = pause(600, cloneEntryId = EntryId(55))),
            timerApi = true,
            account = ACCOUNT,
        )

        val expected = TimerControls.Running(
            started,
            unsent = true,
            accumulatedSeconds = 0,
            target = TimerTarget.Running(EntryId(-9), ACCOUNT),
            pausable = true,
        )
        assertEquals("a timer started while paused", expected, overPaused)
        assertEquals("a timer that replaced the clone", expected, overClone)
    }

    @Test
    fun aPausedChainShowsTheServersFrozenSecondsAndWhatResumeStarts() {
        val shown = serverTimerControls(
            running = null,
            held(revision = 12, pause = pause(seconds = 3_725, cloneEntryId = null)),
            timerApi = true,
            account = ACCOUNT,
        )

        assertEquals(
            TimerControls.Paused(
                seconds = 3_725,
                workspaceId = WorkspaceId(3),
                fields = kept,
                target = TimerTarget.Paused(12, ACCOUNT),
            ),
            shown,
        )
    }

    @Test
    fun aChainWhoseCloneNoLongerRunsInTheCacheShowsNothing() {
        // The clone's Stop is queued on the phone, and the server has not ended the chain yet.
        val shown = serverTimerControls(
            running = null,
            held(revision = 9, pause = pause(600, cloneEntryId = EntryId(55))),
            timerApi = true,
            account = ACCOUNT,
        )

        assertEquals(TimerControls.Idle, shown)
    }

    @Test
    fun withNoTimerAndNoChainTheControlsAreIdle() {
        assertEquals(TimerControls.Idle, serverTimerControls(null, held(revision = 0), timerApi = true, ACCOUNT))
        assertEquals(TimerControls.Idle, serverTimerControls(null, held = null, timerApi = true, ACCOUNT))
    }

    @Test
    fun withoutTheTimerApiNoChainShowsAndPauseIsNotOffered() {
        val paused = held(revision = 4, pause = pause(600, cloneEntryId = null))
        val clone = held(revision = 5, pause = pause(600, cloneEntryId = EntryId(42)))

        assertEquals(
            "a held pause counts for nothing",
            TimerControls.Idle,
            serverTimerControls(null, paused, timerApi = false, account = ACCOUNT),
        )
        val running = serverTimerControls(
            EntryWithSendState(entry(42), unsent = false),
            clone,
            timerApi = false,
            account = ACCOUNT,
        ) as TimerControls.Running
        assertEquals("nor does a held clone's time", 0L, running.accumulatedSeconds)
        assertEquals(Duration.ZERO, running.clockOffset)
        assertFalse(running.pausable)
    }

    @Test
    fun aPomodoroSessionHidesPause() {
        val session = held(revision = 6, pomodoro = pomodoro(entryId = EntryId(42)))

        val running = serverTimerControls(
            EntryWithSendState(entry(42), unsent = false),
            session,
            timerApi = true,
            account = ACCOUNT,
        ) as TimerControls.Running

        assertFalse("the server refuses Pause during a session", running.pausable)
        assertTrue(
            "outside a session it shows",
            (
                serverTimerControls(
                    EntryWithSendState(entry(42), unsent = false),
                    held(revision = 7),
                    timerApi = true,
                    account = ACCOUNT,
                ) as TimerControls.Running
                ).pausable,
        )
    }

    @Test
    fun theClockNeverCountsBackBeforeTheClonesStart() {
        val state = held(revision = 8, pause = pause(600, cloneEntryId = EntryId(55)), offset = Duration.ofSeconds(-40))
        val shown = serverTimerControls(
            EntryWithSendState(entry(55), unsent = false),
            state,
            timerApi = true,
            account = ACCOUNT,
        ) as TimerControls.Running

        assertEquals(600L, shown.seconds(start.plusSeconds(30)))
    }

    private fun entry(id: Long) = TimeEntry(
        id = EntryId(id),
        workspaceId = WorkspaceId(3),
        projectId = kept.projectId,
        taskId = kept.taskId,
        description = kept.description,
        timing = EntryTiming.Running(start),
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
        return TimerState(revision, start.plus(offset), running, pause, pomodoro, discardedPomodoro = null).held(start)
    }

    private fun pause(seconds: Long, cloneEntryId: EntryId?) = TimerPause(
        paused = cloneEntryId == null,
        seconds = seconds,
        workspaceId = 3,
        description = kept.description,
        projectId = kept.projectId?.value,
        taskId = kept.taskId?.value,
        tagIds = listOf(11),
        tags = kept.tags,
        billable = kept.billable,
        cloneEntryId = cloneEntryId?.value,
    )

    private fun pomodoro(entryId: EntryId) = TimerPomodoro(
        workMinutes = 25,
        shortRestMinutes = 5,
        longRestMinutes = 15,
        longRestEvery = 4,
        phase = TimerPomodoroPhase(PhaseKind.WORK, start, completed = 0, plannedEnd = start.plusSeconds(1_500)),
        upcomingPhase = PhaseKind.SHORT_REST,
        transition = PhaseTransition.WORK_TO_SHORT_REST,
        number = 1,
        entryId = entryId.value,
        pomodoroStart = start,
    )
}

/** The name the timer controls of these tests carry for the signed-in account. */
private const val ACCOUNT = "account"
