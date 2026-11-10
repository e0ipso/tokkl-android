package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.preferences.StoredPomodoroDefaults
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The Pomodoro defaults in their own private preferences file, so a cold start has the last
 * values before the refresh answers, and offline. It stays on the phone, because the backup and
 * data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class PomodoroDefaultsStore(context: Context, private val io: CoroutineContext) :
    ClearableStore<StoredPomodoroDefaults> {

    private val file = PreferencesFile(context, FILE_NAME, io)

    /** The stored values, or null when none are stored or one of them is missing. */
    override suspend fun load(): StoredPomodoroDefaults? = file.read {
        if (KEYS.any { !contains(it) }) return@read null
        val defaults = PomodoroDefaults(
            workMinutes = getInt(KEY_WORK_MINUTES, 0),
            shortRestMinutes = getInt(KEY_SHORT_REST_MINUTES, 0),
            longRestMinutes = getInt(KEY_LONG_REST_MINUTES, 0),
            longRestEvery = getInt(KEY_LONG_REST_EVERY, 0),
        )
        StoredPomodoroDefaults(defaults.clamped(), unsent = getBoolean(KEY_UNSENT, false))
    }

    /** Replaces the stored values in one write. */
    @Throws(IOException::class)
    override suspend fun save(value: StoredPomodoroDefaults) {
        val defaults = value.value.clamped()
        file.commit {
            putInt(KEY_WORK_MINUTES, defaults.workMinutes)
            putInt(KEY_SHORT_REST_MINUTES, defaults.shortRestMinutes)
            putInt(KEY_LONG_REST_MINUTES, defaults.longRestMinutes)
            putInt(KEY_LONG_REST_EVERY, defaults.longRestEvery)
            putBoolean(KEY_UNSENT, value.unsent)
        }
    }

    @Throws(IOException::class)
    override suspend fun clear() {
        file.commit { clear() }
    }

    private companion object {
        const val FILE_NAME = "pomodoro_defaults"
        const val KEY_WORK_MINUTES = "work_minutes"
        const val KEY_SHORT_REST_MINUTES = "short_rest_minutes"
        const val KEY_LONG_REST_MINUTES = "long_rest_minutes"
        const val KEY_LONG_REST_EVERY = "long_rest_every"
        const val KEY_UNSENT = "unsent"
        val KEYS = listOf(
            KEY_WORK_MINUTES,
            KEY_SHORT_REST_MINUTES,
            KEY_LONG_REST_MINUTES,
            KEY_LONG_REST_EVERY,
            KEY_UNSENT,
        )
    }
}
