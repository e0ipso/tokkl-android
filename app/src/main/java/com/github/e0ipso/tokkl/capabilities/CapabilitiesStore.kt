package com.github.e0ipso.tokkl.capabilities

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/**
 * The server's capabilities in their own private preferences file, so features know what to show
 * before the first refresh after a restart, and offline. The file holds no credential. It stays on
 * the phone, because the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class CapabilitiesStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored capabilities, or null when none are stored. */
    fun load(): Capabilities? {
        if (!preferences.contains(KEY_TRASH)) return null
        return Capabilities(
            trash = preferences.getBoolean(KEY_TRASH, false),
            restore = preferences.getBoolean(KEY_RESTORE, false),
            favorites = preferences.getBoolean(KEY_FAVORITES, false),
            timeResolution = preferences.getBoolean(KEY_TIME_RESOLUTION, false),
            negatedFilters = preferences.getBoolean(KEY_NEGATED_FILTERS, false),
            summaryCsv = readCsv(KEY_SUMMARY_CSV),
            detailedCsv = readCsv(KEY_DETAILED_CSV),
        )
    }

    /** Missing keys are not-yet-known, so an older file does not count as endpoint absence. */
    private fun readCsv(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    /** Replaces the stored capabilities in one write. */
    @Throws(IOException::class)
    fun save(capabilities: Capabilities) {
        commit {
            putBoolean(KEY_TRASH, capabilities.trash)
            putBoolean(KEY_RESTORE, capabilities.restore)
            putBoolean(KEY_FAVORITES, capabilities.favorites)
            putBoolean(KEY_TIME_RESOLUTION, capabilities.timeResolution)
            putBoolean(KEY_NEGATED_FILTERS, capabilities.negatedFilters)
            putCsv(KEY_SUMMARY_CSV, capabilities.summaryCsv)
            putCsv(KEY_DETAILED_CSV, capabilities.detailedCsv)
        }
    }

    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun SharedPreferences.Editor.putCsv(key: String, value: Boolean?) {
        if (value == null) remove(key) else putBoolean(key, value)
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
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
    }
}
