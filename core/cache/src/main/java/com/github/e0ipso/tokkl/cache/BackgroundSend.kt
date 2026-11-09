package com.github.e0ipso.tokkl.cache

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.HttpStatus
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutionException
import kotlin.coroutines.CoroutineContext

/**
 * Sends the queue in the background once the phone has a network, also with the app closed (docs/PRD.md O4). The app
 * calls [schedule] from [ChangeQueue]'s onQueued, which runs only while the queue holds a change, and [cancelWaiting]
 * once a drain has emptied the queue. The work runs once, so nothing wakes the phone while the queue is empty, and no
 * periodic work comes from here: the only periodic job in the app is the running entry's check (TKA-0074).
 *
 * @param runs the workers that joined a drain, which [BackgroundSendWorker] registers with.
 */
class BackgroundSend(private val workManager: CurrentWorkManager, private val runs: BackgroundSendRuns) :
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.STOP_WORK

    override suspend fun forgetAccount() = cancel()

    /**
     * Enqueues a one-time send that waits for a connected network. Work already waiting or running
     * stays, and this call adds none: that work drains the whole queue, this change included.
     * Returns at once; WorkManager writes the work on its own thread.
     */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<BackgroundSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
        workManager.get().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Cancels the work while it waits, once a drain has left the queue empty, so a retry waiting
     * out its backoff never wakes the phone to find nothing to send. A running worker stays: it
     * joined that same drain through [ChangeSender.sendAndWait] and ends on its own. When
     * [queueEmpty] turns false after the cancel, a change came meanwhile and the work is
     * scheduled again. Blocks on WorkManager and [queueEmpty], so call it on a worker thread.
     *
     * Worker registration and the cancel share a lock. Even when the state read becomes stale,
     * a worker that joined stays, and a worker cancelled before registration sends nothing.
     */
    @WorkerThread
    internal fun cancelWaiting(queueEmpty: () -> Boolean) {
        val workManager = workManager.get()
        runs.cancelWaiting(
            waiting = {
                workManager.getWorkInfosForUniqueWork(
                    WORK_NAME,
                ).get().any { it.state == WorkInfo.State.ENQUEUED }
            },
            cancel = { workManager.cancelUniqueWork(WORK_NAME).result.get() },
            queueEmpty = queueEmpty,
            schedule = ::schedule,
        )
    }

    /**
     * Cancels the work, waiting or running, and returns once WorkManager has recorded that.
     * Signing out calls this. Blocks, so call it on a worker thread.
     */
    @WorkerThread
    fun cancel() {
        workManager.get().cancelUniqueWork(WORK_NAME).result.get()
    }

    companion object {
        /** The unique work's name. One work by this name waits or runs at a time. */
        const val WORK_NAME = "send-queue"

        /**
         * The wait before the first retry after a transient failure. Each later retry waits twice as
         * long as the one before, up to WorkManager's cap of five hours.
         */
        val BACKOFF: Duration = Duration.ofSeconds(30)
    }
}

/**
 * Drains the queue through the app's [ChangeSender], which also sends while the app runs, so the
 * worker and the app never send at once. A transient failure retries with [BackgroundSend.BACKOFF];
 * any other failure ends the work, and the queue waits for the next change or refresh.
 */
class BackgroundSendWorker(
    context: Context,
    params: WorkerParameters,
    private val sender: ChangeSender,
    private val runs: BackgroundSendRuns,
    private val workManager: CurrentWorkManager,
    private val io: CoroutineContext,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(io) {
        val workManager = workManager.get()
        val joined = try {
            runs.join(id) {
                // Cancellation may be recorded before WorkManager delivers stop to this worker.
                // Read its committed state under the same lock as cancelWaiting's cancellation.
                !isStopped && workManager.getWorkInfoById(id).get()?.state == WorkInfo.State.RUNNING
            }
        } catch (_: ExecutionException) {
            return@withContext Result.retry()
        }
        if (!joined) return@withContext Result.success()
        try {
            when (sender.sendAndWait().afterBackgroundSend()) {
                AfterBackgroundSend.DONE -> Result.success()
                AfterBackgroundSend.RETRY -> Result.retry()
                AfterBackgroundSend.GIVE_UP -> Result.failure()
            }
        } finally {
            runs.leave(id)
        }
    }
}

/** What the background send does after a drain. */
internal enum class AfterBackgroundSend {
    /** The queue is empty, or signed out, so there is nothing to send. */
    DONE,

    /** The failure may pass, as no network, a 5xx, a 429 or the disk: try again after a backoff. */
    RETRY,

    /** The server refused the change, or the phone refused the server, until the user acts. */
    GIVE_UP,
}

/** What the background send does after a drain that ended in this outcome; null is a disk failure. */
internal fun ChangeSender.Outcome?.afterBackgroundSend(): AfterBackgroundSend = when (this) {
    is ChangeSender.Outcome.Empty, is ChangeSender.Outcome.SignedOut -> AfterBackgroundSend.DONE

    is ChangeSender.Outcome.Waiting, null -> AfterBackgroundSend.RETRY

    is ChangeSender.Outcome.Stopped -> {
        if (error.isTransient()) AfterBackgroundSend.RETRY else AfterBackgroundSend.GIVE_UP
    }
}

/**
 * True for a failure that a later try may get past: no response at all, a 5xx or a 429
 * (docs/PRD.md O6). A 4xx, a body the phone cannot read and a trust refusal stay until someone
 * acts.
 */
internal fun ApiError.isTransient(): Boolean = when (this) {
    is ApiError.Network -> true
    is ApiError.Http -> this is ApiError.RateLimited || status >= HttpStatus.FIRST_SERVER_ERROR
    is ApiError.InvalidResponse, is ApiError.HostNotAllowed, is ApiError.TrustRefused -> false
}

/**
 * Coordinates background workers joining the sender with cancellation of a waiting retry.
 * The WorkManager state read can be stale, so registration and the completed cancellation share
 * a lock. A worker cancelled before registration must fail [join]'s [canJoin] check too.
 * Every callback may block on disk; call these methods off the main thread.
 */
class BackgroundSendRuns {
    // A JVM lock, not a Mutex: WorkManager's threads call this without suspending.
    private val lock = Any()
    private val joined = mutableSetOf<UUID>()

    fun join(id: UUID, canJoin: () -> Boolean): Boolean = synchronized(lock) {
        if (canJoin()) joined.add(id) else false
    }

    fun leave(id: UUID) {
        synchronized(lock) { joined.remove(id) }
    }

    fun hasJoined(id: UUID): Boolean = synchronized(lock) { id in joined }

    fun cancelWaiting(waiting: () -> Boolean, cancel: () -> Unit, queueEmpty: () -> Boolean, schedule: () -> Unit) {
        try {
            if (!waiting()) return
            synchronized(lock) {
                if (joined.isEmpty()) cancel()
            }
        } catch (_: ExecutionException) {
            // A failed WorkManager read or cancel may leave a retry waiting. The queue check
            // still covers a cancellation that committed before WorkManager reported failure.
        }
        if (!queueEmpty()) schedule()
    }
}
