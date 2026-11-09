package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.PreferencesChange
import com.github.e0ipso.tokkl.session.AccountContext
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.MePreferences
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
import java.io.IOException

/** A preference's value as the phone keeps it, and whether the server still has to get it. */
data class StoredPreference<out T>(val value: T, val unsent: Boolean)

/** How a [SyncedPreference] reads its value from the server's preferences and posts it back. */
interface PreferenceCodec<T : Any> {

    /** The stored [value] as the phone shows it, such as moved into its range. */
    fun loaded(value: T): T = value

    /** The value in a refresh's [preferences]. */
    fun fromServer(preferences: MePreferences): T

    /** The change that posts [value], with its keys and nothing else. */
    fun body(value: T): PreferencesChange
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
 * @param store keeps the value on the phone. It loads from it as this is made, off the main thread.
 * @param client the signed-in account's API client, or null when signed out.
 * @param account the account each change, refresh and send belongs to: once its context has
 *   ended, which sign-out does first, they show, store and send nothing more.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 * @param default the value before one is stored, and after [forget].
 * @param codec how the value reads from the server's preferences and posts back.
 */
class SyncedPreference<T : Any>(
    private val store: ClearableStore<StoredPreference<T>>,
    private val client: ApiClientProvider,
    private val account: CurrentAccount,
    private val scope: CoroutineScope,
    private val default: T,
    private val codec: PreferenceCodec<T>,
) : StartupLoad {

    /** The server has not received the value on show. Guarded by [memory]. */
    private var unsent = false

    /** A change came after the last refresh, whose preferences may predate it. Guarded by [memory]. */
    private var changedSinceRefresh = false

    private val held = LoadedState(scope, default) {
        store.load()?.let { stored ->
            unsent = stored.unsent
            codec.loaded(stored.value)
        } ?: default
    }

    /** The value on show. */
    val value: StateFlow<T> = held.state

    /** [value] once loaded, for readers that may run before the load ends. */
    val loaded: Flow<T> = held.loaded

    override val isReady: Boolean get() = held.isReady

    override suspend fun ready() = held.ready()

    /** Guards the value with the flags beside it. Held for memory work only, never across a write or a request. */
    private val memory = Mutex()

    /** Writes go one at a time, so the last change reaches the server last. */
    private val sending = Mutex()

    /** Orders the writes to disk, so the last one holds the latest value and sign-out's delete comes after them. */
    private val storing = Mutex()

    /**
     * Shows [value] at once, marked unsent when [unsent] says so, keeps it on the phone and then,
     * in the background, sends it to the server when [sendIf] says so. Call it on the main thread:
     * once the value has loaded, as it has for every screen, it shows before this returns.
     */
    fun change(value: T, unsent: suspend () -> Boolean, sendIf: suspend () -> Boolean): Job {
        val context = account.context.value
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ready()
            val markUnsent = unsent()
            val current = memory.withLock {
                // A sign-out came first: the change belonged to the account before.
                if (!context.isCurrent) return@withLock false
                held.value = value
                this@SyncedPreference.unsent = markUnsent
                changedSinceRefresh = true
                true
            }
            if (!current) return@launch
            persist(context)
            if (sendIf()) send(context)
        }
    }

    /**
     * Takes the refresh's `GET /api/v9/me/preferences`, null when it failed, from a server that
     * keeps Tokkl's preferences when [keepsPreferences]. Sends an unsent value instead of reading
     * the server's.
     */
    suspend fun refreshed(preferences: MePreferences?, keepsPreferences: Boolean) {
        val context = account.context.value
        ready()
        val sendNow = memory.withLock {
            val changed = changedSinceRefresh
            changedSinceRefresh = false
            val sendNow = when {
                // The value stays on the phone.
                !keepsPreferences -> {
                    unsent = false
                    false
                }

                unsent -> true

                changed || preferences == null -> false

                else -> {
                    held.value = codec.fromServer(preferences)
                    false
                }
            }
            sendNow
        }
        persist(context)
        if (sendNow) context.launchIn(scope) { send(context) }
    }

    /**
     * Goes back to [default] with nothing unsent, once a write on its way has landed. Signing out
     * calls this once the account's context has ended, so no later write of the account's value
     * reaches the disk, and then deletes the stored value with [clear].
     */
    suspend fun forget() {
        ready()
        storing.withLock {
            memory.withLock {
                held.value = default
                unsent = false
                changedSinceRefresh = false
            }
        }
    }

    /** Deletes the stored value. */
    @Throws(IOException::class)
    suspend fun clear() {
        store.clear()
    }

    /**
     * Posts the value on show while it is unsent, for the account of [context]. A failure leaves
     * it unsent for the next refresh.
     */
    private suspend fun send(context: AccountContext) = sending.withLock {
        val value = memory.withLock { held.value.takeIf { unsent && context.isCurrent } } ?: return@withLock
        val api = client() ?: return@withLock
        val answer = api.account.savePreferences(codec.body(value))
        if (answer !is ApiResult.Success) return@withLock
        memory.withLock {
            // A change while the request ran has its own write queued.
            if (context.isCurrent && held.value == value) unsent = false
        }
        persist(context)
    }

    /** Writes the value on show, while [context] is the account's. */
    private suspend fun persist(context: AccountContext) = storing.withLock {
        val stored = memory.withLock { StoredPreference(held.value, unsent).takeIf { context.isCurrent } }
            ?: return@withLock
        try {
            store.save(stored)
        } catch (_: IOException) {
            // The next change writes it again.
        }
    }
}
