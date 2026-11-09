package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.SignInPause
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Stores what a sign-in got from the server: the account settings first, then it clears the
 * timer state and begins the account in [account], which ends every piece of work the account
 * before started and gives it a new, empty timer state,
 * then it stores the server and its token. If the session write fails, the app stays
 * signed out and the next sign-in replaces the settings. A sign-in again after a refused token
 * then lifts the queue's pause, and the changes that waited go out (docs/PRD.md O6). Blocks on
 * disk I/O.
 *
 * @param clearTimerState clears the timer state and the remembered Start fields, so the account
 *   signing in starts from nothing. Blocks on disk I/O and throws [IOException] when it fails.
 */
internal class AccountSessionWriter(
    private val accountSettings: AccountSettingsSource,
    private val clearTimerState: () -> Unit,
    private val account: CurrentAccount,
    private val session: Session,
    private val signedIn: List<SignedInListener>,
    private val queue: SignInPause,
    private val timerSync: TimerSyncFollower,
) : SessionWriter {

    @Throws(GeneralSecurityException::class, IOException::class)
    override suspend fun save(server: ServerUrl, token: String, settings: AccountSettings) {
        accountSettings.store(settings)
        clearTimerState()
        account.begin()
        session.save(server, token)
        signedIn.forEach(SignedInListener::signedIn)
        if (queue.signInNeeded.value) queue.resume()
        // Signing in again over a refused token keeps the capability: the stream restarts for the
        // new source now. A first sign-in starts it once its refresh has learned the capability.
        timerSync.follow()
    }
}
