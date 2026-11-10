package com.github.e0ipso.tokkl.timernotice

import androidx.work.ListenableWorker
import com.github.e0ipso.tokkl.cache.LocalWrite
import com.github.e0ipso.tokkl.cache.localWrite
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget

/**
 * Tells the user that a timer action sent from outside the app's screens did not go through, in
 * the words the app's pages use ([timerNoticeText]): another device changed the timer first, no
 * connection, or the server's reason. The running notification, the widget, the Quick Settings
 * tile and its long press, the reminder, and the Pomodoro session and ringing notifications send
 * their Pause, Resume, Stop while paused, Next phase and End session this way. None of them has a
 * screen of its own. The notification feature says it in a toast; what the surface shows stays
 * as it was. A [TimerActionResult.Done] says nothing.
 */
interface TimerFailureNotice {
    suspend fun show(result: TimerActionResult)

    /** The phone's storage refused the action, so nothing changed. */
    suspend fun showNotStored()
}

sealed interface SurfaceCommandResult {
    /** Done, or nothing to do, as for a second tap or a stale control. */
    data object Completed : SurfaceCommandResult

    data class Failed(val result: TimerActionResult) : SurfaceCommandResult

    data class NotStored(val cause: Exception) : SurfaceCommandResult
}

/**
 * The one result policy of the timer controls outside the app's screens and the work behind them:
 * runs [command], which returns the server action's result or null for none, and tells [failures]
 * about a refused or unanswered action and about a storage failure ([localWrite]), so nothing that
 * failed passes as done.
 */
suspend fun runSurfaceCommand(
    failures: TimerFailureNotice,
    command: suspend () -> TimerActionResult?,
): SurfaceCommandResult = when (val ran = localWrite(command)) {
    is LocalWrite.Failed -> {
        failures.showNotStored()
        SurfaceCommandResult.NotStored(ran.cause)
    }

    is LocalWrite.Written -> when (val result = ran.value) {
        null, TimerActionResult.Done -> SurfaceCommandResult.Completed

        else -> {
            failures.show(result)
            SurfaceCommandResult.Failed(result)
        }
    }
}

suspend fun TimerSurfaceActions.runFromSurface(
    action: TimerSurfaceAction,
    target: TimerTarget,
    failures: TimerFailureNotice,
): SurfaceCommandResult = runSurfaceCommand(failures) { (tap(action, target) as? SurfaceTap.Request)?.send() }

// Never retried: the user was told and taps again.
fun SurfaceCommandResult.toWorkResult(): ListenableWorker.Result = when (this) {
    SurfaceCommandResult.Completed -> ListenableWorker.Result.success()
    is SurfaceCommandResult.Failed, is SurfaceCommandResult.NotStored -> ListenableWorker.Result.failure()
}
