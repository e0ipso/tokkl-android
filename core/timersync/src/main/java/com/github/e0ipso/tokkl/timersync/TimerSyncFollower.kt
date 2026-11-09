package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.cache.SignInPause
import com.github.e0ipso.tokkl.capabilities.TimerApiOffer
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.collectDurably
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignInStatus
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.timerstate.TimerStateIntake
import com.github.e0ipso.tokkl.timerstate.TimerStateIntakes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Starts a foreground service, and returns false when the platform refuses, as for an app in the background. */
internal fun interface ServiceStart {
    fun start(): Boolean
}

/**
 * Hears that the server refused the account's credential on the timer event stream, as
 * [PauseQueueOnRefusal] does for the queue.
 */
internal fun interface SignInRefusal {
    fun refused()
}

/** Pauses the queue as its own refusal does, which shows the prompt to sign in again. */
internal class PauseQueueOnRefusal(private val queue: SignInPause) : SignInRefusal {
    override fun refused() = queue.signInRefused()
}

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
 * @param timerState where the signed-in account's timer state takes the stream's events.
 * @param timerApi whether the server offers the timer API.
 * @param signInNeeded whether the server refused the account's credential.
 * @param refusal hears a credential the stream's server refused.
 * @param scope where it follows the ends of [timerSync]'s streams that no reconnect fixes, for as
 *   long as the process lives.
 */
class TimerSyncFollower internal constructor(
    private val startService: ServiceStart,
    private val signedIn: SignInStatus,
    private val timerState: TimerStateIntakes,
    private val timerSync: TimerSync<TimerStateIntake>,
    private val timerApi: TimerApiOffer,
    private val signInNeeded: StateFlow<Boolean>,
    private val syncWatcher: TimerSyncWatcher,
    private val refusal: SignInRefusal,
    scope: CoroutineScope,
) : AccountDataOwner {

    override val signOutPhase = SignOutPhase.STOP_WORK

    override suspend fun forgetAccount() {
        timerSync.stop().join()
        stopService()
    }

    init {
        scope.launch { timerSync.terminations.collectDurably("TimerSyncFollower", action = ::streamEnded) }
    }

    /**
     * A stream ended in a way no reconnect fixes. A refused credential of the account signed in now
     * pauses the queue, which asks to sign in again. Either way the service stops, as with no
     * account.
     */
    private fun streamEnded(termination: StreamTermination<TimerStateIntake>) {
        if (termination.end == StreamEnd.Refused && termination.account === timerState.intake()) refusal.refused()
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
    internal suspend fun wanted(): Boolean = timerApi.offersTimerApi() && !signInNeeded.value && signedIn.isSignedIn()

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
            startService.start()
        }
    }

    /**
     * Runs the stream for the account signed in now, for as long as the service [instance] is the
     * one that runs and the stream is still wanted.
     */
    internal fun run(instance: Int) {
        timerSync.run(timerState.intake()) {
            running.value == instance && timerApi.offersTimerApi() && !signInNeeded.value
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

/** The last hook: on each MainActivity start, starts or stops the timer sync to match. */
class TimerSyncHook(private val follower: Lazy<TimerSyncFollower>) : AppHook {
    override suspend fun foregroundStart() = follower.value.follow()
}
