package com.github.e0ipso.tokkl.notification

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * The key of the running entry the notification last followed, in its own private preferences
 * file. The key is a hash, so the file holds no description or name. It stays on the phone,
 * because the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class RunningNotificationStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    fun load(): String? = preferences.getString(KEY_SHOWN, null)

    @Throws(IOException::class)
    fun save(key: String?) {
        commit { if (key == null) remove(KEY_SHOWN) else putString(KEY_SHOWN, key) }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    private companion object {
        const val FILE_NAME = "running_notification"
        const val KEY_SHOWN = "shown"
    }
}
