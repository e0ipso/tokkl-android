package com.github.e0ipso.tokkl.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.TokklApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The notification's Stop action (docs/PRD.md N1). It stops the entry the notification showed on
 * the phone and queues the change, as Stop in the app does, without opening the app or waiting for
 * the network. The queue sends the stop at once, or when the network returns. The cache then holds
 * no running entry, and [RunningNotificationSync] takes the notification away.
 *
 * Declared in the manifest and not exported: only the notification's own PendingIntent reaches it.
 */
class StopTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, NO_ENTRY)
        if (entryId == NO_ENTRY) return
        val changes = (context.applicationContext as TokklApplication).changes
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Not running any more, or not cached since a sign-out: nothing to stop.
                changes.stop(entryId)
            } catch (e: SQLiteException) {
                // The disk refused the change. The timer keeps running, and the notification with it.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.github.e0ipso.tokkl.extra.ENTRY_ID"

        /** No entry has this id: the server's are positive and the phone's negative. */
        private const val NO_ENTRY = 0L
    }
}
