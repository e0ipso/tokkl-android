package com.github.e0ipso.tokkl.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.cache.TimerTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The notification's Stop, Pause and Resume actions (docs/PRD.md N1, T1). Each acts on what the
 * notification showed, on the phone, and queues the change, as the app's buttons do, without
 * opening the app or waiting for the network. The queue sends it at once, or when the network
 * returns, and [RunningNotificationSync] shows the result. An action shown for a state the phone
 * no longer holds, such as a second tap or a tap after sign-out, does nothing.
 *
 * Declared in the manifest and not exported: only the notification's own PendingIntents reach it.
 */
class StopTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val target = intent.timerTarget()
        if (target.session == NO_SESSION && target.entryId == NO_ENTRY) return
        val changes = (context.applicationContext as TokklApplication).changes
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> changes.pause(target)
                    ACTION_RESUME -> changes.resume(target)
                    // A notification posted before Pause existed carries the entry alone.
                    else -> if (target.session == NO_SESSION) changes.stop(target.entryId) else changes.stop(target)
                }
            } catch (e: SQLiteException) {
                // The disk refused the change. The timer keeps running, and the notification with it.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.github.e0ipso.tokkl.extra.ENTRY_ID"
        const val EXTRA_SESSION = "com.github.e0ipso.tokkl.extra.TIMER_SESSION"
        const val EXTRA_CHAIN = "com.github.e0ipso.tokkl.extra.PAUSE_CHAIN"
        const val EXTRA_REVISION = "com.github.e0ipso.tokkl.extra.PAUSE_REVISION"

        const val ACTION_STOP = "com.github.e0ipso.tokkl.action.STOP_TIMER"
        const val ACTION_PAUSE = "com.github.e0ipso.tokkl.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.github.e0ipso.tokkl.action.RESUME_TIMER"

        /** No entry has this id: the server's are positive and the phone's negative. */
        private const val NO_ENTRY = 0L

        /** No session has this id, as with a notification posted before sessions existed. */
        private const val NO_SESSION = 0L
    }
}

/** Puts [target] in this intent's extras, for [timerTarget] to read back. */
fun Intent.putTarget(target: TimerTarget): Intent =
    putExtra(StopTimerReceiver.EXTRA_SESSION, target.session)
        .putExtra(StopTimerReceiver.EXTRA_CHAIN, target.chainId)
        .putExtra(StopTimerReceiver.EXTRA_REVISION, target.revision)
        .putExtra(StopTimerReceiver.EXTRA_ENTRY_ID, target.entryId)

/** The [TimerTarget] [putTarget] put here, with zeros for what is missing. */
fun Intent.timerTarget(): TimerTarget = TimerTarget(
    session = getLongExtra(StopTimerReceiver.EXTRA_SESSION, 0),
    chainId = getLongExtra(StopTimerReceiver.EXTRA_CHAIN, 0),
    revision = getLongExtra(StopTimerReceiver.EXTRA_REVISION, 0),
    entryId = getLongExtra(StopTimerReceiver.EXTRA_ENTRY_ID, 0),
)
