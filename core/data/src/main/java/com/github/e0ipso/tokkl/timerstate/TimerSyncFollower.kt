package com.github.e0ipso.tokkl.timerstate

import android.content.Context
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshed
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOutOrder

/**
 * Decides whether the timer event stream and [TimerSyncService] run, and starts, points or stops
 * them to match.
 */
class TimerSyncFollower(
    private val context: Context,
    private val session: Session,
    private val timerStates: AccountTimerStates,
    private val timerSync: TimerSync<TimerStateSource>,
    private val capabilities: CapabilitiesSource,
    private val sender: ChangeSender,
    private val syncWatcher: TimerSyncWatcher,
    private val slot: TimerSyncServiceSlot,
) : AccountRefreshListener,
    AccountDataOwner {

    override val signOutOrder = SignOutOrder.TIMER_SYNC

    override suspend fun forgetAccount() {
        timerSync.stop()
        stopService()
    }

    /**
     * Whether the timer event stream should run: signed in to a server with the timer API, whose
     * token the server has not refused. Reads the disk.
     */
    internal fun wanted(): Boolean =
        capabilities.state.value.timerState && !sender.signInNeeded.value && session.isSignedIn()

    /** The first refresh after sign-in learns the capability, and any refresh can lose it. */
    override fun refreshed(refresh: AccountRefreshed) {
        follow()
    }

    /**
     * Starts [TimerSyncService], or points the running one at the account signed in now, while
     * [wanted], and stops the stream and the service otherwise. App open, every account refresh,
     * sign-in and the boot and update receiver call it. A start the platform refuses, as in the
     * background, is dropped: the next app open starts the service, and an exact alarm already
     * set still rings. Blocks on disk I/O.
     */
    fun follow() {
        syncWatcher.watchTimerSync()
        val running = slot.service
        if (!wanted()) {
            timerSync.stop()
            stopService()
        } else if (running != null) {
            run(running)
        } else {
            TimerSyncService.start(context)
        }
    }

    /**
     * Runs the stream for the account signed in now, for as long as [running] is the service and
     * the stream is still wanted.
     */
    internal fun run(running: TimerSyncService) {
        timerSync.run(timerStates.source) {
            slot.service === running && capabilities.state.value.timerState && !sender.signInNeeded.value
        }
    }

    /** Stops [TimerSyncService] unless a newer start is on its way, which then decides for itself. */
    fun stopService() {
        slot.stop()
    }
}
