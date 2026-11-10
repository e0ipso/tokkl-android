package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.PreferencesChange
import com.github.e0ipso.tokkl.session.AccountContext
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.LoadResult
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
import kotlin.coroutines.CoroutineContext

/**
 * A preference's value as the phone keeps it, whether the server still has to get it, and whether
 * a refresh found the server keeping it, for a preference whose [PreferenceSharing] looks.
 */
data class StoredPreference<out T>(val value: T, val unsent: Boolean, val shared: Boolean = false)

/** How a [SyncedPreference] reads its value from the server's preferences and posts it back. */
interface PreferenceCodec<T : Any> {

    /** The stored [value] as the phone shows it, such as moved into its range. */
    fun loaded(value: T): T = value

    fun fromServer(preferences: MePreferences): T

    /** The change that posts [value], with its keys and nothing else. */
    fun body(value: T): PreferencesChange
}

/** How a [SyncedPreference] learns whether the server keeps it, and so whether a change goes there. */
fun interface PreferenceSharing {

    /**
     * Whether a change made now goes to the server, and waits as unsent until it has. [shared] is
     * what the last refresh [found]. It may wait, as for the capabilities to load.
     */
    suspend fun sendsChange(shared: Boolean): Boolean

    /**
     * Whether a refresh's [preferences], null when the read failed, from a server whose
     * capabilities say [keepsPreferences], show the server keeping this preference, given the
     * [shared] the phone found before. Null when the capabilities alone tell, and nothing is kept.
     */
    fun found(preferences: MePreferences?, keepsPreferences: Boolean, shared: Boolean): Boolean? = null
}

/**
 * One account preference that Tokkl keeps in `/api/v9/me/preferences` and the phone keeps in
 * [store], so a cold start has it before the refresh answers, and offline.
 *
 * A change that fails to reach the server, as offline, stays on the phone, marked unsent, and goes
 * with the next refresh, so the change that reaches the server last wins, as for time entries
 * (docs/PRD.md O3). Each refresh otherwise takes the server's value, except the first refresh after
 * a change or its acknowledgement, whose preferences may predate the write. A server without
 * Tokkl's preferences, such as Toggl Track, gets nothing: the value stays on the phone.
 *
 * @param store keeps the value on the phone. It loads from it as this is made, off the main thread.
 * @param account the account each change, refresh and send belongs to: once its context has
 *   ended, which sign-out does first, they show, store and send nothing more.
 * @param scope outlives every screen, so leaving the app does not cancel a write.
 * @param default the value before one is stored, and after [forget].
 *
 * Sign-out forgets the value and clears [store]. Each setting extends this with its codec.
 */
open class SyncedPreference<T : Any>(
    private val store: ClearableStore<StoredPreference<T>>,
    private val client: ApiClientProvider,
    private val account: CurrentAccount,
    private val scope: CoroutineScope,
    io: CoroutineContext,
    private val default: T,
    private val codec: PreferenceCodec<T>,
    private val sharing: PreferenceSharing,
) : AccountDataOwner,
    StartupLoad {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        forget()
        clear()
    }

    /** The server has not received the value on show. Guarded by [memory]. */
    private var unsent = false

    /** A change or its acknowledgement came after the last refresh, which may predate it. Guarded by [memory]. */
    private var changedSinceRefresh = false

    /** What the last refresh [PreferenceSharing.found]. Guarded by [memory]. */
    private var shared = false

    private val held = LoadedState(scope, default, io) {
        store.load()?.let { stored ->
            unsent = stored.unsent
            shared = stored.shared
            codec.loaded(stored.value)
        } ?: default
    }

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
     * Call it on the main thread: once the value has loaded, as it has for every screen, it shows
     * before this returns.
     */
    open fun change(value: T): Job {
        val context = account.context.value
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ready()
            val sends = sharing.sendsChange(memory.withLock { shared })
            val current = memory.withLock {
                // A sign-out came first: the change belonged to the account before.
                if (!context.isCurrent) return@withLock false
                held.set(value)
                unsent = sends
                changedSinceRefresh = true
                true
            }
            if (!current) return@launch
            persist(context)
            if (sends) send(context)
        }
    }

    /**
     * Takes the refresh's `GET /api/v9/me/preferences`, null when it failed, from a server whose
     * capabilities say [keepsPreferences]. Sends an unsent value instead of reading the server's,
     * in the returned job. When the phone failed to read its stored value at process start, it reads
     * it again first, and takes nothing while it still cannot, so the server's value never goes to
     * disk over a change the phone has not sent.
     */
    suspend fun refreshed(preferences: MePreferences?, keepsPreferences: Boolean): Job {
        val context = account.context.value
        // A value the phone could not read, such as an unsent change, stays on disk as it is.
        if (held.loadResult() is LoadResult.Failed) return Job().apply { complete() }
        val sendNow = memory.withLock {
            val changed = changedSinceRefresh
            changedSinceRefresh = false
            val found = sharing.found(preferences, keepsPreferences, shared)
            // A change made before the server was found keeping the preference goes to it now.
            if (found == true && !shared && changed) unsent = true
            shared = found ?: false
            when {
                // The value stays on the phone.
                !(found ?: keepsPreferences) -> {
                    unsent = false
                    false
                }

                unsent -> true

                changed || preferences == null -> false

                else -> {
                    held.set(codec.fromServer(preferences))
                    false
                }
            }
        }
        persist(context)
        return context.launch { if (sendNow) send(context) }
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
                held.set(default)
                unsent = false
                changedSinceRefresh = false
                shared = false
            }
        }
    }

    @Throws(IOException::class)
    suspend fun clear() {
        store.clear()
    }

    /** A failure leaves the value unsent for the next refresh. */
    private suspend fun send(context: AccountContext) = sending.withLock {
        val value = memory.withLock { held.value.takeIf { unsent && context.isCurrent } } ?: return@withLock
        val api = client() ?: return@withLock
        val answer = api.account.savePreferences(codec.body(value))
        if (answer !is ApiResult.Success) return@withLock
        memory.withLock {
            // A change while the request ran has its own write queued.
            if (context.isCurrent && held.value == value) {
                unsent = false
                // A refresh can read the old server value while this write is still in flight.
                changedSinceRefresh = true
            }
        }
        persist(context)
    }

    private suspend fun persist(context: AccountContext) = storing.withLock {
        val stored = memory.withLock { StoredPreference(held.value, unsent, shared).takeIf { context.isCurrent } }
            ?: return@withLock
        try {
            store.save(stored)
        } catch (_: IOException) {
            // The value stays in memory, and the next change or refresh writes it again.
        }
    }
}
