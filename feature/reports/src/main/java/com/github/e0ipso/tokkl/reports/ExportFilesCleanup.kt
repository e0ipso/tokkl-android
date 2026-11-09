package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.session.SignedInListener
import java.io.IOException

/**
 * Deletes the finished report exports when the account changes, so no export outlives the account
 * it was made for, even when the next account has the same server, user id and credentials. Each
 * export belongs to the account context it began in, which ends before this runs and cancels its
 * download ([ReportExportController]). No token is kept.
 *
 * Sign-out runs it with the session, in [SignOutPhase.FORGET_SESSION]. A sign-in runs it once the
 * new session is stored.
 */
class ExportFilesCleanup(private val files: ReportExportFiles) :
    AccountDataOwner,
    SignedInListener {

    override val signOutPhase = SignOutPhase.FORGET_SESSION

    override suspend fun forgetAccount() = files.clear()

    override fun signedIn() {
        try {
            files.clear()
        } catch (_: IOException) {
            // The files expire within a day, the provider refuses expired reads, and sign-out
            // deletes them again.
        }
    }
}
