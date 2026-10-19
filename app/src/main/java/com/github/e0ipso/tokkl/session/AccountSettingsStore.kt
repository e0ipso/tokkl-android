package com.github.e0ipso.tokkl.session

import android.content.Context
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.time.DurationFormat
import java.io.IOException

/**
 * The account settings in their own private preferences file, so screens have them offline and
 * after a restart. The file holds no credential. It stays on the phone, because the backup and
 * data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so make calls off the main thread.
 */
class AccountSettingsStore(context: Context) {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    /** The stored settings, or null when none are stored. */
    fun load(): AccountSettings? {
        val timeZone = preferences.getString(KEY_TIME_ZONE, null) ?: return null
        val beginningOfWeek = dayOfWeek(preferences.getInt(KEY_BEGINNING_OF_WEEK, -1)) ?: return null
        val timeResolution = preferences.getString(KEY_TIME_RESOLUTION_MINUTES, null)?.toDoubleOrNull()
        // Settings stored before the app read the duration format have none, which means improved.
        val durationFormat = DurationFormat.fromTogglName(preferences.getString(KEY_DURATION_FORMAT, null))
        // Settings stored before the app kept the default workspace have none until the next refresh.
        val defaultWorkspaceId = KEY_DEFAULT_WORKSPACE_ID.takeIf(preferences::contains)?.let { preferences.getLong(it, 0) }
        // Settings stored before the app read the clock format have none, which means 24-hour.
        val twelveHour = preferences.getBoolean(KEY_TWELVE_HOUR, false)
        return AccountSettings(timeZone, beginningOfWeek, timeResolution, durationFormat, defaultWorkspaceId, twelveHour)
    }

    /** Replaces the stored settings in one write. */
    @Throws(IOException::class)
    fun save(settings: AccountSettings) {
        commit {
            putString(KEY_TIME_ZONE, settings.timeZone)
            putInt(KEY_BEGINNING_OF_WEEK, settings.beginningOfWeek.togglNumber)
            val timeResolution = settings.timeResolutionMinutes
            if (timeResolution == null) {
                remove(KEY_TIME_RESOLUTION_MINUTES)
            } else {
                // A Double's string form reads back as the same Double.
                putString(KEY_TIME_RESOLUTION_MINUTES, timeResolution.toString())
            }
            putString(KEY_DURATION_FORMAT, settings.durationFormat.togglName)
            val defaultWorkspaceId = settings.defaultWorkspaceId
            if (defaultWorkspaceId == null) remove(KEY_DEFAULT_WORKSPACE_ID) else putLong(KEY_DEFAULT_WORKSPACE_ID, defaultWorkspaceId)
            putBoolean(KEY_TWELVE_HOUR, settings.twelveHour)
        }
    }

    @Throws(IOException::class)
    fun clear() {
        commit { clear() }
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    internal companion object {
        const val FILE_NAME = "account_settings"
        private const val KEY_TIME_ZONE = "timezone"
        private const val KEY_BEGINNING_OF_WEEK = "beginning_of_week"
        private const val KEY_TIME_RESOLUTION_MINUTES = "time_resolution_minutes"
        private const val KEY_DURATION_FORMAT = "duration_format"
        private const val KEY_DEFAULT_WORKSPACE_ID = "default_workspace_id"
        private const val KEY_TWELVE_HOUR = "twelve_hour"
    }
}
