package com.github.e0ipso.tokkl.timer

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * The Timer page's view in its own private preferences file, so a cold start opens the last
 * choice before the refresh answers, and offline. It stays on the phone, because the backup and
 * data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class TimerViewStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored view, or null when none is stored or it names a view this version lacks. */
    fun load(): StoredTimerView? {
        val name = preferences.getString(KEY_VIEW, null) ?: return null
        val view = TimerView.entries.firstOrNull { it.name == name } ?: return null
        return StoredTimerView(view, unsent = preferences.getBoolean(KEY_UNSENT, false))
    }

    /** Replaces the stored view in one write. */
    @Throws(IOException::class)
    fun save(stored: StoredTimerView) {
        commit {
            putString(KEY_VIEW, stored.view.name)
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
        const val FILE_NAME = "timer_view"
        const val KEY_VIEW = "view"
        const val KEY_UNSENT = "unsent"
    }
}
