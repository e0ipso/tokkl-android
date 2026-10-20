package com.github.e0ipso.tokkl.cache

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.ApiError
import java.time.Duration
import java.util.concurrent.ExecutionException

/**
 * Sends the queue in the background once the phone has a network, also with the app closed
 * (docs/PRD.md O4). The app calls [schedule] from [ChangeQueue]'s onQueued, which runs only while
 * the queue holds a change, and [cancelWaiting] once a drain has emptied the queue. The work runs
 * once, so nothing wakes the phone while the queue is empty, and no periodic work comes from here: the only periodic job in the app is the running
 * entry's check (TKA-0074).
 */
object BackgroundSend {

    /** The unique work's name. One work by this name waits or runs at a time. */
    const val WORK_NAME = "send-queue"

    /**
     * The wait before the first retry after a transient failure. Each later retry waits twice as
     * long as the one before, up to WorkManager's cap of five hours.
     */
    val BACKOFF: Duration = Duration.ofSeconds(30)

    /**
     * Enqueues a one-time send that waits for a connected network. Work already waiting or running
     * stays, and this call adds none: that work drains the whole queue, this change included.
     * Returns at once; WorkManager writes the work on its own thread.
     */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackgroundSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Cancels the work while it waits, once a drain has left the queue empty, so a retry waiting
     * out its backoff never wakes the phone to find nothing to send. A running worker stays: it
     * joined that same drain through [ChangeSender.sendAndWait] and ends on its own. When
     * [queueEmpty] turns false after the cancel, a change came meanwhile and the work is
     * scheduled again. Blocks on WorkManager and [queueEmpty], so call it off the main thread.
     *
     * A worker that starts between the check and the cancel ends as cancelled. Its drain runs on
     * the app's scope and goes on, and the check after the cancel keeps a queued change covered.
     */
    fun cancelWaiting(context: Context, queueEmpty: () -> Boolean) {
        val workManager = WorkManager.getInstance(context)
        try {
            val waiting = workManager.getWorkInfosForUniqueWork(WORK_NAME).get().any { it.state == WorkInfo.State.ENQUEUED }
            if (!waiting) return
            workManager.cancelUniqueWork(WORK_NAME).result.get()
        } catch (e: ExecutionException) {
            // WorkManager could not read or write its database. The work may still wait, and at
            // worst wakes the phone once; or the cancel went through, which the check below covers.
        }
        if (!queueEmpty()) schedule(context)
    }

    /**
     * Cancels the work, waiting or running, and returns once WorkManager has recorded that.
     * Signing out calls this. Blocks, so call it off the main thread.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
    }
}

/**
 * Drains the queue through the app's [ChangeSender], which also sends while the app runs, so the
 * worker and the app never send at once. A transient failure retries with [BackgroundSend.BACKOFF];
 * any other failure ends the work, and the queue waits for the next change or refresh.
 */
class BackgroundSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender = (applicationContext as TokklApplication).sender
        return when (sender.sendAndWait().afterBackgroundSend()) {
            AfterBackgroundSend.DONE -> Result.success()
            AfterBackgroundSend.RETRY -> Result.retry()
            AfterBackgroundSend.GIVE_UP -> Result.failure()
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
internal fun ChangeSender.Outcome?.afterBackgroundSend(): AfterBackgroundSend =
    when (this) {
        is ChangeSender.Outcome.Empty, is ChangeSender.Outcome.SignedOut -> AfterBackgroundSend.DONE
        is ChangeSender.Outcome.Waiting, null -> AfterBackgroundSend.RETRY
        is ChangeSender.Outcome.Stopped -> if (error.isTransient()) AfterBackgroundSend.RETRY else AfterBackgroundSend.GIVE_UP
    }

/**
 * True for a failure that a later try may get past: no response at all, a 5xx or a 429
 * (docs/PRD.md O6). A 4xx, a body the phone cannot read and a trust refusal stay until someone
 * acts.
 */
internal fun ApiError.isTransient(): Boolean =
    when (this) {
        is ApiError.Network -> true
        is ApiError.Http -> this is ApiError.RateLimited || status >= 500
        is ApiError.InvalidResponse, is ApiError.HostNotAllowed, is ApiError.TrustRefused -> false
    }
