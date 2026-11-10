package com.github.e0ipso.tokkl.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.github.e0ipso.tokkl.feature.pomodoro.R
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The player owns sound/vibration; the high-importance channel adds neither a second sound nor haptics. */
class PomodoroRingingNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pomodoro_alarm_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
                .apply {
                    description = context.getString(R.string.pomodoro_alarm_channel_description)
                    setSound(null, pomodoroAlarmAudioAttributes())
                    enableVibration(false)
                },
        )
    }

    /**
     * While the service still checks the held state, [ring] is null and the notification has no
     * actions. It still opens the alarm of [phase], the phase the alarm delivered, over the lock
     * screen: Android launches a full-screen intent only when the notification is first posted,
     * and the alarm screen shows nothing for a phase that is not the held one. A sticky restart
     * knows no phase and opens nothing.
     */
    fun build(ring: PomodoroRing?, phase: PomodoroPhaseKey? = ring?.phase): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            mainActivityIntent(context).setAction("com.github.e0ipso.tokkl.action.SHOW_POMODORO")
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(UiR.drawable.ic_nav_timer)
            .setContentTitle(context.getString(R.string.pomodoro_alarm_title))
            .setContentText(context.getString(UiR.string.pomodoro_alarm_waiting))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
        if (phase != null) {
            val alarm = pomodoroAlarmPendingIntent(context, phase)
            builder.setContentIntent(alarm).setFullScreenIntent(alarm, true)
        }
        if (ring != null) {
            builder
                .setContentText(context.getString(alarmTransitionLabel(ring.sound)))
                .setSubText(context.getString(UiR.string.pomodoro_number, ring.number, ring.longRestEvery))
                // The native clock counts the overtime up from the planned end, with no app ticks.
                .setWhen(ring.end.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setDeleteIntent(actionIntent(PomodoroRingingActionReceiver.REPOST, ring))
                .addAction(
                    action(
                        UiR.drawable.ic_play,
                        R.string.pomodoro_alarm_next,
                        PomodoroRingingActionReceiver.NEXT,
                        ring,
                    ),
                )
                .addAction(
                    action(UiR.drawable.ic_stop, R.string.pomodoro_alarm_end, PomodoroRingingActionReceiver.END, ring),
                )
        }
        return builder.build()
    }

    private fun action(icon: Int, title: Int, action: String, ring: PomodoroRing): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, icon),
            context.getString(title),
            actionIntent(action, ring),
        ).build()

    private fun actionIntent(action: String, ring: PomodoroRing): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, PomodoroRingingActionReceiver::class.java).setAction(action)
            .putPhase(ring.phase, revision = ring.revision, unique = true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "pomodoro_alarm"
    }
}

/** An immutable intent for one phase; re-posting cannot retarget an older alarm's actions. */
fun pomodoroAlarmIntent(context: Context, phase: PomodoroPhaseKey): Intent =
    Intent(context, PomodoroAlarmActivity::class.java)
        .setAction("com.github.e0ipso.tokkl.action.SHOW_POMODORO_ALARM")
        .putPhase(phase, unique = true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

fun pomodoroAlarmPendingIntent(context: Context, phase: PomodoroPhaseKey): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    pomodoroAlarmIntent(context, phase),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
