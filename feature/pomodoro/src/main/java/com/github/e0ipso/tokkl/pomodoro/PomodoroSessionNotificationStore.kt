package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.storage.PreferencesFile

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
data class PomodoroNotificationDisplay(val account: String, val shown: String? = null)

/** Persists only a random account identity and a content hash, never entry fields. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class PomodoroSessionNotificationStore(context: Context) {
    private val file = PreferencesFile(context, "pomodoro_notification")

    // Opens the file as the store is made, which starts loading it from disk.
    private val prefs = file.preferences

    fun load(): PomodoroNotificationDisplay? = prefs.getString("account", null)?.let {
        PomodoroNotificationDisplay(it, prefs.getString("shown", null))
    }

    fun save(display: PomodoroNotificationDisplay?) {
        file.commit("Could not save the Pomodoro notification state.") {
            clear()
            if (display != null) putString("account", display.account).putString("shown", display.shown)
        }
    }
}
