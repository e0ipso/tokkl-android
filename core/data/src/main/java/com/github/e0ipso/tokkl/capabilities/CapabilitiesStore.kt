package com.github.e0ipso.tokkl.capabilities

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import java.io.IOException

/**
 * The server's capabilities in their own private preferences file, so features know what to show
 * before the first refresh after a restart, and offline. The file holds no credential. It stays on
 * the phone, because the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class CapabilitiesStore(context: Context) : ValueStore<Capabilities> {

    private val file = PreferencesFile(context, FILE_NAME)
    private val preferences get() = file.preferences

    /** The stored capabilities, or null when none are stored. */
    override fun load(): Capabilities? {
        if (!preferences.contains(KEY_TRASH)) return null
        return Capabilities(
            trash = preferences.getBoolean(KEY_TRASH, false),
            restore = preferences.getBoolean(KEY_RESTORE, false),
            favorites = preferences.getBoolean(KEY_FAVORITES, false),
            timeResolution = preferences.getBoolean(KEY_TIME_RESOLUTION, false),
            negatedFilters = preferences.getBoolean(KEY_NEGATED_FILTERS, false),
            summaryCsv = readOptional(KEY_SUMMARY_CSV),
            detailedCsv = readOptional(KEY_DETAILED_CSV),
            timerState = preferences.getBoolean(KEY_TIMER_STATE, false),
            allDay = readOptional(KEY_ALL_DAY),
        )
    }

    /** Missing keys are not known yet, so an older file does not count as absence. */
    private fun readOptional(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    /** Replaces the stored capabilities in one write. */
    @Throws(IOException::class)
    override fun save(value: Capabilities) {
        file.commit {
            putBoolean(KEY_TRASH, value.trash)
            putBoolean(KEY_RESTORE, value.restore)
            putBoolean(KEY_FAVORITES, value.favorites)
            putBoolean(KEY_TIME_RESOLUTION, value.timeResolution)
            putBoolean(KEY_NEGATED_FILTERS, value.negatedFilters)
            putOptional(KEY_SUMMARY_CSV, value.summaryCsv)
            putOptional(KEY_DETAILED_CSV, value.detailedCsv)
            putBoolean(KEY_TIMER_STATE, value.timerState)
            putOptional(KEY_ALL_DAY, value.allDay)
        }
    }

    @Throws(IOException::class)
    fun clear() {
        file.commit { clear() }
    }

    private fun SharedPreferences.Editor.putOptional(key: String, value: Boolean?) {
        if (value == null) remove(key) else putBoolean(key, value)
    }

    internal companion object {
        const val FILE_NAME = "capabilities"
        private const val KEY_TRASH = "trash"
        private const val KEY_RESTORE = "restore"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_TIME_RESOLUTION = "time_resolution"
        private const val KEY_NEGATED_FILTERS = "negated_filters"
        private const val KEY_SUMMARY_CSV = "summary_csv"
        private const val KEY_DETAILED_CSV = "detailed_csv"
        private const val KEY_TIMER_STATE = "timer_state"
        private const val KEY_ALL_DAY = "all_day"
    }
}
