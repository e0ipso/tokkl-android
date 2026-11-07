package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

/**
 * When the server last gave a 2xx answer to the signed-in session (docs/PRD.md O5), from any
 * request: a refresh, a sent change, a report or a catalog page, also from the background send.
 * Every [succeeded] reads [clock] and saves the time in [store], so the time outlives the process
 * and shows offline after a restart. A time never goes back, so a slow answer that lands after a
 * later one leaves the later one. The first read loads the stored time, null when the server has
 * not answered since sign-in, on the reading thread.
 */
class LastAnswer(private val store: ValueStore<Instant?>, private val clock: Clock = Clock.systemUTC()) :
    SuccessListener,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = forget()

    private val mutableState by lazy { MutableStateFlow(store.load()) }

    val state: StateFlow<Instant?> by lazy { mutableState.asStateFlow() }

    private val lock = Any()

    /** Called on the thread of the request that the server answered. */
    override fun succeeded() {
        val now = clock.instant()
        synchronized(lock) {
            val last = mutableState.value
            if (last != null && !now.isAfter(last)) return
            mutableState.value = now
            store.save(now)
        }
    }

    /** Forgets the time, for a sign-out. */
    fun forget() {
        synchronized(lock) {
            mutableState.value = null
            store.save(null)
        }
    }
}
