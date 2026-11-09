package com.github.e0ipso.tokkl.pomodoro

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Current platform grants, independent of whether an alarm has already tried to arm. */
internal data class PomodoroPermissions(val notifications: Boolean, val exactAlarm: Boolean, val fullScreen: Boolean)

internal fun pomodoroPermissions(context: Context): PomodoroPermissions {
    val notifications = context.getSystemService(NotificationManager::class.java)
    return pomodoroPermissions(
        sdk = Build.VERSION.SDK_INT,
        notificationRuntime = {
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        },
        notificationsEnabled = notifications::areNotificationsEnabled,
        exactAlarm = { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
        fullScreen = { canShowPomodoroFullScreen(context) },
    )
}

internal fun pomodoroPermissions(
    sdk: Int,
    notificationRuntime: () -> Boolean,
    notificationsEnabled: () -> Boolean,
    exactAlarm: () -> Boolean,
    fullScreen: () -> Boolean,
) = PomodoroPermissions(
    notifications = (sdk < 33 || notificationRuntime()) && notificationsEnabled(),
    exactAlarm = sdk < 31 || exactAlarm(),
    fullScreen = fullScreenAllowed(sdk, fullScreen),
)

internal fun pomodoroExactAlarmSettings(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

/** Special app access, not a runtime permission. F-Droid/other installers may set its grant. */
fun canShowPomodoroFullScreen(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    return fullScreenAllowed(Build.VERSION.SDK_INT) {
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
}

internal fun fullScreenAllowed(sdk: Int, granted: () -> Boolean): Boolean =
    sdk < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || granted()

/** API 31..33 have no full-screen special-access page. Never send that action there. */
fun pomodoroFullScreenSettings(context: Context): Intent? = if (Build.VERSION.SDK_INT >=
    Build.VERSION_CODES.UPSIDE_DOWN_CAKE
) {
    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
} else {
    null
}
