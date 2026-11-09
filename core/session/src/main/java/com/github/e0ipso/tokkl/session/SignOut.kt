package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Signs the account out and leaves none of its data on the phone (docs/PRD.md S6). The phone holds
 * one account at a time, so the next sign-in starts from nothing.
 *
 * It ends the account's context in [account] first, so no work the account started writes what
 * it gets from then on. Then each of [owners] forgets its part on [io], phase by phase in
 * [SignOutPhase] order. A failed step does not stop the ones after it, and the next [run] runs
 * them all again, since each clears what is there. [account] counts a sign-out whose every step
 * succeeded.
 */
class SignOut(owners: List<AccountDataOwner>, private val account: CurrentAccount, private val io: CoroutineContext) {

    private val steps: List<AccountDataOwner> = owners.sortedBy { it.signOutPhase }

    private val running = Mutex()

    /** Runs every step and returns true when all of them succeeded. One sign-out runs at a time. */
    suspend fun run(): Boolean = running.withLock {
        withContext(io) {
            account.end()
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
            if (cleared) account.signedOut()
            cleared
        }
    }
}
