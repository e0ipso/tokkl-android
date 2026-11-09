package com.github.e0ipso.tokkl.timerstate

import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.QueueSender
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * A timer control on a surface outside the app's screens: the running timer's notification, the
 * home screen widget, the Quick Settings tile and its long press, and the long-timer reminder.
 */
enum class TimerSurfaceAction { Pause, Resume, Stop }

/** What a tap on such a control comes to, checked against what the phone holds at the tap. */
sealed interface SurfaceTap {

    /** A Stop of the running entry, done on the phone and queued, as the app's Stop does. */
    data object Queued : SurfaceTap

    /**
     * A Pause, Resume or Stop while paused, which needs the server and is never queued. [send]
     * sends it once and returns what it came to, or null for a duplicate tap or a target that
     * changed while waiting for the previous call. A surface that
     * runs in a broadcast ends the broadcast first, so a slow server cannot hold it past its time
     * limit.
     */
    // Public for the JVM tests of the surfaces in other modules, which stand in for a tap with it.
    class Request
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    constructor(
        private val request: suspend () -> TimerActionResult?,
    ) : SurfaceTap {
        suspend fun send(): TimerActionResult? = request()
    }
}

/**
 * The one policy behind the timer controls of the running notification, the widget, the tile and
 * the reminder (docs/PRD.md N1, N3, N4, N7, T1). Each control carries the [TimerTarget] its
 * surface drew from the shared [TimerControls], and a tap goes ahead only while the phone still
 * holds what it showed: the same account, the same running entry, or a paused chain.
 *
 * Stop of a running entry, a resumed clone included, goes to the change queue, so it works
 * offline and the server ends the chain when it gets it. Pause, Resume and Stop while paused go to
 * the server through [server]. Pause sends no revision. Resume and Stop while paused send the
 * revision the control was drawn at, also after a process restart, so the server refuses a tap on
 * a state it has moved past. Pause goes ahead only where the controls offer it now, which needs
 * the timer API and no Pomodoro session, so a control drawn before either changed sends nothing.
 * One server action goes out at a time. Duplicate taps on the same state send nothing. Once
 * the server publishes the next state, a tap on its control waits for the previous call to
 * return, then checks the state again before sending. A Stop of a running entry never waits.
 *
 * @param state what the phone shows now, read at the tap, and the signed-in account's name, as
 *   the targets of its controls carry it.
 * @param changes the change queue, which Stop of a running entry goes to.
 * @param server the server's Pause, Resume and Discard.
 * @param sender sends the queue and waits for it, so a Start the server has not had yet reaches
 *   it before the Pause that follows it.
 */
class TimerSurfaceActions(
    private val state: CurrentTimerState,
    private val changes: EntryChanges,
    private val server: PauseActions,
    private val sender: QueueSender,
) {

    /**
     * Checks [action] on the control drawn with [target] against what the phone holds now. A Stop
     * of a running entry is queued here. Returns null when the phone no longer shows what the
     * control showed, or the action is not offered there, and nothing was done.
     */
    suspend fun tap(action: TimerSurfaceAction, target: TimerTarget): SurfaceTap? {
        if (target.account != state.account()) return null
        return when (action) {
            TimerSurfaceAction.Pause -> pause(target)

            TimerSurfaceAction.Resume -> whilePaused(target) { server.resume(target.revision) }

            TimerSurfaceAction.Stop ->
                if (target.entryId != null) {
                    SurfaceTap.Queued.takeIf { changes.stop(target.entryId) }
                } else {
                    whilePaused(target) { server.discard(target.revision) }
                }
        }
    }

    /** A request for [action] on the paused chain [target] showed, while a chain is paused. */
    private suspend fun whilePaused(target: TimerTarget, action: suspend () -> TimerActionResult): SurfaceTap? {
        if (target.entryId != null) return null
        val paused = state.currentControls() as? TimerControls.Paused ?: return null
        return request(paused.target, action)
    }

    private suspend fun pause(target: TimerTarget): SurfaceTap? {
        val running = state.currentControls() as? TimerControls.Running ?: return null
        if (!running.pausable || target.entryId == null || changes.currentId(
                target.entryId,
            ) != running.entry.id
        ) {
            return null
        }
        return request(running.target) {
            if (running.unsent) sender.sendQueued()
            server.pause()
        }
    }

    /** Ignore duplicate taps; serialize actions on newly published states and recheck them. */
    private fun request(expected: TimerTarget, action: suspend () -> TimerActionResult): SurfaceTap.Request? {
        if (expected in sending) return null
        return SurfaceTap.Request {
            if (!sending.add(expected)) return@Request null
            try {
                requests.withLock {
                    val current = state.currentControls()
                    val moved = expected.account != state.account() || current.target != expected
                    if (moved || (current is TimerControls.Running && !current.pausable)) {
                        return@withLock null
                    }
                    action()
                }
            } finally {
                sending.remove(expected)
            }
        }
    }

    private val sending = ConcurrentHashMap.newKeySet<TimerTarget>()
    private val requests = Mutex()
}
