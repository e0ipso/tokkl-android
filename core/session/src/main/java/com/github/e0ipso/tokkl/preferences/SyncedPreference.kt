package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.capabilities.CapabilityCheck
import com.github.e0ipso.tokkl.session.MePreferences
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
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/** A preference's value as the phone keeps it, and whether the server still has to get it. */
data class StoredPreference<out T>(val value: T, val unsent: Boolean)

/** How a [SyncedPreference] reads its value from the server's preferences and posts it back. */
interface PreferenceCodec<T : Any> {

    /** The stored [value] as the phone shows it, such as moved into its range. */
    fun loaded(value: T): T = value

    /** The value in a refresh's [preferences]. */
    fun fromServer(preferences: MePreferences): T

    /** The body that posts [value], with its keys and nothing else. */
    fun body(value: T): JsonObject
}

/**
 * One account preference that Tokkl keeps in `/api/v9/me/preferences` and the phone keeps in
 * [store], so a cold start has it before the refresh answers, and offline.
 *
 * A [change] shows at once and goes to `POST /api/v9/me/preferences` with [body], without waiting
 * for the answer. When that fails, as offline, the value stays on the phone, marked unsent, and
 * goes to the server with the next refresh, so the change that reaches the server last wins, as
 * for time entries (docs/PRD.md O3). Each refresh otherwise takes the server's value through
 * [fromServer], except the one refresh right after a change, whose preferences may predate it. A
 * server without Tokkl's preferences, such as Toggl Track, gets nothing: the value stays on the
 * phone.
 *
 * @param store keeps the value on the phone. The first read loads it, on the reading thread.
 * @param client the signed-in account's API client, or null when signed out. Runs on [io].
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 * @param default the value before one is stored, and after [forget].
 * @param codec how the value reads from the server's preferences and posts back.
 */
class SyncedPreference<T : Any>(
    private val store: ClearableStore<StoredPreference<T>>,
    private val client: ApiClientProvider,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val default: T,
    private val codec: PreferenceCodec<T>,
) {

    private val state by lazy { Loaded(store.load()) }

    /** What the store held, and what changed since. Guarded by [lock], apart from [value]'s reads. */
    private inner class Loaded(stored: StoredPreference<T>?) {
        val value = MutableStateFlow(stored?.value?.let(codec::loaded) ?: default)

        /** The server has not received the value on show. */
        var unsent = stored?.unsent ?: false
    }

    /** The value on show. */
    val value: StateFlow<T> by lazy { state.value.asStateFlow() }

    /** Guards [Loaded.unsent], [changedSinceRefresh] and each change of [value] with them. */
    private val lock = Any()

    /** A change came after the last refresh, whose preferences may predate it. */
    private var changedSinceRefresh = false

    /** Writes go one at a time, so the last change reaches the server last. */
    private val sending = Mutex()

    /** Orders the writes to disk, so the last one holds the latest value. */
    private val storing = Any()

    /**
     * Shows [value] at once, marked [unsent], keeps it on the phone and then, in the background,
     * sends it to the server when [sendIf] says so. Call it on the main thread.
     */
    fun change(value: T, unsent: Boolean, sendIf: CapabilityCheck): Job {
        synchronized(lock) {
            state.value.value = value
            state.unsent = unsent
            changedSinceRefresh = true
        }
        return scope.launch(io) {
            persist()
            if (sendIf.offered()) send()
        }
    }

    /**
     * Takes the refresh's `GET /api/v9/me/preferences`, null when it failed, from a server that
     * keeps Tokkl's preferences when [keepsPreferences]. Sends an unsent value instead of reading
     * the server's. Blocks on disk I/O, so call it off the main thread.
     */
    fun refreshed(preferences: MePreferences?, keepsPreferences: Boolean) {
        val sendNow = synchronized(lock) {
            val changed = changedSinceRefresh
            changedSinceRefresh = false
            when {
                // The value stays on the phone.
                !keepsPreferences -> {
                    state.unsent = false
                    false
                }

                state.unsent -> true

                changed || preferences == null -> false

                else -> {
                    state.value.value = codec.fromServer(preferences)
                    false
                }
            }
        }
        persist()
        if (sendNow) scope.launch(io) { send() }
    }

    /**
     * Goes back to [default] with nothing unsent. Signing out calls this once the client is gone,
     * so no send can succeed, and then deletes the stored value with [clear].
     */
    fun forget() {
        synchronized(storing) {
            synchronized(lock) {
                state.value.value = default
                state.unsent = false
                changedSinceRefresh = false
            }
        }
    }

    /** Deletes the stored value. Does disk I/O. */
    @Throws(IOException::class)
    fun clear() {
        store.clear()
    }

    /** Posts the value on show while it is unsent. A failure leaves it unsent for the next refresh. */
    private suspend fun send() = sending.withLock {
        val value = synchronized(lock) { state.value.value.takeIf { state.unsent } } ?: return@withLock
        val api = client() ?: return@withLock
        val answer = api.send("POST", api.server.api("me", "preferences"), codec.body(value), JsonObject.serializer())
        if (answer !is ApiResult.Success) return@withLock
        synchronized(lock) {
            // A change while the request ran has its own write queued.
            if (state.value.value == value) state.unsent = false
        }
        persist()
    }

    private fun persist() {
        synchronized(storing) {
            val stored = synchronized(lock) { StoredPreference(state.value.value, state.unsent) }
            try {
                store.save(stored)
            } catch (_: IOException) {
                // The next change writes it again.
            }
        }
    }
}
