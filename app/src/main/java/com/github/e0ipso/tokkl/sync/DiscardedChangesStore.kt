package com.github.e0ipso.tokkl.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.IOException

/**
 * [DiscardedChanges]'s count, in its own private preferences file. It stays on the phone, because
 * the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class DiscardedChangesStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun load(): Int = preferences.getInt(KEY_COUNT, 0)

    /** Written in the background: a lost write only shows the notice once more, or not at all. */
    fun save(count: Int) {
        preferences.edit { if (count > 0) putInt(KEY_COUNT, count) else remove(KEY_COUNT) }
    }

    /** Forgets the count at once, for a sign-out. */
    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    private companion object {
        const val FILE_NAME = "discarded_changes"
        const val KEY_COUNT = "count"
    }
}
