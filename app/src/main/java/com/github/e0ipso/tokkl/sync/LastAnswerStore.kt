package com.github.e0ipso.tokkl.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.IOException
import java.time.Instant

/**
 * [LastAnswer]'s time, in its own private preferences file. It stays on the phone, because the
 * backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class LastAnswerStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun load(): Instant? =
        preferences.getLong(KEY_ANSWERED, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli)

    /**
     * Keeps [answered], or forgets the time when it is null. A time is written in the background,
     * since every answer brings one and a lost write costs only an older time. Forgetting is
     * written at once, for a sign-out.
     */
    @Throws(IOException::class)
    fun save(answered: Instant?) {
        if (answered != null) {
            preferences.edit { putLong(KEY_ANSWERED, answered.toEpochMilli()) }
        } else {
            commit { remove(KEY_ANSWERED) }
        }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    private companion object {
        const val FILE_NAME = "last_answer"
        const val KEY_ANSWERED = "answered"
        const val NONE = Long.MIN_VALUE
    }
}
