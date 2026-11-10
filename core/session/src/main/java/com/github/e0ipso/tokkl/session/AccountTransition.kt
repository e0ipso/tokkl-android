package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ServerUrl
import java.io.IOException
import java.security.GeneralSecurityException

/** Hears that an account has just signed in, once its session is saved. The app binds every one it has. */
fun interface SignedInListener {
    fun signedIn()
}

/**
 * Moves the phone to the account a sign-in got from the server: the configured server, its API
 * token and the account settings from the sign-in's own `GET /api/v9/me`. Beginning the account in
 * [account] ends every piece of work the account before started. If the session write fails, the
 * app stays signed out and the next sign-in replaces the settings. A sign-in again after a refused
 * token then lifts the queue's pause, and the changes that waited go out (docs/PRD.md O6). Blocks
 * on disk I/O. The app assembles it, since the timer state, the queue and the timer sync sit above
 * this module.
 *
 * @param storeSettings stores the account settings, as `AccountSettingsSource.store` does, and
 *   throws [IOException] when it fails.
 * @param clearTimerState clears the timer state and the remembered Start fields, so the account
 *   signing in starts from nothing. Blocks on disk I/O and throws [IOException] when it fails.
 * @param storeSession stores the server and its token, as `Session.save` does, and throws
 *   [IOException] or [GeneralSecurityException] when it fails.
 * @param followTimerSync lets the timer sync follow the account. Signing in again over a refused
 *   token keeps the capability, so the stream restarts for the new account at once; a first
 *   sign-in starts it once its refresh has learned the capability.
 */
class AccountTransition(
    private val storeSettings: suspend (AccountSettings) -> Unit,
    private val clearTimerState: () -> Unit,
    private val account: CurrentAccount,
    private val storeSession: suspend (ServerUrl, String) -> Unit,
    private val signedIn: List<SignedInListener>,
    private val queuePaused: () -> Boolean,
    private val resumeQueue: () -> Unit,
    private val followTimerSync: suspend () -> Unit,
) {

    @Throws(GeneralSecurityException::class, IOException::class)
    suspend fun signIn(server: ServerUrl, token: String, settings: AccountSettings) {
        storeSettings(settings)
        clearTimerState()
        account.begin()
        storeSession(server, token)
        signedIn.forEach(SignedInListener::signedIn)
        if (queuePaused()) resumeQueue()
        followTimerSync()
    }
}
