package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Signs out and clears the phone, such as [SignOut.run]. Returns false when a step failed. */
fun interface AccountSignOut {
    suspend fun run(): Boolean
}

/**
 * Signs the account out and leaves none of its data on the phone (docs/PRD.md S6). The phone holds
 * one account at a time, so the next sign-in starts from nothing.
 *
 * Each of [owners] forgets its part on [io], phase by phase in [SignOutPhase] order. A failed
 * step does not stop the ones after it, and the next [run] runs them all again, since each clears
 * what is there. A sign-out whose every step succeeded ends the account's [lifetime].
 */
class SignOut(owners: List<AccountDataOwner>, private val lifetime: AccountLifetime, private val io: CoroutineContext) :
    AccountSignOut {

    private val steps: List<AccountDataOwner> = owners.sortedBy { it.signOutPhase }

    private val running = Mutex()

    /** Runs every step and returns true when all of them succeeded. One sign-out runs at a time. */
    override suspend fun run(): Boolean = running.withLock {
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
            if (cleared) lifetime.end()
            cleared
        }
    }
}
