package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.PomodoroSession
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PomodoroAlarmState(
    val session: PomodoroSession? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val closed: Boolean = false,
)

/** UI commands only. Closing, Back, Home and recreation have no ringing side effects. */
internal class PomodoroAlarmModel(
    private val current: suspend () -> PomodoroSession?,
    private val reached: suspend () -> ReachedPhaseEnd?,
    private val next: suspend (PomodoroPhaseKey) -> Unit,
    private val end: suspend (PomodoroPhaseKey) -> Unit,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutable = MutableStateFlow(PomodoroAlarmState())
    val state = mutable.asStateFlow()
    private val commands = Mutex()
    private var target: PomodoroPhaseKey? = null

    fun open(expected: PomodoroPhaseKey?): Job = scope.launch {
        commands.withLock {
            try {
                val stored = current()
                if (expected != null && stored != null && stored.phaseKey() == expected && stored.isRinging()) {
                    target = expected
                    mutable.value = PomodoroAlarmState(session = stored)
                } else {
                    // A stale onNewIntent must not replace a valid alarm already on screen.
                    refreshLocked()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(session = null, failed = true)
            }
        }
    }

    fun refresh(): Job = scope.launch {
        commands.withLock {
            try {
                refreshLocked()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Disable stale buttons when durable state cannot be read.
                mutable.value = mutable.value.copy(session = null, failed = true)
            }
        }
    }

    fun next(): Job? = act(next)
    fun end(): Job? = act(end)

    private fun act(action: suspend (PomodoroPhaseKey) -> Unit): Job? {
        val shown = state.value
        val phase = shown.session?.phaseKey() ?: return null
        if (shown.busy || shown.closed) return null
        mutable.value = shown.copy(busy = true, failed = false)
        return scope.launch {
            commands.withLock {
                var failed = false
                try {
                    // The store checks this identity under its transaction guard.
                    action(phase)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed = true
                }
                try {
                    // A callback can fail after commit. Never restore/replay the old phase.
                    refreshLocked()
                    mutable.value = mutable.value.copy(busy = false, failed = failed && !state.value.closed)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    mutable.value = PomodoroAlarmState(failed = true)
                }
            }
        }
    }

    private suspend fun refreshLocked() {
        val stored = current()
        if (target != null && stored != null && stored.phaseKey() == target && stored.isRinging()) {
            mutable.value = mutable.value.copy(session = stored, closed = false)
        } else {
            mutable.value = PomodoroAlarmState(closed = true)
        }
    }

    private suspend fun PomodoroSession.isRinging(): Boolean =
        reached() == ReachedPhaseEnd(phaseKind, phaseStartedAt) && clockAt(clock.instant()).let { it.overtime || it.seconds == 0L }
}
