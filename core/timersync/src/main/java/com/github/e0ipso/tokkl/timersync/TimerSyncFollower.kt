package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Hears each decision on whether the timer sync runs, so it can follow whether the server has the timer API. */
fun interface TimerSyncWatcher {
    suspend fun watchTimerSync()
}

/**
 * Decides whether the timer event stream and [TimerSyncService] run, and starts, points or stops
 * them to match. It also holds which instance of the service runs in the foreground, which the
 * service publishes, and sends the service its stop requests, so nothing keeps a reference to the
 * service.
 *
 * @param startService starts [TimerSyncService] in the foreground, and returns false when the
 *   platform refuses, as it does for an app in the background.
 * @param signedIn whether an account is signed in, read from the session.
 * @param source the signed-in account's timer state.
 * @param timerApi whether the server offers the timer API.
 * @param signInNeeded whether the server refused the account's credential.
 */
class TimerSyncFollower(
    private val startService: () -> Boolean,
    private val signedIn: suspend () -> Boolean,
    private val source: () -> TimerStateSource,
    private val timerSync: TimerSync<TimerStateSource>,
    private val timerApi: suspend () -> Boolean,
    private val signInNeeded: () -> Boolean,
    private val syncWatcher: TimerSyncWatcher,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.STOP_WORK

    override suspend fun forgetAccount() {
        timerSync.stop().join()
        stopService()
    }

    private val instances = AtomicInteger()
    private val running = MutableStateFlow<Int?>(null)

    /**
     * The number of the [TimerSyncService] instance that runs in the foreground, or null while
     * none does. The service sets and clears it itself. The app's device tests wait on it too.
     */
    val runningService: StateFlow<Int?> = running.asStateFlow()

    private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The stop requests for the running service, which it collects on the main thread while it
     * runs. A request made while no service runs reaches no later one.
     */
    internal val stopRequests: SharedFlow<Unit> = stops.asSharedFlow()

    /**
     * Whether the timer event stream should run: signed in to a server with the timer API, whose
     * token the server has not refused. Reads the disk.
     */
    internal suspend fun wanted(): Boolean = timerApi() && !signInNeeded() && signedIn()

    /**
     * Starts [TimerSyncService], or points the running one at the account signed in now, while
     * [wanted], and stops the stream and the service otherwise. App open, every account refresh,
     * sign-in and the boot and update receiver call it. A start the platform refuses, as in the
     * background, is dropped: the next app open starts the service, and an exact alarm already
     * set still rings. Blocks on disk I/O.
     */
    suspend fun follow() {
        syncWatcher.watchTimerSync()
        val running = running.value
        if (!wanted()) {
            timerSync.stop()
            stopService()
        } else if (running != null) {
            run(running)
        } else {
            startService()
        }
    }

    /**
     * Runs the stream for the account signed in now, for as long as the service [instance] is the
     * one that runs and the stream is still wanted.
     */
    internal fun run(instance: Int) {
        timerSync.run(source()) {
            running.value == instance && timerApi() && !signInNeeded()
        }
    }

    /** Stops [TimerSyncService] unless a newer start is on its way, which then decides for itself. */
    fun stopService() {
        stops.tryEmit(Unit)
    }

    /** A new instance of the service runs in the foreground. Returns its number. */
    internal fun serviceRuns(): Int = instances.incrementAndGet().also { running.value = it }

    /** The service [instance] is gone. */
    internal fun serviceEnded(instance: Int) {
        running.compareAndSet(instance, null)
    }
}
