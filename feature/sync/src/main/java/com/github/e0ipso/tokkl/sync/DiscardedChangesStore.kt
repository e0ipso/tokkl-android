package com.github.e0ipso.tokkl.sync

import android.content.Context
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException

/**
 * [DiscardedChanges]'s count, in its own private preferences file. It stays on the phone, because
 * the backup and data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class DiscardedChangesStore(context: Context) : ClearableStore<Int> {

    private val file = PreferencesFile(context, FILE_NAME)

    override suspend fun load(): Int = file.read { getInt(KEY_COUNT, 0) }

    /** Written in the background: a lost write only shows the notice once more, or not at all. */
    override suspend fun save(value: Int) {
        file.write { if (value > 0) putInt(KEY_COUNT, value) else remove(KEY_COUNT) }
    }

    /** Forgets the count at once, for a sign-out. */
    @Throws(IOException::class)
    override suspend fun clear() {
        file.commit { clear() }
    }

    private companion object {
        const val FILE_NAME = "discarded_changes"
        const val KEY_COUNT = "count"
    }
}
