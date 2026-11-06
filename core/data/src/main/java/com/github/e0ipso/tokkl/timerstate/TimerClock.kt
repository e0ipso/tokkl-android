package com.github.e0ipso.tokkl.timerstate

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// What the screens and notifications show of a held timer state, computed from the state and the
// phone's clock corrected by the state's offset. Nothing here applies a Pomodoro or pause rule:
// the server decides the phase, its planned end, the transition and the pomodoro number. Pass the
// phone's own clock reading as `deviceNow`.

/** The server's clock at the phone's [deviceNow]. */
fun HeldTimerState.serverNow(deviceNow: Instant): Instant = deviceNow.plus(clockOffset)

/** Time left before the phase's planned end at [deviceNow], zero after it. Null without a session. */
fun HeldTimerState.phaseRemaining(deviceNow: Instant): Duration? {
    val end = state.pomodoro?.phase?.plannedEnd ?: return null
    return nonNegative(Duration.between(serverNow(deviceNow), end))
}

/** How far [deviceNow] is past the phase's planned end, zero before it. Null without a session. */
fun HeldTimerState.phaseOvertime(deviceNow: Instant): Duration? {
    val end = state.pomodoro?.phase?.plannedEnd ?: return null
    return nonNegative(Duration.between(end, serverNow(deviceNow)))
}

/**
 * The running clone's time since its start at [deviceNow]. Null without a chain, while it is
 * paused, or when the running entry is not its clone.
 */
fun HeldTimerState.cloneElapsed(deviceNow: Instant): Duration? {
    val pause = state.pause ?: return null
    val clone = state.runningEntry?.takeIf { !pause.paused && it.id == pause.cloneEntryId } ?: return null
    val start = instantOrNull(clone.start) ?: return null
    return nonNegative(Duration.between(start, serverNow(deviceNow)))
}

/**
 * The pause chain's total at [deviceNow]: the seconds its stopped entries stored, frozen while
 * paused, plus [cloneElapsed] while its clone runs. Null without a chain. The day's totals count
 * the entries themselves and must not add this.
 */
fun HeldTimerState.chainTotal(deviceNow: Instant): Duration? {
    val pause = state.pause ?: return null
    return Duration.ofSeconds(pause.seconds).plus(cloneElapsed(deviceNow) ?: Duration.ZERO)
}

private fun nonNegative(duration: Duration): Duration = if (duration.isNegative) Duration.ZERO else duration

/** The v9 running entry's start, such as `2026-10-28T08:13:05+00:00`. */
private fun instantOrNull(text: String): Instant? =
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }
