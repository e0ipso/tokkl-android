package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import com.github.e0ipso.tokkl.storage.PreferencesFile
import kotlin.coroutines.CoroutineContext

/** What the session notification showed: the random identity of its [account] and the hash of what it [shown]. */
data class PomodoroNotificationDisplay(val account: String, val shown: String? = null)

/** Persists only a random account identity and a content hash, never entry fields. */
class PomodoroSessionNotificationStore(context: Context, private val io: CoroutineContext) :
    PomodoroNotificationMemory {
    private val file = PreferencesFile(context, "pomodoro_notification", io)

    override suspend fun load(): PomodoroNotificationDisplay? = file.read {
        getString("account", null)?.let { PomodoroNotificationDisplay(it, getString("shown", null)) }
    }

    override suspend fun save(display: PomodoroNotificationDisplay?) {
        file.commit("Could not save the Pomodoro notification state.") {
            clear()
            if (display != null) putString("account", display.account).putString("shown", display.shown)
        }
    }
}
