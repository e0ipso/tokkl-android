package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.ReminderChange
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.StartupLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Tokkl main: packages/types/index.ts and packages/api/src/models/users.ts. */
data class ReminderPreferences(val enabled: Boolean = true, val hours: Int = 8) {
    fun clamped() = copy(hours = hours.coerceIn(HOURS))
    companion object {
        val HOURS = 1..168
        const val ENABLED_KEY = ReminderChange.ENABLED
        const val HOURS_KEY = ReminderChange.HOURS
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
) : AccountDataOwner,
    StartupLoad {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        forget()
        preference.clear()
    }

    private val preference = SyncedPreference(
        store,
        client,
        account,
        scope,
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
    )

    /** The preferences the reminder follows: the defaults until they load from the store. */
    val state: StateFlow<ReminderPreferences> get() = preference.value

    /** [state] once loaded, for readers that may run before the load ends. */
    val loaded: Flow<ReminderPreferences> get() = preference.loaded

    override val isReady: Boolean get() = preference.isReady

    override suspend fun ready() = preference.ready()

    /** The preferences, once loaded. */
    suspend fun current(): ReminderPreferences {
        ready()
        return state.value
    }

    /** Shows [preferences] at once, once loaded, keeps them on the phone and sends them in the background. */
    fun change(preferences: ReminderPreferences): Job = preference.change(preferences.clamped())

    /** Receives the existing account refresh, without another GET. */
    suspend fun refreshed(preferences: MePreferences?, tokkl: Boolean): Job = preference.refreshed(preferences, tokkl)

    /**
     * Goes back to the defaults, once a write on its way has landed. Signing out calls this once
     * the account's context has ended, so no later write of the account's preferences reaches the
     * disk.
     */
    suspend fun forget() {
        preference.forget()
    }

    private companion object {
        val KEYS = listOf(ReminderPreferences.ENABLED_KEY, ReminderPreferences.HOURS_KEY)
    }
}

internal fun ReminderPreferences.body() = ReminderChange(enabled, clamped().hours)

fun MePreferences.reminderPreferences(): ReminderPreferences {
    val enabled = boolean(ReminderPreferences.ENABLED_KEY) ?: true
    val number = number(ReminderPreferences.HOURS_KEY)
    val hours = number?.takeIf { it % 1.0 == 0.0 && it >= 1 && it <= 168 }?.toInt() ?: 8
    return ReminderPreferences(enabled, hours)
}
