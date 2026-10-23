package com.github.e0ipso.tokkl.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import androidx.core.net.toUri
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.TokklApplication
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Separate alert channel; the ongoing notification and its Live Update are unchanged. */
class ReminderNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun post(timer: ReminderTimer) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled()) return
        manager.notify(ID, build(timer))
    }

    internal fun build(timer: ReminderTimer): Notification = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_nav_timer)
        .setContentTitle(context.getString(R.string.reminder_question))
        .setContentText(context.getString(R.string.reminder_summary))
        .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.reminder_question)))
        .setCategory(Notification.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(context, ID,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).putExtra(OPEN_TIMER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(Notification.Action.Builder(null, context.getString(R.string.reminder_keep_running), action(timer, "keep")).build())
        .addAction(Notification.Action.Builder(null, context.getString(R.string.notification_pause), action(timer, PAUSE)).build())
        .addAction(Notification.Action.Builder(null, context.getString(R.string.notification_stop), action(timer, STOP)).build())
        .build()

    private fun action(timer: ReminderTimer, kind: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderActionReceiver::class.java)
            .setData("tokkl-reminder://${timer.id}/${timer.start}/$kind".toUri())
            .putExtra(ReminderWork.ID, timer.id).putExtra(ReminderWork.START, timer.start)
            .putExtra(STOP, kind == STOP).putExtra(PAUSE, kind == PAUSE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun remove() = manager.cancel(ID)

    companion object {
        const val ID = 2
        const val CHANNEL = "long_timer_reminder"
        /** A consumed boolean extra on the normal launcher intent. */
        const val OPEN_TIMER = "com.github.e0ipso.tokkl.OPEN_TIMER_REMINDER"

        /** The actions' boolean extras, and the last part of their data URI. */
        internal const val STOP = "stop"
        internal const val PAUSE = "pause"
    }
}

/** Immutable notification actions work with the app closed and without a network. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as TokklApplication).reminder.act(
                    intent.getLongExtra(ReminderWork.ID, 0),
                    intent.getLongExtra(ReminderWork.START, 0),
                    stopTimer = intent.getBooleanExtra(ReminderNotifier.STOP, false),
                    pauseTimer = intent.getBooleanExtra(ReminderNotifier.PAUSE, false),
                )
            } catch (e: SQLiteException) {
                // The disk refused Stop or Pause. Keep the notification so the user can try again.
            } catch (e: IOException) {
                // Keep the notification if confirmation could not be stored.
            } finally {
                pending.finish()
            }
        }
    }
}
