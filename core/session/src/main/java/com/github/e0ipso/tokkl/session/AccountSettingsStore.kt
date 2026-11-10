package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import com.github.e0ipso.tokkl.time.DurationFormat
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The account settings in their own private preferences file, so screens have them offline and
 * after a restart. The file holds no credential. It stays on the phone, because the backup and
 * data extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class AccountSettingsStore(context: Context, private val io: CoroutineContext) :
    ValueStore<AccountSettings>,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = clear()

    private val file = PreferencesFile(context, FILE_NAME, io)

    override suspend fun load(): AccountSettings? = file.read {
        val timeZone = getString(KEY_TIME_ZONE, null) ?: return@read null
        val beginningOfWeek = dayOfWeek(getInt(KEY_BEGINNING_OF_WEEK, -1)) ?: return@read null
        val timeResolution = getString(KEY_TIME_RESOLUTION_MINUTES, null)?.toDoubleOrNull()
        // Settings stored before the app read the duration format have none, which means improved.
        val durationFormat = DurationFormat.fromTogglName(getString(KEY_DURATION_FORMAT, null))
        // Settings stored before the app kept the default workspace have none until the next refresh.
        val defaultWorkspaceId = KEY_DEFAULT_WORKSPACE_ID.takeIf(this::contains)?.let { WorkspaceId(getLong(it, 0)) }
        // Settings stored before the app read the clock format have none, which means 24-hour.
        val twelveHour = getBoolean(KEY_TWELVE_HOUR, false)
        // Settings stored before the app read the grouping preference have none, which means grouped.
        val collapseSimilar = getBoolean(KEY_COLLAPSE_SIMILAR, true)
        // Settings stored before the app kept the account's id have none until the next refresh.
        val userId = KEY_USER_ID.takeIf(this::contains)?.let { getLong(it, 0) }
        AccountSettings(
            timeZone,
            beginningOfWeek,
            timeResolution,
            durationFormat,
            defaultWorkspaceId,
            twelveHour,
            collapseSimilar,
            userId,
        )
    }

    @Throws(IOException::class)
    override suspend fun save(value: AccountSettings) {
        file.commit {
            putString(KEY_TIME_ZONE, value.timeZone)
            putInt(KEY_BEGINNING_OF_WEEK, value.beginningOfWeek.togglNumber)
            val timeResolution = value.timeResolutionMinutes
            if (timeResolution == null) {
                remove(KEY_TIME_RESOLUTION_MINUTES)
            } else {
                // A Double's string form reads back as the same Double.
                putString(KEY_TIME_RESOLUTION_MINUTES, timeResolution.toString())
            }
            putString(KEY_DURATION_FORMAT, value.durationFormat.togglName)
            val defaultWorkspaceId = value.defaultWorkspaceId
            if (defaultWorkspaceId == null) {
                remove(
                    KEY_DEFAULT_WORKSPACE_ID,
                )
            } else {
                putLong(KEY_DEFAULT_WORKSPACE_ID, defaultWorkspaceId.value)
            }
            putBoolean(KEY_TWELVE_HOUR, value.twelveHour)
            putBoolean(KEY_COLLAPSE_SIMILAR, value.collapseSimilar)
            val userId = value.userId
            if (userId == null) remove(KEY_USER_ID) else putLong(KEY_USER_ID, userId)
        }
    }

    @Throws(IOException::class)
    suspend fun clear() {
        file.commit { clear() }
    }

    internal companion object {
        const val FILE_NAME = "account_settings"
        private const val KEY_TIME_ZONE = "timezone"
        private const val KEY_BEGINNING_OF_WEEK = "beginning_of_week"
        private const val KEY_TIME_RESOLUTION_MINUTES = "time_resolution_minutes"
        private const val KEY_DURATION_FORMAT = "duration_format"
        private const val KEY_DEFAULT_WORKSPACE_ID = "default_workspace_id"
        private const val KEY_TWELVE_HOUR = "twelve_hour"
        private const val KEY_COLLAPSE_SIMILAR = "collapse_similar"
        private const val KEY_USER_ID = "user_id"
    }
}
