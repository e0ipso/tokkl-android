package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The exact alarm and the ring of the server's Pomodoro session, from the account's held timer
 * state. The server owns the session: this arms one alarm at the phase's planned end, corrected
 * by the state's clock offset and keyed by `phase.start`, rings from that end until a state with
 * another phase or none arrives, and never moves the session on. Next phase and End session go to
 * the server, from any device, and their state ends the ring here.
 *
 * - A state of a new phase cancels the old alarm, stops its ring and arms the new phase. A state
 *   without a session, or a server without the timer API, cancels both.
 * - A new revision of the same phase moves the alarm with the state's offset and shows the new
 *   revision on a ring already going, which it never stops.
 * - A planned end already past is armed in the past, so the alarm rings at once. Where no exact
 *   alarm can be set, a past end rings straight away instead.
 * - An alarm delivered for another phase than the held one rings nothing.
 *
 * Every call reads the held state anew, under one lock, so a delivery, the ringing service's own
 * check and a state change never act on different phases.
 *
 * @param held the account's held timer state now: after a process restart, the stored one.
 * @param available whether the account's server offers the timer API.
 * @param io where calls run: the first read of the held state and of the capabilities loads them
 *   from disk.
 * @param clock the phone's clock.
 */
internal class PomodoroPhaseAlarm(
    private val held: () -> HeldTimerState?,
    private val available: () -> Boolean,
    private val alarms: PomodoroAlarmScheduler,
    private val ringing: PomodoroRinging,
    private val io: CoroutineContext,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Mutex()

    /** The phase and time of the alarm this process armed, so a state that moves neither arms nothing. */
    private var armed: Pair<PomodoroPhaseKey, Instant>? = null

    /** The phase this process asked to ring. Its alarm has gone off. */
    private var rung: PomodoroPhaseKey? = null

    /** True when the exact-alarm permission is denied. The Pomodoro page reads this. */
    val permissionDenied: Boolean get() = alarms.permissionDenied

    /**
     * Arms, moves or drops the alarm and the ring for the held state now. Every state change and
     * every change of the capability calls it. [rearm] arms the alarm again even where this
     * process already armed it, as after boot, an app update, a clock or timezone change or an
     * exact-alarm grant.
     */
    suspend fun follow(rearm: Boolean = false) = locked {
        val ring = currentLocked()
        if (ring == null) {
            releaseLocked()
            return@locked
        }
        ringing.stop(keep = ring.phase)
        if (rung != ring.phase) rung = null
        if (rung == ring.phase) {
            // The alarm has gone off and the phase still rings, now with this revision.
            ringing.update(ring)
            return@locked
        }
        if (!rearm && armed == ring.phase to ring.end) return@locked
        if (arm(ring)) return@locked
        // No exact alarm can be set. A planned end already past still rings.
        if (ring.due(clock.instant())) ringLocked(ring)
    }

    /**
     * The alarm of [phase] went off. Rings while [phase] is the held one and its planned end has
     * come; an alarm delivered early, as after a clock change, is armed again for that end.
     */
    suspend fun onAlarm(phase: PomodoroPhaseKey) = locked {
        val ring = currentLocked()?.takeIf { it.phase == phase } ?: return@locked
        if (ring.due(clock.instant())) ringLocked(ring) else arm(ring)
    }

    /**
     * Calls [use] with the ring of the held phase while it is [expected], or any for null, and its
     * planned end has come, and returns whether it did. The ringing service checks a start, or its
     * restart after the process died, through this before it plays.
     */
    suspend fun withRingingPhase(expected: PomodoroPhaseKey?, use: suspend (PomodoroRing) -> Unit): Boolean = locked {
        val ring = currentLocked()
            ?.takeIf { (expected == null || it.phase == expected) && it.due(clock.instant()) }
            ?: return@locked false
        rung = ring.phase
        use(ring)
        true
    }

    /** Posts the ringing notification of [phase] again after a swipe, while it still rings. */
    suspend fun repost(phase: PomodoroPhaseKey) = locked {
        val ring = currentLocked()?.takeIf { it.phase == phase && rung == phase } ?: return@locked
        ringing.start(ring)
    }

    /** The held phase, or null without a session or the timer API. */
    suspend fun current(): PomodoroRing? = locked { currentLocked() }

    /**
     * Calls [read] with the held state, null without the timer API, and whether its phase rings or
     * is about to: it rang, or its planned end has come and its alarm goes off at once. A
     * notification posted from them cannot replace a ring that starts meanwhile.
     */
    suspend fun <T> withState(read: suspend (HeldTimerState?, ringing: Boolean) -> T): T = locked {
        val state = heldLocked()
        val ring = state?.ring()
        read(state, ring != null && (ring.phase == rung || ring.due(clock.instant())))
    }

    /** Drops the alarm and the ring. Sign-out calls it after retiring the held state. */
    suspend fun release() = locked { releaseLocked() }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) { lock.withLock { block() } }

    private fun heldLocked(): HeldTimerState? = held()?.takeIf { available() }

    private fun currentLocked(): PomodoroRing? = heldLocked()?.ring()

    /** Arms [ring]'s planned end. Returns false when no exact alarm can be set. */
    private fun arm(ring: PomodoroRing): Boolean {
        val scheduled = alarms.schedule(ring.end, ring.phase)
        armed = if (scheduled) ring.phase to ring.end else null
        return scheduled
    }

    private suspend fun ringLocked(ring: PomodoroRing) {
        rung = ring.phase
        ringing.start(ring)
    }

    private suspend fun releaseLocked() {
        rung = null
        armed = null
        try {
            ringing.stop()
        } finally {
            alarms.cancel()
        }
    }
}

/**
 * Sends Next phase, or End session when [end], for [phase] at the [revision] a notification or the
 * alarm screen showed. A button of a phase that is no longer the held one sends nothing and
 * returns null. Otherwise returns what the server action came to: its state, refusal included,
 * reaches the held state and through it the alarm.
 */
internal suspend fun PomodoroPhaseAlarm.sendPhaseAction(
    actions: SessionActions,
    phase: PomodoroPhaseKey,
    revision: Long,
    end: Boolean,
): TimerActionResult? {
    if (current()?.phase != phase) return null
    return if (end) actions.endSession(revision) else actions.nextPhase(revision)
}
