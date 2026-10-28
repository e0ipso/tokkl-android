package com.github.e0ipso.tokkl.timerstate

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.notification.RunningNotifier
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmActivity
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmViewModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroRingingNotifier
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotifier

/**
 * The app's timer surfaces outside its pages, as TimerCrossDeviceServerTest and
 * TimerActionsOfflineServerTest read and tap them: the running notification, the Pomodoro session
 * notification in its active and ringing forms, and the full-screen alarm.
 */
internal object TimerSurfaces {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    private val OPEN_STAGES = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)

    /**
     * Waits for the running timer's notification, running or paused as [ongoing] says: the one
     * with its id on its channel that is no group summary. A timeout names every notification
     * with that id.
     */
    fun runningNotification(ongoing: Boolean): Notification {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (true) {
            val withId = notifications.activeNotifications.filter { it.id == RunningNotifier.NOTIFICATION_ID }
            withId.singleOrNull {
                it.notification.channelId == RunningNotifier.CHANNEL_ID &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    (it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) == ongoing
            }?.let { return it.notification }
            if (SystemClock.elapsedRealtime() > deadline) {
                throw AssertionError("no running notification with ongoing=$ongoing: " + withId.joinToString {
                    "${it.key} tag=${it.tag} flags=${it.notification.flags} channel=${it.notification.channelId} " +
                        "group=${it.notification.group} actions=${it.notification.actions?.map { action -> action.title }}"
                })
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    /** The session notification in its active form, or null. */
    fun sessionNotification(): StatusBarNotification? =
        notifications.activeNotifications.singleOrNull { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
            ?.takeIf { it.notification.channelId == PomodoroSessionNotifier.CHANNEL_ID }

    /** The session notification in its ringing form, or null. */
    fun ringingNotification(): Notification? =
        notifications.activeNotifications.singleOrNull { it.id == PomodoroSessionNotifier.NOTIFICATION_ID }
            ?.notification?.takeIf { it.channelId == PomodoroRingingNotifier.CHANNEL_ID }

    /** Whether no full-screen alarm is open, or each one left is finishing. */
    fun noOpenAlarm(): Boolean = openAlarms().all { it.isFinishing }

    /** Waits until a full-screen alarm is open and not finishing, and returns its model. */
    fun awaitAlarm(): PomodoroAlarmModel {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (true) {
            val open = openAlarms().singleOrNull { !it.isFinishing }
            if (open != null) {
                var model: PomodoroAlarmModel? = null
                instrumentation.runOnMainSync { model = ViewModelProvider(open)[PomodoroAlarmViewModel::class.java].model }
                return checkNotNull(model)
            }
            if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("no full-screen alarm opened")
            SystemClock.sleep(POLL_MS)
        }
    }

    fun closeAlarms() {
        instrumentation.runOnMainSync { openAlarmsOnMain().forEach(Activity::finish) }
    }

    /**
     * A node of the full-screen alarm. The ringing notification's heads-up shows buttons with the
     * same labels, and UiAutomator reports its views under the app's package, so the alarm's own
     * Compose tree is the one place a tap surely lands on the alarm.
     */
    fun onTheAlarm(matcher: SemanticsMatcher): SemanticsMatcher =
        matcher and hasAnyAncestor(hasAnyDescendant(hasText(context.getString(R.string.pomodoro_alarm_title))))

    private fun openAlarms(): List<PomodoroAlarmActivity> {
        var open = emptyList<PomodoroAlarmActivity>()
        instrumentation.runOnMainSync { open = openAlarmsOnMain() }
        return open
    }

    private fun openAlarmsOnMain(): List<PomodoroAlarmActivity> {
        val registry = ActivityLifecycleMonitorRegistry.getInstance()
        return OPEN_STAGES.flatMap { registry.getActivitiesInStage(it) }.filterIsInstance<PomodoroAlarmActivity>()
    }

    private const val TIMEOUT_MS = 10_000L
    private const val POLL_MS = 50L
}
