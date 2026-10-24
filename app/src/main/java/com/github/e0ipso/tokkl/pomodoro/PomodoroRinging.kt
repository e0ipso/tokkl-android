package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.transitionKind
import java.time.Instant

/** Identifies a stored phase, including its account/workspace and current pomodoro. */
data class PomodoroPhaseKey(
    val workspaceId: Long,
    val kind: String,
    val startedAt: Instant,
    val pomodoroStartedAt: Instant,
    val completed: Double,
)

internal fun PomodoroSession.phaseKey() = PomodoroPhaseKey(
    workspaceId, phaseKind, phaseStartedAt, pomodoroStartedAt, completed,
)

/** The four released WAVs. The Android output maps these to bundled raw resources. */
enum class PomodoroSound(val transition: String) {
    WORK_TO_SHORT_REST("workToShortRest"),
    WORK_TO_LONG_REST("workToLongRest"),
    SHORT_REST_TO_WORK("shortRestToWork"),
    LONG_REST_TO_WORK("longRestToWork"),
}

data class PomodoroRing(val phase: PomodoroPhaseKey, val sound: PomodoroSound)

internal fun PomodoroSession.ring(): PomodoroRing {
    val transition = transitionKind(
        SessionPhase(phaseKind, phaseStartedAt, completed),
        PomodoroSessionConfig(workMinutes, shortRestMinutes, longRestMinutes, longRestEvery),
    )
    return PomodoroRing(phaseKey(), PomodoroSound.entries.single { it.transition == transition })
}

/** Store callbacks run under its phase lock; Android dispatches them to the main thread. */
interface PomodoroRinging {
    suspend fun start(ring: PomodoroRing)

    suspend fun stop()
}

internal object NoPomodoroRinging : PomodoroRinging {
    override suspend fun start(ring: PomodoroRing) = Unit

    override suspend fun stop() = Unit
}

/** Framework effects are separate so unit tests can keep an indefinite ring without a clock. */
internal interface PomodoroRingOutput {
    fun show(ring: PomodoroRing)

    fun play(sound: PomodoroSound)

    fun vibrate()

    fun stopAudio()

    fun stopVibration()

    fun removeNotification()
}

/**
 * Main-thread state for one service. A repeated delivery only posts the notification again.
 * There is deliberately no duration, timer, auto-advance or silence action here. Only the
 * session store accepts Next/End and stops the output after committing the local change.
 */
internal class PomodoroRingController(private val output: PomodoroRingOutput) {
    var current: PomodoroRing? = null
        private set

    fun start(ring: PomodoroRing) {
        if (current == ring) {
            output.show(ring)
            return
        }
        if (current != null) stop()
        current = ring
        output.show(ring)
        // A failed audio device must not suppress the independently requested vibration.
        try {
            output.play(ring.sound)
        } finally {
            output.vibrate()
        }
    }

    fun dismissed(phase: PomodoroPhaseKey) {
        current?.takeIf { it.phase == phase }?.let(output::show)
    }

    fun stop() {
        current = null
        try {
            output.stopAudio()
        } finally {
            try {
                output.stopVibration()
            } finally {
                output.removeNotification()
            }
        }
    }
}

/** One shared id for the session notification, including its ringing form (TKA-0251). */
object PomodoroSessionNotification {
    const val ID = 3
}
