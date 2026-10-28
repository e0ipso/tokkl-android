package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import java.time.Instant

/**
 * A phase of the server's session, by its `phase.start`. The phase alarm, the ring, the alarm
 * screen and every notification button carry it, so a delivery or a tap meant for an older phase
 * changes nothing once another device or the phone has moved the session on.
 */
data class PomodoroPhaseKey(val start: Instant)

/** The four released WAVs, one per server `transition`. The Android output maps them to bundled raw resources. */
enum class PomodoroSound(val transition: PhaseTransition) {
    WORK_TO_SHORT_REST(PhaseTransition.WORK_TO_SHORT_REST),
    WORK_TO_LONG_REST(PhaseTransition.WORK_TO_LONG_REST),
    SHORT_REST_TO_WORK(PhaseTransition.SHORT_REST_TO_WORK),
    LONG_REST_TO_WORK(PhaseTransition.LONG_REST_TO_WORK),
}

/**
 * The held session's phase as its alarm, its ring and the alarm screen show it.
 *
 * @property end the phone's time of the phase's planned end: the server's `planned_end` less the
 *   held state's clock offset.
 * @property sound what the phase end plays, from the server's `transition`.
 * @property revision the state's revision. Next phase and End session send it.
 */
data class PomodoroRing(
    val phase: PomodoroPhaseKey,
    val kind: PhaseKind,
    val sound: PomodoroSound,
    val number: Int,
    val longRestEvery: Int,
    val end: Instant,
    val revision: Long,
)

/** The phase this state's session is in, or null without a session. */
fun HeldTimerState.ring(): PomodoroRing? {
    val session = state.pomodoro ?: return null
    return PomodoroRing(
        phase = PomodoroPhaseKey(session.phase.start),
        kind = session.phase.kind,
        sound = PomodoroSound.entries.single { it.transition == session.transition },
        number = session.number,
        longRestEvery = session.longRestEvery,
        end = session.phase.plannedEnd.minus(clockOffset),
        revision = state.revision,
    )
}

/** Whether the phone's [deviceNow] has reached the phase's planned end, from which every device rings. */
internal fun PomodoroRing.due(deviceNow: Instant): Boolean = !deviceNow.isBefore(end)

/**
 * The ringing service as [PomodoroPhaseAlarm] drives it. The alarm calls these under its lock, and
 * Android dispatches them to the main thread.
 */
interface PomodoroRinging {

    /** Rings [ring]. While its phase already rings, the sound goes on and the notification shows [ring]. */
    suspend fun start(ring: PomodoroRing)

    /** Shows [ring] while its phase rings, as after a revision change within the phase. Starts nothing. */
    suspend fun update(ring: PomodoroRing)

    /** Stops the ring, unless [keep] is the phase it rings. */
    suspend fun stop(keep: PomodoroPhaseKey? = null)
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
 * Main-thread state for one service. A ring of the phase already ringing only posts the
 * notification again, with that ring's revision. There is deliberately no duration, timer,
 * auto-advance or silence action here: only a state with another phase, or none, stops it.
 */
internal class PomodoroRingController(private val output: PomodoroRingOutput) {
    var current: PomodoroRing? = null
        private set

    fun start(ring: PomodoroRing) {
        val playing = current
        if (playing != null && playing.phase == ring.phase && playing.sound == ring.sound) {
            current = ring
            output.show(ring)
            return
        }
        if (playing != null) stop()
        current = ring
        output.show(ring)
        // A failed audio device must not suppress the independently requested vibration.
        try {
            output.play(ring.sound)
        } finally {
            output.vibrate()
        }
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
