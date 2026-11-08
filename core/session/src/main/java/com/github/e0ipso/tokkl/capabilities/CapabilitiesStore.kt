package com.github.e0ipso.tokkl.capabilities

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import java.io.IOException

/**
 * The server's capabilities in their own private preferences file, so features know what to show
 * before the first refresh after a restart, and offline. The file holds no credential. It stays on
 * the phone, because the backup and data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class CapabilitiesStore(context: Context) : ValueStore<Capabilities> {

    private val file = PreferencesFile(context, FILE_NAME)

    /** The stored capabilities, or null when none are stored. */
    override suspend fun load(): Capabilities? = file.read {
        if (!contains(KEY_TRASH)) return@read null
        Capabilities(
            trash = getBoolean(KEY_TRASH, false),
            restore = getBoolean(KEY_RESTORE, false),
            favorites = getBoolean(KEY_FAVORITES, false),
            timeResolution = getBoolean(KEY_TIME_RESOLUTION, false),
            negatedFilters = getBoolean(KEY_NEGATED_FILTERS, false),
            summaryCsv = readOptional(KEY_SUMMARY_CSV),
            detailedCsv = readOptional(KEY_DETAILED_CSV),
            timerState = getBoolean(KEY_TIMER_STATE, false),
            allDay = readOptional(KEY_ALL_DAY),
        )
    }

    /** Missing keys are not known yet, so an older file does not count as absence. */
    private fun SharedPreferences.readOptional(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    /** Replaces the stored capabilities in one write. */
    @Throws(IOException::class)
    override suspend fun save(value: Capabilities) {
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
    suspend fun clear() {
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
