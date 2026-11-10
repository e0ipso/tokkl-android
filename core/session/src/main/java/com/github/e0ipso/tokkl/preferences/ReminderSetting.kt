package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.ReminderChange
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/** Tokkl main: packages/types/index.ts and packages/api/src/models/users.ts. */
data class ReminderPreferences(val enabled: Boolean = true, val hours: Int = 8) {
    fun clamped() = copy(hours = hours.coerceIn(HOURS))
    companion object {
        val HOURS = 1..168
    }
}

/**
 * The reminder's preferences as the phone keeps them, whether the server still has to get them,
 * and whether a refresh found its two keys there.
 */
typealias StoredReminderPreferences = StoredPreference<ReminderPreferences>

/**
 * Account preferences with the same immediate local change and refresh retry as Pomodoro defaults,
 * through [SyncedPreference]. Whether the server keeps them comes from the two actual keys in the
 * refresh, not Tokkl's role: older Tokkl and Toggl use the phone. Only these keys go to
 * POST /me/preferences. Each change, refresh and send belongs to the account of [account] it began
 * for, so an old send stores nothing once sign-out has ended that account. The preferences load
 * from [store] as this is made, in [scope], off the main thread.
 */
class ReminderSetting(
    store: ClearableStore<StoredReminderPreferences>,
    client: ApiClientProvider,
    account: CurrentAccount,
    scope: CoroutineScope,
    io: CoroutineContext,
) : SyncedPreference<ReminderPreferences>(
    store,
    client,
    account,
    scope,
    io,
    default = ReminderPreferences(),
    codec = object : PreferenceCodec<ReminderPreferences> {
        override fun loaded(value: ReminderPreferences) = value.clamped()

        override fun fromServer(preferences: MePreferences) = preferences.reminderPreferences()

        override fun body(value: ReminderPreferences) = value.body()
    },
    sharing = object : PreferenceSharing {
        override suspend fun sendsChange(shared: Boolean) = shared

        override fun found(preferences: MePreferences?, keepsPreferences: Boolean, shared: Boolean) = when {
            !keepsPreferences -> false
            preferences == null -> shared
            else -> KEYS.all(preferences::has)
        }
    },
) {

    /** The preferences the reminder follows: the defaults until they load from the store. */
    val state: StateFlow<ReminderPreferences> get() = value

    suspend fun current(): ReminderPreferences {
        ready()
        return state.value
    }

    /** Shows [value] at once, once loaded, keeps it on the phone and sends it in the background. */
    override fun change(value: ReminderPreferences): Job = super.change(value.clamped())

    private companion object {
        val KEYS = listOf(ReminderChange.ENABLED, ReminderChange.HOURS)
    }
}

internal fun ReminderPreferences.body() = ReminderChange(enabled, clamped().hours)

fun MePreferences.reminderPreferences(): ReminderPreferences {
    val enabled = boolean(ReminderChange.ENABLED) ?: true
    val number = number(ReminderChange.HOURS)
    val hours = number?.takeIf { it % 1.0 == 0.0 && it >= 1 && it <= 168 }?.toInt() ?: 8
    return ReminderPreferences(enabled, hours)
}
