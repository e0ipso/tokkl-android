package com.github.e0ipso.tokkl.sync

import android.content.Context
import androidx.core.content.edit
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import java.io.IOException
import java.time.Instant

/**
 * [LastAnswer]'s time, in its own private preferences file. It stays on the phone, because the
 * backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class LastAnswerStore(context: Context) : ValueStore<Instant?> {

    private val file = PreferencesFile(context, FILE_NAME)
    private val preferences get() = file.preferences

    override fun load(): Instant? =
        preferences.getLong(KEY_ANSWERED, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli)

    /**
     * Keeps [value], or forgets the time when it is null. A time is written in the background,
     * since every answer brings one and a lost write costs only an older time. Forgetting is
     * written at once, for a sign-out.
     */
    @Throws(IOException::class)
    override fun save(value: Instant?) {
        if (value != null) {
            preferences.edit { putLong(KEY_ANSWERED, value.toEpochMilli()) }
        } else {
            file.commit { remove(KEY_ANSWERED) }
        }
    }

    private companion object {
        const val FILE_NAME = "last_answer"
        const val KEY_ANSWERED = "answered"
        const val NONE = Long.MIN_VALUE
    }
}
