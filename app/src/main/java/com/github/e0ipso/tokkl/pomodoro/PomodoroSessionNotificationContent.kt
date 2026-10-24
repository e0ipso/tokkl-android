package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.plannedEnd
import java.security.MessageDigest
import java.time.Instant

internal data class PomodoroSessionNotificationContent(
    val phase: PomodoroPhaseKey,
    val description: String,
    val pomodoro: Int,
    val end: Instant,
) {
    // No entry description is written to the preferences file.
    val key: String get() = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun PomodoroSession.notificationContent() = PomodoroSessionNotificationContent(
    phaseKey(), fields.description,
    completed.toInt() + if (phaseKind == "work") 1 else 0,
    plannedEnd(
        SessionPhase(phaseKind, phaseStartedAt, completed),
        PomodoroSessionConfig(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery),
    ),
)
