package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import com.github.e0ipso.tokkl.timerstate.TimerPomodoroPhase
import com.github.e0ipso.tokkl.timerstate.TimerState
import java.time.Instant

/**
 * A session the phone holds, for the page's presentation tests: [kind] began at [phaseStart] and
 * ends [phaseSeconds] later, the phone got it at [phaseStart] and the server's clock agrees with
 * the phone's.
 */
fun heldSession(
    kind: PhaseKind = PhaseKind.WORK,
    phaseStart: Instant,
    phaseSeconds: Long = 25 * 60,
    number: Int = 1,
    longRestEvery: Int = 4,
    revision: Long = 7,
    workspaceId: Long = 1,
    entryId: Long = 501,
    description: String = "",
): HeldTimerState {
    val work = kind == PhaseKind.WORK
    val session = TimerPomodoro(
        workMinutes = 25, shortRestMinutes = 5, longRestMinutes = 15, longRestEvery = longRestEvery,
        phase = TimerPomodoroPhase(kind, phaseStart, completed = if (work) number - 1 else number, plannedEnd = phaseStart.plusSeconds(phaseSeconds)),
        upcomingPhase = when {
            !work -> PhaseKind.WORK
            number % longRestEvery == 0 -> PhaseKind.LONG_REST
            else -> PhaseKind.SHORT_REST
        },
        transition = when {
            kind == PhaseKind.LONG_REST -> PhaseTransition.LONG_REST_TO_WORK
            kind == PhaseKind.SHORT_REST -> PhaseTransition.SHORT_REST_TO_WORK
            number % longRestEvery == 0 -> PhaseTransition.WORK_TO_LONG_REST
            else -> PhaseTransition.WORK_TO_SHORT_REST
        },
        number = number, entryId = entryId, pomodoroStart = phaseStart,
    )
    val entry = ServerTimeEntry(id = entryId, workspaceId = workspaceId, description = description, start = phaseStart.toString())
    return HeldTimerState(
        TimerState(revision, phaseStart, entry, pause = null, pomodoro = session, discardedPomodoro = null),
        receivedAt = phaseStart,
    )
}
