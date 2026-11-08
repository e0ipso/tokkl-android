package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.platform.mainActivityIntent
import com.github.e0ipso.tokkl.timerstate.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.PomodoroWidgetRedraw
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower
import com.github.e0ipso.tokkl.timersync.TimerSyncWatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.time.Instant

/**
 * Arms one [AlarmManager.setAlarmClock] at a phase's planned end. That is the exact alarm the
 * platform documents for a user-facing alarm: it leaves idle mode to deliver it and shows the
 * alarm icon. Tapping the icon opens the app.
 *
 * API 33 and later grant `USE_EXACT_ALARM` at install and the user cannot revoke it, so
 * [permissionDenied] stays false there. API 31 and 32 use the revocable `SCHEDULE_EXACT_ALARM`
 * permission, and [permissionDenied] follows [AlarmManager.canScheduleExactAlarms]. The Pomodoro
 * page reads [permissionDenied]. When access is denied, one inexact alarm redraws the widget
 * after the planned end as best-effort process recovery; it never starts the ringing service.
 *
 * [watchTimerSync] keeps [PomodoroAlarmRearmReceiver] enabled only while [timerSyncStarts] says
 * the account's server has the timer API. Only such a server has sessions, and its timer sync
 * service starts after boot and updates. The manifest leaves that receiver disabled, so a reboot
 * without it does not start the app.
 *
 * @param timerSyncStarts whether the account's server has the timer API, so the boot and update
 *   broadcasts must start the timer sync service and arm the held phase again. Read on every
 *   change, off the main thread.
 * @param exactAlarms whether Android refuses exact alarms now.
 */
class AlarmClockPomodoroScheduler(
    private val context: Context,
    private val timerSyncStarts: suspend () -> Boolean,
    private val exactAlarms: () -> Boolean,
) : PomodoroAlarmScheduler,
    TimerSyncWatcher {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override val permissionDenied: Boolean
        get() = exactAlarms()

    override fun schedule(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean {
        if (permissionDenied) return scheduleRedraw(triggerAt, phase)
        val pending = operation(create = true, phase) ?: return false
        return try {
            operation(create = false, redraw = true)?.let(manager::cancel)
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showPhase()), pending)
            true
        } catch (_: SecurityException) {
            // Revoked between the check and the call. The page reads permissionDenied.
            scheduleRedraw(triggerAt, phase)
        }
    }

    /**
     * Best-effort recovery if the live sync process dies without exact-alarm access. This
     * one-shot alarm only redraws the widget; it never starts audio or a foreground service.
     * Android may delay it. The permission warning remains, and false still means no exact
     * ringing alarm was armed. No session means cancel() removes this alarm too.
     */
    private fun scheduleRedraw(triggerAt: Instant, phase: PomodoroPhaseKey): Boolean {
        operation(create = false)?.let(manager::cancel)
        operation(create = true, phase, redraw = true)?.let { pending ->
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pending)
        }
        return false
    }

    override fun cancel() {
        operation(create = false)?.let(manager::cancel)
        operation(create = false, redraw = true)?.let(manager::cancel)
    }

    /** Applies a change of [timerSyncStarts] to the receiver, one change at a time. */
    override suspend fun watchTimerSync() {
        watching.withLock { applyRearmReceiver() }
    }

    private val watching = Mutex()

    private suspend fun applyRearmReceiver() {
        val state = if (timerSyncStarts()) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, PomodoroAlarmRearmReceiver::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** The broadcast the alarm delivers. [create] false returns null when the intent is not armed. */
    private fun operation(create: Boolean, phase: PomodoroPhaseKey? = null, redraw: Boolean = false): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        val intent = Intent(context, PomodoroPhaseAlarmReceiver::class.java)
            .setAction(
                if (redraw) {
                    PomodoroPhaseAlarmReceiver.ACTION_WIDGET_REDRAW
                } else {
                    PomodoroPhaseAlarmReceiver.ACTION_PHASE_END
                },
            )
        if (phase != null) intent.putPhase(phase)
        return PendingIntent.getBroadcast(context, OPERATION_REQUEST, intent, flags)
    }

    private fun showPhase(): PendingIntent {
        val intent = mainActivityIntent(context)
            .setAction(ACTION_SHOW)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        return PendingIntent.getActivity(
            context,
            SHOW_REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        /** Distinct from the launcher intent, so the alarm icon does not replace the notification's tap. */
        const val ACTION_SHOW = "com.github.e0ipso.tokkl.action.SHOW_POMODORO_ALARM"
        const val OPERATION_REQUEST = 1
        const val SHOW_REQUEST = 2
    }
}

/**
 * The phase alarm. Not exported: only its own [PendingIntent] reaches it. While the held session
 * is still in the phase it was armed for, it starts that phase's ringing service, which an alarm
 * delivery may start from the background. It does not start the next phase.
 */
class PomodoroPhaseAlarmReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PHASE_END && intent.action != ACTION_WIDGET_REDRAW) return
        val phase = intent.phaseKey() ?: return
        val pending = goAsync()
        scope.launch(io) {
            try {
                if (intent.action == ACTION_PHASE_END) {
                    get<PomodoroPhaseAlarm>().onAlarm(phase)
                } else {
                    get<PomodoroWidgetRedraw>().redraw(context, phase)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PHASE_END = "com.github.e0ipso.tokkl.action.POMODORO_PHASE_END"
        const val ACTION_WIDGET_REDRAW = "com.github.e0ipso.tokkl.action.POMODORO_WIDGET_REDRAW"
    }
}

/**
 * Arms the phase alarm again from the stored timer state after boot, an app update, a clock or
 * timezone change, or a grant of the exact-alarm permission, with no request to the server. It
 * hands each of these broadcasts to [PomodoroAlarmRearm].
 *
 * Declared disabled. [AlarmClockPomodoroScheduler] enables it while the account's server has the
 * timer API. The system sends these protected broadcasts, so the receiver is exported.
 */
class PomodoroAlarmRearmReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val rearm: PomodoroAlarmRearm by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null || action !in ACTIONS) return
        val pending = goAsync()
        scope.launch(io) {
            try {
                rearm.rearm(action)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/**
 * What [PomodoroAlarmRearmReceiver] does with each of its broadcasts. It arms the held phase's
 * alarm again from the stored timer state, even where this process armed it already. A planned
 * end that has already passed is armed in the past, so the alarm rings at once and the phase stays
 * in overtime: API 35 and later forbid starting the ringing service from BOOT_COMPLETED, and an
 * alarm delivery may. After boot and an app update it also starts the timer sync service, which
 * those two broadcasts allow from the background. A start the platform refuses anyway is dropped:
 * the next app open starts it, and the armed alarm still rings.
 */
class PomodoroAlarmRearm(private val alarm: PomodoroPhaseAlarm, private val timerSync: TimerSyncFollower) {

    /** Arms the held phase again after the broadcast [action]. */
    suspend fun rearm(action: String) {
        alarm.follow(rearm = true)
        if (action in TIMER_SYNC_STARTS) timerSync.follow()
    }

    private companion object {
        /** The broadcasts that may start a foreground service from the background. */
        val TIMER_SYNC_STARTS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
