package com.github.e0ipso.tokkl.sync

import android.content.Context
import androidx.core.content.edit
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException

/**
 * [DiscardedChanges]'s count, in its own private preferences file. It stays on the phone, because
 * the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class DiscardedChangesStore(context: Context) : ClearableStore<Int> {

    private val file = PreferencesFile(context, FILE_NAME)
    private val preferences get() = file.preferences

    override fun load(): Int = preferences.getInt(KEY_COUNT, 0)

    /** Written in the background: a lost write only shows the notice once more, or not at all. */
    override fun save(value: Int) {
        preferences.edit { if (value > 0) putInt(KEY_COUNT, value) else remove(KEY_COUNT) }
    }

    /** Forgets the count at once, for a sign-out. */
    @Throws(IOException::class)
    override fun clear() {
        file.commit { clear() }
    }

    private companion object {
        const val FILE_NAME = "discarded_changes"
        const val KEY_COUNT = "count"
    }
}
