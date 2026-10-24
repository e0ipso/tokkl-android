package com.github.e0ipso.tokkl.pomodoro

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.github.e0ipso.tokkl.R

/** A native countdown crosses zero into negative elapsed overtime without app ticks. */
internal class PomodoroSessionNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun post(content: PomodoroSessionNotificationContent, account: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.pomodoro_session_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.pomodoro_session_channel_description) },
            )
            if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                false
            } else {
                manager.notify(PomodoroSessionNotification.ID, build(content, account))
                true
            }
        } catch (_: SecurityException) {
            // The grant may have been revoked between checking permission and posting.
            false
        }
    }

    fun remove() { manager.cancel(PomodoroSessionNotification.ID) }

    internal fun build(content: PomodoroSessionNotificationContent, account: String): Notification {
        val phaseLabel = when (content.phase.kind) {
            "work" -> R.string.pomodoro_session_work
            "shortRest" -> R.string.pomodoro_session_short_rest
            else -> R.string.pomodoro_session_long_rest
        }
        val open = PendingIntent.getActivity(
            context, 0, pomodoroSessionOpenIntent(context, account, content.phase),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_pomodoro)
            .setContentTitle(context.getString(R.string.pomodoro_session_title, context.getString(phaseLabel), content.pomodoro))
            .setContentText(content.description.ifBlank { context.getString(R.string.pomodoro_session_no_description) })
            .setWhen(content.end.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(open)
            .addAction(action(R.drawable.ic_play, R.string.pomodoro_session_next, PomodoroSessionActionReceiver.NEXT, content, account))
            .addAction(action(R.drawable.ic_stop, R.string.pomodoro_session_end, PomodoroSessionActionReceiver.END, content, account))
            .build()
    }

    private fun action(icon: Int, label: Int, action: String, content: PomodoroSessionNotificationContent, account: String) =
        Notification.Action.Builder(
            Icon.createWithResource(context, icon), context.getString(label),
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, PomodoroSessionActionReceiver::class.java).setAction(action)
                    .putNotificationIdentity(account, content.phase),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    companion object { const val CHANNEL_ID = "pomodoro_session" }
}
