package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.session.SignedInListener
import java.io.IOException

/**
 * The account the report exports belong to. Each export holds a [Lease] while it downloads,
 * publishes and opens the chooser. Every sign-in and sign-out invalidates the leases, which
 * cancels their downloads, and deletes the finished files, so no export outlives the account it was
 * made for, even when the next account has the same server, user id and credentials. No token is
 * kept.
 *
 * Sign-out runs it with the session, in [SignOutPhase.FORGET_SESSION]. A sign-in runs it once the
 * new session is stored.
 */
class ExportAccount(private val files: ReportExportFiles) :
    AccountDataOwner,
    SignedInListener {

    override val signOutPhase = SignOutPhase.FORGET_SESSION

    override suspend fun forgetAccount() = invalidate()

    override fun signedIn() {
        try {
            invalidate()
        } catch (_: IOException) {
            // The files expire within a day, the provider refuses expired reads, and sign-out
            // deletes them again.
        }
    }

    private var generation = 0L
    private val leases = mutableSetOf<Lease>()

    inner class Lease internal constructor(internal val generation: Long, internal val cancel: () -> Unit) {
        // A JVM lock, not a Mutex: the export runs its step inside the lease without suspending,
        // on the IO and main threads, and no change of account may come in between.
        fun <T> current(action: () -> T): T? = synchronized(this@ExportAccount) {
            if (generation == this@ExportAccount.generation && this in leases) action() else null
        }

        fun close() = synchronized(this@ExportAccount) { leases.remove(this) }
    }

    @Synchronized
    fun open(cancel: () -> Unit): Lease = Lease(generation, cancel).also { leases.add(it) }

    /** Ends every lease, cancelling its download, and deletes the finished exports. */
    @Synchronized
    @Throws(IOException::class)
    fun invalidate() {
        generation++
        leases.forEach { it.cancel() }
        leases.clear()
        files.clear()
    }
}
