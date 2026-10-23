package com.github.e0ipso.tokkl.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors

/**
 * Posts and removes the running timer's notification (docs/PRD.md N1). It is ongoing and shows
 * the description, the project and a chronometer that the system ticks from the entry's start,
 * after a pause chain's stored time (T1), so no foreground service runs and the app sets no alarm
 * to update it. Tapping it opens the app, and its Pause and Stop actions reach
 * [StopTimerReceiver] without opening the app. While paused it is quiet, can be swiped away and
 * asks for no promotion: it shows the paused total, which stands still, with Resume and Stop.
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

    internal fun build(
        content: RunningNotificationContent,
        requestPromotion: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && manager.canPostPromotedNotifications(),
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_timer)
            .setContentTitle(content.description.ifBlank { context.getString(R.string.notification_no_description) })
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
        if (content.paused) {
            return builder
                .setContentText(pausedText(content))
                .setShowWhen(false)
                .setOngoing(false)
                .addAction(action(R.drawable.ic_play, R.string.notification_resume, StopTimerReceiver.ACTION_RESUME, content))
                .addAction(action(R.drawable.ic_stop, R.string.notification_stop, StopTimerReceiver.ACTION_STOP, content))
                .build()
        }
        builder
            .setContentText(projectText(content))
            .setWhen(content.start.toEpochMilli() - content.accumulatedSeconds * MILLIS_PER_SECOND)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .addAction(action(R.drawable.ic_pause, R.string.notification_pause, StopTimerReceiver.ACTION_PAUSE, content))
            .addAction(action(R.drawable.ic_stop, R.string.notification_stop, StopTimerReceiver.ACTION_STOP, content))
        // Standard style, ongoing, a title and a non-minimal channel meet the promotion rules.
        // Keep the same notification when the user or the device declines promotion.
        // https://developer.android.com/develop/ui/views/notifications/live-update
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && requestPromotion) {
            builder.addExtras(Bundle().apply { putBoolean(REQUEST_PROMOTED_ONGOING, true) })
                .setColor(TokklLightExtraColors.primaryFill.toArgb())
        }
        return builder.build()
    }

    private fun projectText(content: RunningNotificationContent): String? {
        val project = content.project ?: return null
        val task = content.task ?: return project
        return context.getString(R.string.notification_project_task, project, task)
    }

    /** "Paused · 1:05:00", with the project and task when there is one. */
    private fun pausedText(content: RunningNotificationContent): String {
        val total = formatDuration(content.accumulatedSeconds)
        val project = projectText(content) ?: return context.getString(R.string.notification_paused, total)
        return context.getString(R.string.notification_paused_project, total, project)
    }

    /** Brings the app's task to the front as the launcher does, or starts it. */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(@DrawableRes icon: Int, @StringRes title: Int, action: String, content: RunningNotificationContent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(title), timerIntent(action, content.target)).build()

    /** Each action is its own PendingIntent, as its intent action tells them apart. */
    private fun timerIntent(action: String, target: TimerTarget): PendingIntent {
        val intent = Intent(context, StopTimerReceiver::class.java).setAction(action).putTarget(target)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** The id of the one notification the app posts. */
        const val NOTIFICATION_ID = 1

        const val CHANNEL_ID = "running_timer"

        private const val MILLIS_PER_SECOND = 1_000L

        // Notification.EXTRA_REQUEST_PROMOTED_ONGOING's documented key. The native setter was
        // added in 36.1; the extra also safely requests it on Android 16.0 and falls back there.
        internal const val REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}
