package com.github.e0ipso.tokkl.pomodoro

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Special app access, not a runtime permission. F-Droid/other installers may set its grant. */
internal fun canShowPomodoroFullScreen(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    return fullScreenAllowed(Build.VERSION.SDK_INT) {
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
}

internal fun fullScreenAllowed(sdk: Int, granted: () -> Boolean): Boolean = sdk < 34 || granted()

/** API 31..33 have no full-screen special-access page. Never send that action there. */
internal fun pomodoroFullScreenSettings(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= 34) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else null
