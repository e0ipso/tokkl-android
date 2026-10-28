package com.github.e0ipso.tokkl.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.showTimerActionFailure
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The notification's Stop, Pause and Resume actions (docs/PRD.md N1, T1), for what the
 * notification showed, without opening the app. [TokklApplication.timerSurfaceActions] decides
 * each one. Stop of a running entry changes the phone and queues the change, so it works offline,
 * and [RunningNotificationSync] shows the result. Pause, Resume and Stop while paused go to the
 * server after the broadcast has ended; one that fails, offline included, changes nothing and says
 * why in a toast. An action shown for a state the phone no longer holds, such as a second tap or a
 * tap after sign-out, does nothing.
 *
 * Declared in the manifest and not exported: only the notification's own PendingIntents reach it.
 */
class StopTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_STOP -> TimerSurfaceAction.Stop
            ACTION_PAUSE -> TimerSurfaceAction.Pause
            ACTION_RESUME -> TimerSurfaceAction.Resume
            else -> return
        }
        val target = intent.timerTarget()
        val app = context.applicationContext as TokklApplication
        val finish = goAsync().finishOnce()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tap = app.timerSurfaceActions.tap(action, target)
                // The server may be slow to answer. The timer sync service, which runs wherever
                // these actions are offered, keeps the process alive meanwhile.
                finish()
                if (tap is SurfaceTap.Request) tap.send()?.let { showTimerActionFailure(app, it) }
            } catch (e: SQLiteException) {
                // The disk refused the change. The timer keeps running, and the notification with it.
            } finally {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.github.e0ipso.tokkl.extra.ENTRY_ID"
        const val EXTRA_REVISION = "com.github.e0ipso.tokkl.extra.PAUSE_REVISION"
        const val EXTRA_ACCOUNT = "com.github.e0ipso.tokkl.extra.TIMER_ACCOUNT"

        const val ACTION_STOP = "com.github.e0ipso.tokkl.action.STOP_TIMER"
        const val ACTION_PAUSE = "com.github.e0ipso.tokkl.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.github.e0ipso.tokkl.action.RESUME_TIMER"
    }
}

/** Puts [target] in this intent's extras, for [timerTarget] to read back. */
fun Intent.putTarget(target: TimerTarget): Intent =
    putExtra(StopTimerReceiver.EXTRA_ACCOUNT, target.account)
        .putExtra(StopTimerReceiver.EXTRA_REVISION, target.revision)
        .putExtra(StopTimerReceiver.EXTRA_ENTRY_ID, target.entryId)

/**
 * The [TimerTarget] [putTarget] put here. An intent without an account, as from a notification
 * posted by an older version, names none, so its actions act on nothing.
 */
fun Intent.timerTarget(): TimerTarget = TimerTarget.server(
    revision = getLongExtra(StopTimerReceiver.EXTRA_REVISION, 0),
    entryId = getLongExtra(StopTimerReceiver.EXTRA_ENTRY_ID, 0),
    account = getStringExtra(StopTimerReceiver.EXTRA_ACCOUNT).orEmpty(),
)

/** Ends this broadcast the first time the function it returns is called, and never again. */
internal fun BroadcastReceiver.PendingResult.finishOnce(): () -> Unit {
    val finished = AtomicBoolean(false)
    return { if (finished.compareAndSet(false, true)) finish() }
}
