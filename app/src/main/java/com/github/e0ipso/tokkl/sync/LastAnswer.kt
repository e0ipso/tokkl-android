package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.api.SuccessListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

/**
 * When the server last gave a 2xx answer to the signed-in session (docs/PRD.md O5), from any
 * request: a refresh, a sent change, a report or a catalog page, also from the background send.
 * Every [succeeded] reads [clock] and hands the time to [save], so the time outlives the process
 * and shows offline after a restart. A time never goes back, so a slow answer that lands after a
 * later one leaves the later one.
 *
 * @param initial the stored time, or null when the server has not answered since sign-in.
 * @param save keeps the time on the phone, or forgets it when given null.
 */
class LastAnswer(
    initial: Instant?,
    private val save: (Instant?) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
) : SuccessListener {

    private val mutableState = MutableStateFlow(initial)

    val state: StateFlow<Instant?> = mutableState.asStateFlow()

    private val lock = Any()

    /** Called on the thread of the request that the server answered. */
    override fun succeeded() {
        val now = clock.instant()
        synchronized(lock) {
            val last = mutableState.value
            if (last != null && !now.isAfter(last)) return
            mutableState.value = now
            save(now)
        }
    }

    /** Forgets the time, for a sign-out. */
    fun forget() {
        synchronized(lock) {
            mutableState.value = null
            save(null)
        }
    }
}
