package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import java.security.MessageDigest
import java.time.Instant

/**
 * What the session notification shows of the held session: the phase, "Pomodoro N of M" from the
 * server's number, the upcoming phase, the open pomodoro's description and the planned end that
 * its native clock counts down to, then past into overtime. Its buttons carry [phase] and
 * [revision].
 *
 * @property end the phone's time of the planned end, corrected by the state's clock offset.
 */
internal data class PomodoroSessionNotificationContent(
    val phase: PomodoroPhaseKey,
    val kind: PhaseKind,
    val upcoming: PhaseKind,
    val number: Int,
    val longRestEvery: Int,
    val description: String,
    val end: Instant,
    val revision: Long,
) {
    // No entry description is written to the preferences file.
    val key: String get() = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** The session notification of this state, or null without a session. */
internal fun HeldTimerState.notificationContent(): PomodoroSessionNotificationContent? {
    val session = state.pomodoro ?: return null
    val ring = ring() ?: return null
    val open = state.runningEntry?.takeIf { it.id == session.entryId }
    return PomodoroSessionNotificationContent(
        phase = ring.phase,
        kind = session.phase.kind,
        upcoming = session.upcomingPhase,
        number = session.number,
        longRestEvery = session.longRestEvery,
        description = open?.description.orEmpty(),
        end = ring.end,
        revision = state.revision,
    )
}
