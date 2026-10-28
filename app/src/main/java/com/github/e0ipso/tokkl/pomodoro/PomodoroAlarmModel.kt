package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the full-screen alarm shows.
 *
 * @property ring the phase it is bound to, at the newest revision held, or null before it opens.
 * @property problem why the last Next phase or End session did not go through, or null.
 * @property closed the screen has nothing to show and finishes.
 */
internal data class PomodoroAlarmState(
    val ring: PomodoroRing? = null,
    val busy: Boolean = false,
    val problem: TimerActionResult? = null,
    val closed: Boolean = false,
)

/**
 * The full-screen alarm's model, bound to one phase of the server's session. It opens only for the
 * held phase its intent names, once that phase's planned end has come, and closes as soon as the
 * held state has another phase or none, whichever device moved it on. A new revision of the same
 * phase keeps it open with that revision. Next phase and End session go to the server with the
 * revision shown; one that fails leaves the alarm open, ringing, with the standard message.
 * Closing, Back, Home and recreation have no ringing side effects.
 *
 * All public methods are called on the main thread.
 *
 * @param held the held timer state while the server offers the timer API, and null otherwise.
 */
internal class PomodoroAlarmModel(
    held: Flow<HeldTimerState?>,
    private val actions: SessionActions,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutable = MutableStateFlow(PomodoroAlarmState())
    val state = mutable.asStateFlow()

    private var latest: HeldTimerState? = null
    private var loaded = false

    /** The phase an intent asked for that waits for the held state to load. */
    private var requested: Request? = null

    /** The phase this screen is bound to. */
    private var target: PomodoroPhaseKey? = null

    init {
        scope.launch { held.collect(::accept) }
    }

    /** An intent asks for the alarm of [expected]. A stale one never replaces an alarm on screen. */
    fun open(expected: PomodoroPhaseKey?) {
        requested = Request(expected)
        if (loaded) resolve()
    }

    fun next(): Job? = act { actions.nextPhase(it) }

    fun end(): Job? = act { actions.endSession(it) }

    private fun accept(value: HeldTimerState?) {
        latest = value
        loaded = true
        if (requested != null) resolve() else follow()
    }

    private fun resolve() {
        val expected = requested?.phase
        requested = null
        val ring = latest?.ring()
        val bound = target
        if (bound != null && ring?.phase == bound) {
            follow()
        } else if (expected != null && ring?.phase == expected && ring.due(clock.instant())) {
            target = expected
            mutable.value = PomodoroAlarmState(ring = ring)
        } else {
            close()
        }
    }

    /** Keeps the screen on its phase at the newest revision, or closes it once the phase has gone. */
    private fun follow() {
        val bound = target ?: return
        val ring = latest?.ring()?.takeIf { it.phase == bound }
        if (ring == null) close() else mutable.update { it.copy(ring = ring) }
    }

    private fun close() {
        target = null
        mutable.value = PomodoroAlarmState(closed = true)
    }

    private fun act(send: suspend (Long) -> TimerActionResult): Job? {
        val shown = state.value
        val ring = shown.ring ?: return null
        if (shown.busy || shown.closed) return null
        mutable.value = shown.copy(busy = true, problem = null)
        return scope.launch {
            val result = send(ring.revision)
            mutable.update { it.copy(busy = false, problem = result.takeUnless { result == TimerActionResult.Done }) }
        }
    }

    private class Request(val phase: PomodoroPhaseKey?)
}
