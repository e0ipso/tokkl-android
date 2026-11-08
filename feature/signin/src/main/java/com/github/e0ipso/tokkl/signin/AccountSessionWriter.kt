package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Stores what a sign-in got from the server: the account settings first, then it clears the
 * timer state and begins the account in the [lifetime], which gives it a new, empty timer state,
 * then it stores the server and its token. If the session write fails, the app stays
 * signed out and the next sign-in replaces the settings. A sign-in again after a refused token
 * then lifts the queue's pause, and the changes that waited go out (docs/PRD.md O6). Blocks on
 * disk I/O.
 */
internal class AccountSessionWriter(
    private val accountSettings: AccountSettingsSource,
    private val timerStates: AccountTimerStates,
    private val lifetime: AccountLifetime,
    private val session: Session,
    private val signedIn: List<SignedInListener>,
    private val sender: ChangeSender,
    private val timerSync: TimerSyncFollower,
) : SessionWriter {

    @Throws(GeneralSecurityException::class, IOException::class)
    override suspend fun save(server: ServerUrl, token: String, settings: AccountSettings) {
        accountSettings.store(settings)
        timerStates.clearForSignIn()
        lifetime.begin()
        session.save(server, token)
        signedIn.forEach(SignedInListener::signedIn)
        if (sender.signInNeeded.value) sender.resume()
        // Signing in again over a refused token keeps the capability: the stream restarts for the
        // new source now. A first sign-in starts it once its refresh has learned the capability.
        timerSync.follow()
    }
}
