package com.github.e0ipso.tokkl.preferences

import androidx.annotation.VisibleForTesting
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshed
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/** Tokkl main: packages/types/index.ts and packages/api/src/models/users.ts. */
data class ReminderPreferences(val enabled: Boolean = true, val hours: Int = 8) {
    fun clamped() = copy(hours = hours.coerceIn(HOURS))
    companion object {
        val HOURS = 1..168
        const val ENABLED_KEY = "long_timer_reminder_enabled"
        const val HOURS_KEY = "long_timer_reminder_hours"
    }
}

data class StoredReminderPreferences(val preferences: ReminderPreferences, val unsent: Boolean, val shared: Boolean)

/**
 * Account preferences with the same immediate local change and refresh retry as Pomodoro defaults.
 * Capability comes from the two actual keys, not Tokkl's role: older Tokkl and Toggl use the phone.
 * Only these keys go to POST /me/preferences. A generation prevents an old send persisting after sign-out.
 */
class ReminderSetting(
    private val store: ClearableStore<StoredReminderPreferences>,
    private val client: ApiClientProvider,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AccountRefreshListener,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        forget()
        store.clear()
    }

    private val loaded by lazy { Loaded(store.load()) }

    /** What the store held, and what changed since. Guarded by [lock], apart from [state]'s reads. */
    private class Loaded(stored: StoredReminderPreferences?) {
        val state = MutableStateFlow(stored?.preferences?.clamped() ?: ReminderPreferences())
        var shared = stored?.shared ?: false
        var unsent = stored?.unsent ?: false
    }

    /** The preferences the reminder follows, loaded from the store on the first read. */
    val state: StateFlow<ReminderPreferences> by lazy { loaded.state.asStateFlow() }

    // A JVM lock, not a Mutex: changes come from the main thread and refreshes from the IO
    // dispatcher, neither suspending.
    private val lock = Any()
    private val sending = Mutex()
    private var changed = false
    private var generation = 0L

    fun change(preferences: ReminderPreferences): Job {
        val expected = synchronized(lock) {
            loaded.state.value = preferences.clamped()
            loaded.unsent = loaded.shared
            changed = true
            generation
        }
        return scope.launch(io) {
            persist(expected)
            send(expected)
        }
    }

    override fun refreshed(refresh: AccountRefreshed) {
        refreshed(refresh.preferences, tokkl = refresh.capabilities.favorites)
    }

    /** Receives the existing account refresh, without another GET. Blocks on disk I/O. */
    fun refreshed(preferences: MePreferences?, tokkl: Boolean): Job {
        val expected = synchronized(lock) {
            val wasShared = loaded.shared
            if (!tokkl) {
                loaded.shared = false
            } else if (preferences != null) {
                loaded.shared = preferences.body.keys.containsAll(
                    listOf(ReminderPreferences.ENABLED_KEY, ReminderPreferences.HOURS_KEY),
                )
            }
            if (loaded.shared && !wasShared && changed) loaded.unsent = true
            if (!loaded.shared) loaded.unsent = false
            val untouched = loaded.shared && !loaded.unsent && !changed
            if (untouched && preferences != null) {
                loaded.state.value = preferences.reminderPreferences()
            }
            changed = false
            generation
        }
        persist(expected)
        return scope.launch(io) { send(expected) }
    }

    fun forget() = synchronized(lock) {
        generation++
        loaded.state.value = ReminderPreferences()
        loaded.shared = false
        loaded.unsent = false
        changed = false
    }

    private suspend fun send(expected: Long) = sending.withLock {
        val values = synchronized(
            lock,
        ) { loaded.state.value.takeIf { expected == generation && loaded.unsent && loaded.shared } } ?: return@withLock
        val api = client() ?: return@withLock
        val result = api.send("POST", api.server.api("me", "preferences"), values.body(), JsonObject.serializer())
        if (result is ApiResult.Success) {
            synchronized(lock) {
                if (expected == generation && loaded.state.value == values) loaded.unsent = false
            }
        }
        persist(expected)
    }

    private fun persist(expected: Long) = synchronized(lock) {
        if (generation != expected) return@synchronized
        try {
            store.save(StoredReminderPreferences(loaded.state.value, loaded.unsent, loaded.shared))
        } catch (_: IOException) {
            // A later change or refresh retries the disk write.
        }
    }
}

internal fun ReminderPreferences.body() = buildJsonObject {
    put(ReminderPreferences.ENABLED_KEY, enabled)
    put(ReminderPreferences.HOURS_KEY, clamped().hours)
}

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
fun MePreferences.reminderPreferences(): ReminderPreferences {
    val enabled =
        (body[ReminderPreferences.ENABLED_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: true
    val number = (body[ReminderPreferences.HOURS_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    val hours = number?.takeIf { it % 1.0 == 0.0 && it >= 1 && it <= 168 }?.toInt() ?: 8
    return ReminderPreferences(enabled, hours)
}
