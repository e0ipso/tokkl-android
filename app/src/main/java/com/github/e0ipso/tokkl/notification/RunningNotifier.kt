package com.github.e0ipso.tokkl.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R

/**
 * Posts and removes the running timer's notification (docs/PRD.md N1). It is ongoing and shows
 * the description, the project and a chronometer that the system ticks from the entry's start,
 * so no foreground service runs and the app sets no alarm to update it. Tapping it opens the
 * app, and its Stop action reaches [StopTimerReceiver] without opening the app.
 *
 * On Android 13 and later it shows only once the user has allowed notifications (TKA-0058 asks).
 * Without that, [post] does nothing.
 */
class RunningNotifier(context: Context) {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    fun post(content: RunningNotificationContent) {
        manager.createNotificationChannel(channel())
        manager.notify(NOTIFICATION_ID, build(content))
    }

    fun remove() {
        manager.cancel(NOTIFICATION_ID)
    }

    /** Low importance: it makes no sound and does not pop up over the screen. */
    private fun channel(): NotificationChannel =
        NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_running), NotificationManager.IMPORTANCE_LOW)
            .apply { description = context.getString(R.string.notification_channel_running_description) }

    private fun build(content: RunningNotificationContent): Notification {
        val stop = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_stop),
            context.getString(R.string.notification_stop),
            stopIntent(content.entryId),
        ).build()
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_timer)
            .setContentTitle(content.description.ifBlank { context.getString(R.string.notification_no_description) })
            .setContentText(projectText(content))
            .setWhen(content.start.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openApp())
            .addAction(stop)
            .build()
    }

    private fun projectText(content: RunningNotificationContent): String? {
        val project = content.project ?: return null
        val task = content.task ?: return project
        return context.getString(R.string.notification_project_task, project, task)
    }

    /** Brings the app's task to the front as the launcher does, or starts it. */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(entryId: Long): PendingIntent {
        val intent = Intent(context, StopTimerReceiver::class.java).putExtra(StopTimerReceiver.EXTRA_ENTRY_ID, entryId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** The id of the one notification the app posts. */
        const val NOTIFICATION_ID = 1

        const val CHANNEL_ID = "running_timer"
    }
}
