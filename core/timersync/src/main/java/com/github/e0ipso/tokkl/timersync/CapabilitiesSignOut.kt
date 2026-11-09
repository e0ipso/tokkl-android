package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase

/**
 * Sign-out's step for the server's capabilities: back to none, the stored flags deleted, then the
 * [watcher] told, so it stops starting the timer sync at boot. One step, since the watcher reads
 * the capabilities.
 *
 * @param forget puts the capabilities back to none, as `CapabilitiesSource.forget` does.
 */
class CapabilitiesSignOut(
    private val forget: suspend () -> Unit,
    private val store: CapabilitiesStore,
    private val watcher: TimerSyncWatcher,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() {
        forget()
        store.clear()
        watcher.watchTimerSync()
    }
}
