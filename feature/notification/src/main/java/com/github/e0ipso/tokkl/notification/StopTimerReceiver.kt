package com.github.e0ipso.tokkl.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timernotice.runFromSurface
import com.github.e0ipso.tokkl.timernotice.sendTimerAction
import com.github.e0ipso.tokkl.timernotice.timerNotStoredText
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceAction
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.flatEntryId
import com.github.e0ipso.tokkl.timerstate.flatRevision
import com.github.e0ipso.tokkl.timerstate.timerTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

/**
 * The notification's Stop, Pause and Resume actions (docs/PRD.md N1, T1), for what the
 * notification showed, without opening the app. [TimerSurfaceActions] decides
 * each one. Stop of a running entry changes the phone and queues the change while the broadcast
 * lasts, so it works offline, and [RunningNotificationSync] shows the result; one the phone's storage
 * refuses says so in a toast ([runFromSurface]). Pause, Resume and
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
    private val failures: TimerFailureNotice by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_STOP -> TimerSurfaceAction.Stop
            ACTION_PAUSE -> TimerSurfaceAction.Pause
            ACTION_RESUME -> TimerSurfaceAction.Resume
            else -> return
        }
        val target = intent.timerTarget() ?: return
        if (action != TimerSurfaceAction.Stop || target !is TimerTarget.Running) {
            workManager.sendTimerAction(action, target)
            return
        }
        val pending = goAsync()
        scope.launch(io) {
            try {
                // A Stop the disk refused keeps the timer running, and the notification with it, and says so.
                surfaceActions.runFromSurface(action, target, failures)
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
    .putExtra(StopTimerReceiver.EXTRA_REVISION, target.flatRevision)
    .putExtra(StopTimerReceiver.EXTRA_ENTRY_ID, target.flatEntryId)

/**
 * The [TimerTarget] [putTarget] put here, or null for an intent without an account, as from a
 * notification posted by an older version, whose actions act on nothing.
 */
fun Intent.timerTarget(): TimerTarget? = timerTarget(
    getStringExtra(StopTimerReceiver.EXTRA_ACCOUNT),
    getLongExtra(StopTimerReceiver.EXTRA_REVISION, 0),
    getLongExtra(StopTimerReceiver.EXTRA_ENTRY_ID, 0),
)

/**
 * Says a failed timer action in a toast, which Android shows from the background too. The toast
 * goes up on [main], the main thread, from whichever thread calls [show].
 */
class ToastTimerFailureNotice(private val context: Context, private val main: CoroutineContext) : TimerFailureNotice {
    override suspend fun show(result: TimerActionResult) {
        if (result == TimerActionResult.Done) return
        val text = timerNoticeText(context.resources, TimerNotice.ActionFailed(result, 0))
        toast(text)
    }

    override suspend fun showNotStored() = toast(timerNotStoredText(context.resources))

    private suspend fun toast(text: String) {
        withContext(main) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }
}
