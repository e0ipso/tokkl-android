package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.LoadedState
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/**
 * When the server last gave a 2xx answer to the signed-in session (docs/PRD.md O5), from any
 * request: a refresh, a sent change, a report or a catalog page, also from the background send.
 * Every [succeeded] reads [clock] and saves the time in [store], so the time outlives the process
 * and shows offline after a restart. A time never goes back, so a slow answer that lands after a
 * later one leaves the later one. The stored time, null when the server has not answered since
 * sign-in, loads as this is made, in [scope] on [io], off the main thread.
 */
class LastAnswer(
    private val store: ValueStore<Instant?>,
    private val clock: Clock,
    private val scope: CoroutineScope,
    io: CoroutineContext,
) : SuccessListener,
    AccountDataOwner,
    StartupLoad {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = forget()

    private val loaded = LoadedState(scope, null, io) { store.load() }

    val state: StateFlow<Instant?> = loaded.state

    override val isReady: Boolean get() = loaded.isReady

    override suspend fun ready() = loaded.ready()

    /** Orders the changes of the time with their writes, so the disk keeps the latest. */
    private val writing = Mutex()

    /**
     * Called on the thread of the request that the server answered, without waiting for the
     * write. Once the stored time has loaded, the new one shows before this returns.
     */
    override fun succeeded() {
        val now = clock.instant()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ready()
            writing.withLock {
                val last = loaded.value
                if (last != null && !now.isAfter(last)) return@withLock
                loaded.set(now)
                store.save(now)
            }
        }
    }

    /** Forgets the time, for a sign-out. */
    suspend fun forget() {
        ready()
        writing.withLock {
            loaded.set(null)
            store.save(null)
        }
    }
}
