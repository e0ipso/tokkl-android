package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * The Pomodoro defaults in their own private preferences file, so a cold start has the last
 * values before the refresh answers, and offline. It stays on the phone, because the backup and
 * data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class PomodoroDefaultsStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored values, or null when none are stored or one of them is missing. */
    fun load(): StoredPomodoroDefaults? {
        if (KEYS.any { !preferences.contains(it) }) return null
        val defaults = PomodoroDefaults(
            workMinutes = preferences.getInt(KEY_WORK_MINUTES, 0),
            shortRestMinutes = preferences.getInt(KEY_SHORT_REST_MINUTES, 0),
            longRestMinutes = preferences.getInt(KEY_LONG_REST_MINUTES, 0),
            longRestEvery = preferences.getInt(KEY_LONG_REST_EVERY, 0),
        )
        return StoredPomodoroDefaults(defaults.clamped(), unsent = preferences.getBoolean(KEY_UNSENT, false))
    }

    /** Replaces the stored values in one write. */
    @Throws(IOException::class)
    fun save(stored: StoredPomodoroDefaults) {
        val defaults = stored.defaults.clamped()
        commit {
            putInt(KEY_WORK_MINUTES, defaults.workMinutes)
            putInt(KEY_SHORT_REST_MINUTES, defaults.shortRestMinutes)
            putInt(KEY_LONG_REST_MINUTES, defaults.longRestMinutes)
            putInt(KEY_LONG_REST_EVERY, defaults.longRestEvery)
            putBoolean(KEY_UNSENT, stored.unsent)
        }
    }

    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    private companion object {
        const val FILE_NAME = "pomodoro_defaults"
        const val KEY_WORK_MINUTES = "work_minutes"
        const val KEY_SHORT_REST_MINUTES = "short_rest_minutes"
        const val KEY_LONG_REST_MINUTES = "long_rest_minutes"
        const val KEY_LONG_REST_EVERY = "long_rest_every"
        const val KEY_UNSENT = "unsent"
        val KEYS = listOf(KEY_WORK_MINUTES, KEY_SHORT_REST_MINUTES, KEY_LONG_REST_MINUTES, KEY_LONG_REST_EVERY, KEY_UNSENT)
    }
}
