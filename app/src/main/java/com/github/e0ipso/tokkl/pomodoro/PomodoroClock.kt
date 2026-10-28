package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.plannedEnd
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.serverNow
import java.time.Duration
import java.time.Instant

/** What a phase clock shows: [seconds] left, or [seconds] past the planned end when [overtime]. */
data class PomodoroClock(val overtime: Boolean, val seconds: Long)

/**
 * The held session's phase clock at the phone's [deviceNow], corrected by the state's offset, or
 * null without a session. Time left rounds up to whole seconds and overtime rounds down, so the
 * clock reads 0:00 until the planned end and then counts overtime from 0:00.
 */
fun HeldTimerState.phaseClockAt(deviceNow: Instant): PomodoroClock? {
    val end = state.pomodoro?.phase?.plannedEnd ?: return null
    return clockFor(Duration.between(serverNow(deviceNow), end).toMillis())
}

private fun clockFor(remainingMillis: Long): PomodoroClock =
    if (remainingMillis >= 0) PomodoroClock(false, (remainingMillis + 999) / 1000)
    else PomodoroClock(true, -remainingMillis / 1000)

// The local session's clock, which the phase alarm and the alarm screen still read until task 8
// moves them to the held state. Task 10 deletes it with the local session.
internal fun PomodoroSession.screenPhase() = SessionPhase(phaseKind, phaseStartedAt, completed)

internal fun PomodoroSession.screenLengths() = PomodoroSessionConfig(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery)

internal fun PomodoroSession.clockAt(now: Instant): PomodoroClock =
    clockFor(Duration.between(now, plannedEnd(screenPhase(), screenLengths())).toMillis())
