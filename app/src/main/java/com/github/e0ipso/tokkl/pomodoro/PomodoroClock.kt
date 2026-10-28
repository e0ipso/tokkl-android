package com.github.e0ipso.tokkl.pomodoro

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

/**
 * The ringing phase's clock at the phone's [deviceNow]: time left before its planned end, or the
 * overtime since, rounded as [phaseClockAt] rounds them.
 */
fun PomodoroRing.clockAt(deviceNow: Instant): PomodoroClock = clockFor(Duration.between(deviceNow, end).toMillis())
