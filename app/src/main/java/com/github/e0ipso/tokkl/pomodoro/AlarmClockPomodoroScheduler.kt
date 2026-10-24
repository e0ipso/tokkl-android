package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.TokklApplication
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Arms one [AlarmManager.setAlarmClock] at a phase's planned end. That is the exact alarm the
 * platform documents for a user-facing alarm: it leaves idle mode to deliver it and shows the
 * alarm icon. Tapping the icon opens the app.
 *
 * API 33 and later grant `USE_EXACT_ALARM` at install and the user cannot revoke it, so
 * [permissionDenied] stays false there. API 31 and 32 use the revocable `SCHEDULE_EXACT_ALARM`
 * permission, and [permissionDenied] follows [AlarmManager.canScheduleExactAlarms]. The Pomodoro
 * page reads [permissionDenied].
 *
 * [watchSession] keeps [PomodoroAlarmRearmReceiver] enabled only while a session runs. The
 * manifest leaves that receiver disabled, so a reboot with no session does not start the app.
 */
class AlarmClockPomodoroScheduler(private val context: Context) : PomodoroAlarmScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override val permissionDenied: Boolean
        get() = !manager.canScheduleExactAlarms()

    override fun schedule(triggerAt: Instant): Boolean {
        if (permissionDenied) return false
        val pending = operation(create = true) ?: return false
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showPhase()), pending)
            true
        } catch (e: SecurityException) {
            // Revoked between the check and the call. The page reads permissionDenied.
            false
        }
    }

    override fun cancel() {
        val pending = operation(create = false) ?: return
        manager.cancel(pending)
    }

    override fun watchSession(running: Boolean) {
        val state = if (running) {
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
    internal fun operation(create: Boolean): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        val intent = Intent(context, PomodoroPhaseAlarmReceiver::class.java)
            .setAction(PomodoroPhaseAlarmReceiver.ACTION_PHASE_END)
        return PendingIntent.getBroadcast(context, OPERATION_REQUEST, intent, flags)
    }

    private fun showPhase(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_SHOW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
 * The phase alarm. Not exported: only its own [PendingIntent] reaches it. It records that the
 * phase reached its planned end. It does not start the next phase. TKA-0250 starts the ringing
 * service from that record.
 */
class PomodoroPhaseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PHASE_END) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as TokklApplication).pomodoroSessions.onPhaseAlarm()
            } catch (e: SQLiteException) {
                // The disk refused the read. The phase stays where it is.
            } catch (e: IOException) {
                // The record of the end could not be stored. The phase stays where it is.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PHASE_END = "com.github.e0ipso.tokkl.action.POMODORO_PHASE_END"
    }
}

/**
 * Arms the phase alarm again from the stored session after boot, an app update, a clock or
 * timezone change, or a grant of the exact-alarm permission. A planned end that has already
 * passed rings at once and leaves the phase in overtime.
 *
 * Declared disabled. [AlarmClockPomodoroScheduler.watchSession] enables it while a session runs.
 * The system sends these protected broadcasts, so the receiver is exported.
 */
class PomodoroAlarmRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val done = completed
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as TokklApplication).pomodoroSessions.rearm()
            } catch (e: SQLiteException) {
                // The disk refused the session. A later boot, update or clock change tries again.
            } catch (e: IOException) {
                // The phase-end record could not be stored. The session row is unchanged.
            } finally {
                // A direct receiver invocation in the boot test has no active broadcast result.
                pending?.finish()
                done?.countDown()
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

        /** The boot test waits on this. Production leaves it null. */
        @Volatile
        internal var completed: CountDownLatch? = null
    }
}
