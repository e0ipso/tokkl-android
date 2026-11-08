package com.github.e0ipso.tokkl.sync

import android.content.Context
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import java.io.IOException
import java.time.Instant

/**
 * [LastAnswer]'s time, in its own private preferences file. It stays on the phone, because the
 * backup and data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class LastAnswerStore(context: Context) : ValueStore<Instant?> {

    private val file = PreferencesFile(context, FILE_NAME)

    override suspend fun load(): Instant? =
        file.read { getLong(KEY_ANSWERED, NONE) }.takeIf { it != NONE }?.let(Instant::ofEpochMilli)

    /**
     * Keeps [value], or forgets the time when it is null. A time is written in the background,
     * since every answer brings one and a lost write costs only an older time. Forgetting is
     * written at once, for a sign-out.
     */
    @Throws(IOException::class)
    override suspend fun save(value: Instant?) {
        if (value != null) {
            file.write { putLong(KEY_ANSWERED, value.toEpochMilli()) }
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
