package com.github.e0ipso.tokkl.reminder

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.MePreferences
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
    initial: StoredReminderPreferences?,
    private val save: (StoredReminderPreferences) -> Unit,
    private val client: () -> TogglApiClient?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {
    private val mutable = MutableStateFlow(initial?.preferences?.clamped() ?: ReminderPreferences())
    val state = mutable.asStateFlow()
    private val lock = Any()
    private val sending = Mutex()
    private var shared = initial?.shared ?: false
    private var unsent = initial?.unsent ?: false
    private var changed = false
    private var generation = 0L

    fun change(preferences: ReminderPreferences): Job {
        val expected = synchronized(lock) {
            mutable.value = preferences.clamped()
            unsent = shared
            changed = true
            generation
        }
        return scope.launch(io) {
            store(expected)
            send(expected)
        }
    }

    /** Receives the existing account refresh, without another GET. Blocks on disk I/O. */
    fun refreshed(preferences: MePreferences?, tokkl: Boolean): Job {
        val expected = synchronized(lock) {
            val wasShared = shared
            if (!tokkl) shared = false
            else if (preferences != null) shared = preferences.body.keys.containsAll(
                listOf(ReminderPreferences.ENABLED_KEY, ReminderPreferences.HOURS_KEY),
            )
            if (shared && !wasShared && changed) unsent = true
            if (!shared) unsent = false
            if (shared && !unsent && !changed && preferences != null) mutable.value = preferences.reminderPreferences()
            changed = false
            generation
        }
        store(expected)
        return scope.launch(io) { send(expected) }
    }

    fun forget() = synchronized(lock) {
        generation++
        mutable.value = ReminderPreferences()
        shared = false
        unsent = false
        changed = false
    }

    private suspend fun send(expected: Long) = sending.withLock {
        val values = synchronized(lock) { mutable.value.takeIf { expected == generation && unsent && shared } } ?: return@withLock
        val api = client() ?: return@withLock
        val result = api.send("POST", api.server.api("me", "preferences"), values.body(), JsonObject.serializer())
        if (result is ApiResult.Success) synchronized(lock) {
            if (expected == generation && mutable.value == values) unsent = false
        }
        store(expected)
    }

    private fun store(expected: Long) = synchronized(lock) {
        if (generation != expected) return@synchronized
        try {
            save(StoredReminderPreferences(mutable.value, unsent, shared))
        } catch (e: IOException) {
            // A later change or refresh retries the disk write.
        }
    }
}

internal fun ReminderPreferences.body() = buildJsonObject {
    put(ReminderPreferences.ENABLED_KEY, enabled)
    put(ReminderPreferences.HOURS_KEY, clamped().hours)
}

internal fun MePreferences.reminderPreferences(): ReminderPreferences {
    val enabled = (body[ReminderPreferences.ENABLED_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: true
    val number = (body[ReminderPreferences.HOURS_KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    val hours = number?.takeIf { it % 1.0 == 0.0 && it >= 1 && it <= 168 }?.toInt() ?: 8
    return ReminderPreferences(enabled, hours)
}
