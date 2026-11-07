package com.github.e0ipso.tokkl.notification

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.storage.ValueStore
import java.io.IOException

/**
 * The running entry the notification last followed, its key and id, in its own private
 * preferences file. The key is a hash, so the file holds no description or name. It stays on the
 * phone, because the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class RunningNotificationStore(context: Context) : ValueStore<FollowedEntry?> {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    override fun load(): FollowedEntry? {
        val key = preferences.getString(KEY_SHOWN, null) ?: return null
        val entryId = if (preferences.contains(KEY_SHOWN_ENTRY)) preferences.getLong(KEY_SHOWN_ENTRY, 0) else null
        return FollowedEntry(key, entryId)
    }

    @Throws(IOException::class)
    override fun save(value: FollowedEntry?) {
        commit {
            if (value == null) remove(KEY_SHOWN) else putString(KEY_SHOWN, value.key)
            if (value?.entryId == null) remove(KEY_SHOWN_ENTRY) else putLong(KEY_SHOWN_ENTRY, value.entryId)
        }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    private companion object {
        const val FILE_NAME = "running_notification"
        const val KEY_SHOWN = "shown"
        const val KEY_SHOWN_ENTRY = "shown_entry"
    }
}
