package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.IOException

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
data class PomodoroNotificationDisplay(val account: String, val shown: String? = null)

/** Persists only a random account identity and a content hash, never entry fields. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class PomodoroSessionNotificationStore(context: Context) {
    private val prefs = context.getSharedPreferences("pomodoro_notification", Context.MODE_PRIVATE)

    fun load(): PomodoroNotificationDisplay? = prefs.getString("account", null)?.let {
        PomodoroNotificationDisplay(it, prefs.getString("shown", null))
    }

    fun save(display: PomodoroNotificationDisplay?) {
        val edit = prefs.edit().clear()
        if (display != null) edit.putString("account", display.account).putString("shown", display.shown)
        if (!edit.commit()) throw IOException("Could not save the Pomodoro notification state.")
    }
}
