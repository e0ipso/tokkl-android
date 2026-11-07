package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Signs the account out and leaves none of its data on the phone (docs/PRD.md S6). The phone holds
 * one account at a time, so the next sign-in starts from nothing.
 *
 * Each of [owners] forgets its part, in [SignOutOrder], on [io]. A failed step does not stop the
 * ones after it, and the next [run] runs them all again, since each clears what is there.
 */
class SignOut(owners: List<AccountDataOwner>, private val io: CoroutineContext) {

    private val steps: List<AccountDataOwner> = owners.sortedBy { it.signOutOrder }

    init {
        val orders = steps.map { it.signOutOrder }
        require(orders.distinct() == orders) { "Two sign-out steps share an order: $orders" }
    }

    private val mutableFinished = MutableStateFlow(0)

    /**
     * How many sign-outs have finished in this process with every step done. The navigation
     * starts over at sign-in and drops its view models, and with them what each screen holds in
     * memory, when this changes.
     */
    val finished: StateFlow<Int> = mutableFinished.asStateFlow()

    private val running = Mutex()

    /** Runs every step and returns true when all of them succeeded. One sign-out runs at a time. */
    suspend fun run(): Boolean = running.withLock {
        withContext(io) {
            var cleared = true
            for (step in steps) {
                try {
                    step.forgetAccount()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    cleared = false
                }
            }
            if (cleared) mutableFinished.update { it + 1 }
            cleared
        }
    }
}
