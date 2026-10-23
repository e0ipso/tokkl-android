package com.github.e0ipso.tokkl.time

import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max

// Port of ../tokkl/packages/common/pomodoroSession.ts at
// tokkl commit bed61cb1e9e3179efdc0e29b82bc8ab9e663b71e (Tokkl 1.8.1).
// PomodoroTest replays all 64 cases of fixtures/pomodoroSession.json at that commit.
// Numbers and phase names retain the upstream validation, including fractional/invalid inputs.
// IllegalArgumentException corresponds to RangeError; Instants outside JavaScript's Date range
// are invalid dates, as in Snap.kt. All instants have JavaScript Date millisecond precision.

/** Phase lengths in whole minutes, and the number of ended work phases before a long rest. */
data class PomodoroSessionConfig(
    val workMinutes: Double = 25.0,
    val shortRestMinutes: Double = 5.0,
    val longRestMinutes: Double = 15.0,
    val longRestEvery: Double = 4.0,
)

data class ConfigRange(val min: Double, val max: Double)

val DEFAULT_POMODORO_SESSION_CONFIG = PomodoroSessionConfig()

val POMODORO_SESSION_CONFIG_RANGES: Map<String, ConfigRange> = java.util.Collections.unmodifiableMap(
    mapOf(
        "workMinutes" to ConfigRange(1.0, 180.0),
        "shortRestMinutes" to ConfigRange(1.0, 60.0),
        "longRestMinutes" to ConfigRange(1.0, 60.0),
        "longRestEvery" to ConfigRange(1.0, 12.0),
    ),
)

/**
 * [kind] is work, shortRest or longRest. [completed] counts work phases ended since the session
 * began or the last long rest ended. A rest includes the work phase it follows in that count.
 * [startedAt] is the session start or the instant the previous phase was confirmed.
 */
data class SessionPhase(val kind: String, val startedAt: Instant, val completed: Double)

/** [skipped] means the user confirmed before the planned end. */
data class PhaseChange(val next: SessionPhase, val transition: String, val skipped: Boolean)

/** Starts with work and no ended work phases. Callers supply every instant; these rules read no clock. */
fun startSession(at: Instant): SessionPhase = SessionPhase("work", sessionDate(sessionTime(at)), 0.0)

/** The instant a phase reaches its configured length. It stays in that phase until confirmed. */
fun plannedEnd(
    phase: SessionPhase,
    config: PomodoroSessionConfig = DEFAULT_POMODORO_SESSION_CONFIG,
): Instant {
    checkSessionConfig(config)
    checkSessionPhase(phase)
    val minutes = when (phase.kind) {
        "work" -> config.workMinutes
        "shortRest" -> config.shortRestMinutes
        else -> config.longRestMinutes
    }
    return sessionDate(sessionTime(phase.startedAt) + minutes * 60_000)
}

/** Seconds past the planned end, retaining fractional seconds, or zero before it. */
fun overtimeSeconds(
    phase: SessionPhase,
    at: Instant,
    config: PomodoroSessionConfig = DEFAULT_POMODORO_SESSION_CONFIG,
): Double = max(0.0, (sessionTime(at) - sessionTime(plannedEnd(phase, config))) / 1000)

/** The transition on confirm, including when longRestEvery has been lowered below the count. */
fun transitionKind(
    phase: SessionPhase,
    config: PomodoroSessionConfig = DEFAULT_POMODORO_SESSION_CONFIG,
): String {
    checkSessionConfig(config)
    checkSessionPhase(phase)
    return when (phase.kind) {
        "work" -> if (phase.completed + 1 >= config.longRestEvery) "workToLongRest" else "workToShortRest"
        "shortRest" -> "shortRestToWork"
        else -> "longRestToWork"
    }
}

/**
 * Advances only on confirm. Skipped work counts toward the long rest too; ending a long rest
 * resets the count. The next phase begins at the exact, unsnapped confirm instant.
 */
fun nextPhase(
    phase: SessionPhase,
    at: Instant,
    config: PomodoroSessionConfig = DEFAULT_POMODORO_SESSION_CONFIG,
): PhaseChange {
    val transition = transitionKind(phase, config)
    val confirmedAt = sessionTime(at)
    require(confirmedAt >= sessionTime(phase.startedAt)) { "Confirm must not be before the phase start" }
    val startedAt = sessionDate(confirmedAt)
    val skipped = confirmedAt < sessionTime(plannedEnd(phase, config))
    val next = when (transition) {
        "workToShortRest" -> SessionPhase("shortRest", startedAt, phase.completed + 1)
        "workToLongRest" -> SessionPhase("longRest", startedAt, phase.completed + 1)
        else -> SessionPhase("work", startedAt, if (transition == "longRestToWork") 0.0 else phase.completed)
    }
    return PhaseChange(next, transition, skipped)
}

/**
 * The entry from a work start to the next confirmed work start, or session end. Snap each end to
 * the account's nearest wall-clock grid point, taking the later point on a tie. A short or
 * collapsed span has no entry. The caller retains the unsnapped end for the next pomodoro.
 */
fun pomodoroEntrySpan(start: Instant, end: Instant, minutes: Double, timeZone: ZoneId): SnappedSpan? =
    snapSpan(start, end, minutes, timeZone)

private fun checkSessionConfig(config: PomodoroSessionConfig) {
    val values = mapOf(
        "workMinutes" to config.workMinutes,
        "shortRestMinutes" to config.shortRestMinutes,
        "longRestMinutes" to config.longRestMinutes,
        "longRestEvery" to config.longRestEvery,
    )
    for ((key, range) in POMODORO_SESSION_CONFIG_RANGES) {
        val value = values.getValue(key)
        require(value.isFinite() && floor(value) == value && value in range.min..range.max) {
            "Invalid $key: $value"
        }
    }
}

private fun checkSessionPhase(phase: SessionPhase) {
    require(phase.kind in listOf("work", "shortRest", "longRest")) { "Invalid phase kind: ${phase.kind}" }
    require(phase.completed.isFinite() && floor(phase.completed) == phase.completed && phase.completed >= 0) {
        "Invalid completed count: ${phase.completed}"
    }
    sessionTime(phase.startedAt)
}

private const val SESSION_MAX_TIME_MS = 8_640_000_000_000_000L
private val SESSION_DATE_RANGE =
    Instant.ofEpochMilli(-SESSION_MAX_TIME_MS)..<Instant.ofEpochMilli(SESSION_MAX_TIME_MS + 1)

private fun sessionTime(at: Instant): Double {
    require(at in SESSION_DATE_RANGE) { "Invalid date" }
    return at.toEpochMilli().toDouble()
}

private fun sessionDate(ms: Double): Instant {
    require(ms.isFinite() && ms >= -SESSION_MAX_TIME_MS && ms <= SESSION_MAX_TIME_MS) { "Invalid date" }
    return Instant.ofEpochMilli(ms.toLong())
}
