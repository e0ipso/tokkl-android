package com.github.e0ipso.tokkl.timer

import android.content.Context
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.PreferencesFile
import java.io.IOException

/**
 * The Timer page's view in its own private preferences file, so a cold start opens the last
 * choice before the refresh answers, and offline. It stays on the phone, because the backup and
 * data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class TimerViewStore(context: Context) : ClearableStore<StoredTimerView> {

    private val file = PreferencesFile(context, FILE_NAME)

    /** The stored view, or null when none is stored or it names a view this version lacks. */
    override suspend fun load(): StoredTimerView? = file.read {
        val name = getString(KEY_VIEW, null) ?: return@read null
        val view = TimerView.entries.firstOrNull { it.name == name } ?: return@read null
        StoredTimerView(view, unsent = getBoolean(KEY_UNSENT, false))
    }

    /** Replaces the stored view in one write. */
    @Throws(IOException::class)
    override suspend fun save(value: StoredTimerView) {
        file.commit {
            putString(KEY_VIEW, value.value.name)
            putBoolean(KEY_UNSENT, value.unsent)
        }
    }

    @Throws(IOException::class)
    override suspend fun clear() {
        file.commit { clear() }
    }

    private companion object {
        const val FILE_NAME = "timer_view"
        const val KEY_VIEW = "view"
        const val KEY_UNSENT = "unsent"
    }
}
