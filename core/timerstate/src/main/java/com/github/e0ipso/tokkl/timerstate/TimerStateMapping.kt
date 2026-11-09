package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.TimerPause
import com.github.e0ipso.tokkl.api.TimerPomodoro
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.cache.toTimeEntry
import com.github.e0ipso.tokkl.model.DiscardedPomodoro
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.PauseChain
import com.github.e0ipso.tokkl.model.PomodoroPhase
import com.github.e0ipso.tokkl.model.PomodoroSession
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.WorkspaceId
import java.time.Instant
import com.github.e0ipso.tokkl.api.DiscardedPomodoro as ServerDiscardedPomodoro

// The one mapping from the server's timer state to the model's types. The holder keeps and stores
// the server's state as it came; everything above core:timerstate reads these.

/** This state from the server, as the phone holds it once it arrived at [receivedAt]. */
fun TimerState.held(receivedAt: Instant): HeldTimerState = HeldTimerState(
    revision = revision,
    serverTime = serverTime,
    // A running entry the phone cannot read shows as none.
    runningEntry = runningEntry?.toTimeEntry(),
    pause = pause?.toModel(),
    pomodoro = pomodoro?.toModel(),
    discardedPomodoro = discardedPomodoro?.toModel(),
    receivedAt = receivedAt,
)

internal fun StoredHeld.toHeld(): HeldTimerState = state.held(receivedAt)

/** The server pauses a chain by ending its clone, so a chain without one is the paused chain. */
private fun TimerPause.toModel(): PauseChain {
    val workspace = WorkspaceId(workspaceId)
    val fields = EntryFields(description, projectId?.let(::ProjectId), taskId?.let(::TaskId), tags, billable)
    val clone = cloneEntryId ?: return PauseChain.Paused(seconds, workspace, fields)
    return PauseChain.Resumed(seconds, workspace, fields, EntryId(clone))
}

private fun TimerPomodoro.toModel() = PomodoroSession(
    workMinutes = workMinutes,
    shortRestMinutes = shortRestMinutes,
    longRestMinutes = longRestMinutes,
    longRestEvery = longRestEvery,
    phase = PomodoroPhase(phase.kind, phase.start, phase.completed, phase.plannedEnd),
    upcomingPhase = upcomingPhase,
    transition = transition,
    number = number,
    entryId = EntryId(entryId),
    pomodoroStart = pomodoroStart,
)

private fun ServerDiscardedPomodoro.toModel() = DiscardedPomodoro(EntryId(entryId), start, stop)
