package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ReminderChange
import com.github.e0ipso.tokkl.session.AccountContext
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.LoadedState
import com.github.e0ipso.tokkl.storage.StartupLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException

/** Tokkl main: packages/types/index.ts and packages/api/src/models/users.ts. */
data class ReminderPreferences(val enabled: Boolean = true, val hours: Int = 8) {
    fun clamped() = copy(hours = hours.coerceIn(HOURS))
    companion object {
        val HOURS = 1..168
        const val ENABLED_KEY = ReminderChange.ENABLED
        const val HOURS_KEY = ReminderChange.HOURS
    }
}

data class StoredReminderPreferences(val preferences: ReminderPreferences, val unsent: Boolean, val shared: Boolean)

/**
 * Account preferences with the same immediate local change and refresh retry as Pomodoro defaults.
 * Capability comes from the two actual keys, not Tokkl's role: older Tokkl and Toggl use the phone.
 * Only these keys go to POST /me/preferences. Each change, refresh and send belongs to the account
 * of [account] it began for, so an old send stores nothing once sign-out has ended that account.
 * The preferences load from [store] as this is made, in [scope], off the main thread.
 */
class ReminderSetting(
    private val store: ClearableStore<StoredReminderPreferences>,
    private val client: ApiClientProvider,
    private val account: CurrentAccount,
    private val scope: CoroutineScope,
) : AccountDataOwner,
    StartupLoad {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        forget()
        store.clear()
    }

    // Guarded by [lock], as the state's changes are.
    private var shared = false
    private var unsent = false
    private var changed = false

    private val held = LoadedState(scope, ReminderPreferences()) {
        store.load()?.let { stored ->
            shared = stored.shared
            unsent = stored.unsent
            stored.preferences.clamped()
        } ?: ReminderPreferences()
    }

    /** The preferences the reminder follows: the defaults until they load from the store. */
    val state: StateFlow<ReminderPreferences> = held.state

    /** [state] once loaded, for readers that may run before the load ends. */
    val loaded: Flow<ReminderPreferences> = held.loaded

    override val isReady: Boolean get() = held.isReady

    override suspend fun ready() = held.ready()

    /** The preferences, once loaded. */
    suspend fun current(): ReminderPreferences = held.current()

    /** Guards the preferences with the flags beside them. Held for memory work only. */
    private val lock = Mutex()
    private val sending = Mutex()

    /** Orders the writes to disk, so the last one holds the latest preferences. */
    private val storing = Mutex()

    /** Shows [preferences] at once, once loaded, keeps them on the phone and sends them in the background. */
    fun change(preferences: ReminderPreferences): Job {
        val context = account.context.value
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ready()
            val current = lock.withLock {
                // A sign-out came first: the change belonged to the account before.
                if (!context.isCurrent) return@withLock false
                held.value = preferences.clamped()
                unsent = shared
                changed = true
                true
            }
            if (!current) return@launch
            persist(context)
            send(context)
        }
    }

    /** Receives the existing account refresh, without another GET. */
    suspend fun refreshed(preferences: MePreferences?, tokkl: Boolean): Job {
        val context = account.context.value
        ready()
        lock.withLock {
            val wasShared = shared
            if (!tokkl) {
                shared = false
            } else if (preferences != null) {
                shared = preferences.body.keys.containsAll(
                    listOf(ReminderPreferences.ENABLED_KEY, ReminderPreferences.HOURS_KEY),
                )
            }
            if (shared && !wasShared && changed) unsent = true
            if (!shared) unsent = false
            val untouched = shared && !unsent && !changed
            if (untouched && preferences != null) {
                held.value = preferences.reminderPreferences()
            }
            changed = false
        }
        persist(context)
        return context.launch { send(context) }
    }

    /**
     * Goes back to the defaults, once a write on its way has landed. Signing out calls this once
     * the account's context has ended, so no later write of the account's preferences reaches the
     * disk.
     */
    suspend fun forget() {
        ready()
        storing.withLock {
            lock.withLock {
                held.value = ReminderPreferences()
                shared = false
                unsent = false
                changed = false
            }
        }
    }

    private suspend fun send(context: AccountContext) = sending.withLock {
        val values = lock.withLock {
            held.value.takeIf { context.isCurrent && unsent && shared }
        } ?: return@withLock
        val api = client() ?: return@withLock
        val result = api.account.savePreferences(values.body())
        if (result is ApiResult.Success) {
            lock.withLock {
                if (context.isCurrent && held.value == values) unsent = false
            }
        }
        persist(context)
    }

    private suspend fun persist(context: AccountContext) = storing.withLock {
        val stored = lock.withLock {
            StoredReminderPreferences(held.value, unsent, shared).takeIf { context.isCurrent }
        } ?: return@withLock
        try {
            store.save(stored)
        } catch (_: IOException) {
            // A later change or refresh retries the disk write.
        }
    }
}

internal fun ReminderPreferences.body() = ReminderChange(enabled, clamped().hours)

fun MePreferences.reminderPreferences(): ReminderPreferences {
    val enabled =
        (body[ReminderPreferences.ENABLED_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: true
    val number = (body[ReminderPreferences.HOURS_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    val hours = number?.takeIf { it % 1.0 == 0.0 && it >= 1 && it <= 168 }?.toInt() ?: 8
    return ReminderPreferences(enabled, hours)
}
