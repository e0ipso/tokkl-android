package com.github.e0ipso.tokkl.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The notification's Stop, Pause and Resume actions (docs/PRD.md N1, T1), for what the
 * notification showed, without opening the app. [TimerSurfaceActions] decides
 * each one. Stop of a running entry changes the phone and queues the change while the broadcast
 * lasts, so it works offline, and [RunningNotificationSync] shows the result. Pause, Resume and
 * Stop while paused need the server, so they go to WorkManager ([sendTimerAction]); one that fails,
 * offline included, changes nothing and says why in a toast. An action shown for a state the phone
 * no longer holds, such as a second tap or a tap after sign-out, does nothing.
 *
 * Declared in the manifest and not exported: only the notification's own PendingIntents reach it.
 */
class StopTimerReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val surfaceActions: TimerSurfaceActions by inject()
    private val workManager: CurrentWorkManager by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_STOP -> TimerSurfaceAction.Stop
            ACTION_PAUSE -> TimerSurfaceAction.Pause
            ACTION_RESUME -> TimerSurfaceAction.Resume
            else -> return
        }
        val target = intent.timerTarget()
        if (action != TimerSurfaceAction.Stop || target.entryId == null) {
            workManager.sendTimerAction(action, target)
            return
        }
        val pending = goAsync()
        scope.launch(io) {
            try {
                surfaceActions.tap(action, target)
            } catch (_: SQLiteException) {
                // The disk refused the change. The timer keeps running, and the notification with it.
            } finally {
                pending.finish()
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
fun Intent.putTarget(target: TimerTarget): Intent = putExtra(StopTimerReceiver.EXTRA_ACCOUNT, target.account)
    .putExtra(StopTimerReceiver.EXTRA_REVISION, target.revision)
    .putExtra(StopTimerReceiver.EXTRA_ENTRY_ID, target.entryId?.value ?: 0L)

/**
 * The [TimerTarget] [putTarget] put here. An intent without an account, as from a notification
 * posted by an older version, names none, so its actions act on nothing.
 */
fun Intent.timerTarget(): TimerTarget = TimerTarget.server(
    revision = getLongExtra(StopTimerReceiver.EXTRA_REVISION, 0),
    entryId = getLongExtra(StopTimerReceiver.EXTRA_ENTRY_ID, 0).takeIf { it != 0L }?.let(::EntryId),
    account = getStringExtra(StopTimerReceiver.EXTRA_ACCOUNT).orEmpty(),
)
