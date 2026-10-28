package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.serverTimerControls
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerPause
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import com.github.e0ipso.tokkl.timerstate.TimerPomodoroPhase
import com.github.e0ipso.tokkl.timerstate.TimerState
import java.time.Duration
import java.time.Instant

/**
 * Server timer states for the checks of the native timer surfaces, and the shared controls the
 * app builds from them with the cache's running entry, as [serverTimerControls] does for every
 * surface.
 */
internal object ServerTimerStates {

    /** The account name the controls carry in these checks. */
    const val ACCOUNT = "the signed-in account"

    /** A state the phone received [at] while the server's clock read [offset] later. */
    fun held(revision: Long, at: Instant, pause: TimerPause? = null, pomodoro: TimerPomodoro? = null, offset: Duration = Duration.ZERO) =
        HeldTimerState(TimerState(revision, at.plus(offset), runningEntry = null, pause, pomodoro, discardedPomodoro = null), receivedAt = at)

    /** A chain paused at [seconds], which Resume starts with [fields] in [workspaceId]. */
    fun paused(seconds: Long, fields: EntryFields, workspaceId: Long = 1) = pause(seconds, fields, workspaceId, cloneEntryId = null)

    /** A chain whose clone [entryId] runs after [seconds] its earlier entries store. */
    fun resumed(seconds: Long, entryId: Long, fields: EntryFields = EntryFields(), workspaceId: Long = 1) =
        pause(seconds, fields, workspaceId, cloneEntryId = entryId)

    /** A Pomodoro session whose open pomodoro is [entryId], in its first work phase from [at]. */
    fun session(entryId: Long, at: Instant) = TimerPomodoro(
        workMinutes = 25,
        shortRestMinutes = 5,
        longRestMinutes = 15,
        longRestEvery = 4,
        phase = TimerPomodoroPhase(PhaseKind.WORK, at, completed = 0, plannedEnd = at.plusSeconds(1_500)),
        upcomingPhase = PhaseKind.SHORT_REST,
        transition = PhaseTransition.WORK_TO_SHORT_REST,
        number = 1,
        entryId = entryId,
        pomodoroStart = at,
    )

    /** The shared controls for the cache's [running] entry and the [held] state, for [ACCOUNT]. */
    fun controls(running: CachedTimeEntry?, held: HeldTimerState? = null, timerApi: Boolean = true, unsent: Boolean = false): TimerControls =
        serverTimerControls(running?.takeIf { it.stop == null }?.let { EntryWithSendState(it, unsent) }, held, timerApi, ACCOUNT)

    private fun pause(seconds: Long, fields: EntryFields, workspaceId: Long, cloneEntryId: Long?) = TimerPause(
        paused = cloneEntryId == null,
        seconds = seconds,
        workspaceId = workspaceId,
        description = fields.description,
        projectId = fields.projectId,
        taskId = fields.taskId,
        tagIds = emptyList(),
        tags = fields.tags,
        billable = fields.billable,
        cloneEntryId = cloneEntryId,
    )
}
