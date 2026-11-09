package com.github.e0ipso.tokkl.timernotice

import androidx.work.ListenableWorker
import com.github.e0ipso.tokkl.cache.LocalWrite
import com.github.e0ipso.tokkl.cache.localWrite
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget

/** What a timer control outside the app's screens came to, as [runSurfaceCommand] says it. */
sealed interface SurfaceCommandResult {

    /** It went through, or there was nothing to do, as for a second tap or a stale control. */
    data object Completed : SurfaceCommandResult

    /** The server refused the action or did not answer, as [result] says. The user was told. */
    data class Failed(val result: TimerActionResult) : SurfaceCommandResult

    /** The phone's storage refused to read or write it, so nothing changed. The user was told. */
    data class NotStored(val cause: Exception) : SurfaceCommandResult
}

/**
 * The one result policy of the timer controls outside the app's screens: the running
 * notification, the widget, the Quick Settings tile and its long press, the reminder, and the
 * work that sends their server actions. Runs [command], which reads and writes the phone's
 * storage and returns the result of the server action it sent, or null when it sent none. A
 * server action that did not go through says why through [failures], as the app does for a
 * failed timer action, and so does a failure of the phone's storage ([localWrite]): nothing that
 * failed passes as done. Anything else, cancellation included, still throws.
 */
suspend fun runSurfaceCommand(failures: TimerFailureNotice, command: suspend () -> TimerActionResult?): SurfaceCommandResult =
    when (val ran = localWrite(command)) {
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

/**
 * Runs [action] on the control drawn with [target] under [runSurfaceCommand]: checks it against
 * what the phone holds now, queues a Stop of a running entry and sends a server action once.
 */
suspend fun TimerSurfaceActions.runFromSurface(
    action: TimerSurfaceAction,
    target: TimerTarget,
    failures: TimerFailureNotice,
): SurfaceCommandResult = runSurfaceCommand(failures) { (tap(action, target) as? SurfaceTap.Request)?.send() }

/**
 * What the work that ran a control's action tells WorkManager: success when it went through or
 * had nothing to do, and failure when the server or the storage refused it. It never runs again:
 * the user was told and taps again.
 */
fun SurfaceCommandResult.toWorkResult(): ListenableWorker.Result = when (this) {
    SurfaceCommandResult.Completed -> ListenableWorker.Result.success()
    is SurfaceCommandResult.Failed, is SurfaceCommandResult.NotStored -> ListenableWorker.Result.failure()
}
