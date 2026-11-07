package com.github.e0ipso.tokkl.cache

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.nullable
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Checks the running entry every 15 minutes while a timer runs (docs/PRD.md 6.3), since no push
 * reaches the phone and a timer stopped on another client would keep ticking in the notification.
 * [follow] schedules the check while the cache holds a running entry and cancels it once none
 * runs, so no work waits while no timer runs (section 7, Battery). This is the app's only
 * periodic work.
 *
 * @param results serializes each check's cache write with sign-out, which [cancelAndWait] forgets.
 */
class RunningCheck(private val workManager: CurrentWorkManager, private val results: RunningCheckResults) :
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.STOP_WORK

    override suspend fun forgetAccount() = cancelAndWait()

    /**
     * Schedules the check while [running] is true and cancels it while it is false, until [scope]
     * ends. Call once, at process start, with whether the cache holds a running entry. A start,
     * a stop and a refresh land in the cache from any surface, so following it covers them all.
     */
    fun follow(running: Flow<Boolean>, scope: CoroutineScope, io: CoroutineContext): Job = scope.launch(io) {
        running.distinctUntilChanged().collect { if (it) schedule() else cancel() }
    }

    /**
     * Enqueues the periodic check, which waits for a connected network. A check already waiting
     * or running stays, and this call adds none. Returns at once.
     */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<RunningCheckWorker>(INTERVAL)
            .setInitialDelay(INTERVAL)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.get().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Cancels the check, waiting or running. Returns at once. */
    fun cancel() {
        workManager.get().cancelUniqueWork(WORK_NAME)
    }

    /**
     * Cancels the check and returns once WorkManager has recorded that. Signing out calls this.
     * Blocks, so call it off the main thread.
     */
    fun cancelAndWait() {
        results.forget()
        workManager.get().cancelUniqueWork(WORK_NAME).result.get()
    }

    companion object {
        /** The unique work's name. One check by this name waits or runs at a time. */
        const val WORK_NAME = "running-check"

        /**
         * How often the check runs: the shortest period WorkManager allows. The first check waits one
         * period too, since the start that schedules it has just reached the cache.
         */
        val INTERVAL: Duration = Duration.ofMinutes(15)
    }
}

/**
 * Reads `GET /api/v9/me/time_entries/current` once and writes the answer to the cache that the
 * notification, the widget and the tile read, as [AccountCache.checkedRunning] describes. It
 * sends nothing from the queue and refreshes nothing else. A failed request leaves the cache for
 * the next period, with no retry in between, so a failure adds no wakeup.
 */
class RunningCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val cache: AccountCache,
    private val session: Session,
    private val results: RunningCheckResults,
    private val io: CoroutineContext,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(io) {
        val generation = results.generation()
        val api = session.client() ?: return@withContext Result.success()
        val before = cache.runningCheckStart()
        val current = api.get(
            api.server.api("me", "time_entries", "current"),
            ServerTimeEntry.serializer().nullable,
        )
        if (current is ApiResult.Success) {
            results.write(generation) { cache.checkedRunning(before, current.value?.toCached()) }
        }
        Result.success()
    }
}

/**
 * Serializes a check's cache write with sign-out. A synchronous request can finish after its
 * worker is cancelled; a reply from the forgotten session must never refill the cleared cache.
 */
class RunningCheckResults {
    private var generation = 0L

    @Synchronized
    fun generation(): Long = generation

    @Synchronized
    fun forget() {
        generation++
    }

    @Synchronized
    fun write(expected: Long, apply: () -> Unit) {
        if (generation == expected) apply()
    }
}

/** What one running entry check writes to the cache. */
data class RunningCheckWrites(
    /** The cached running entries the server no longer runs. */
    val delete: List<Long> = emptyList(),
    /** The server's running entry, to replace the cached row or to add. */
    val upsert: CachedTimeEntry? = null,
)

/**
 * What a check writes, given [server], the entry the server runs or null when none runs, read
 * after the cache held the running entries [before]. [now] are the cached running entries when
 * the answer arrives. [unsent] tells whether the queue holds a change to an entry, [cached]
 * gives the cached row of an id, and [pendingAtStart] holds the ids the queue had changes to
 * when the request began, with the server ids that pending local ids have taken since.
 *
 * - An entry with unsent changes stays as it is, whatever the server says. So does an entry with
 *   a change queued when the request began: the sender may have sent it during the request, after
 *   the server read the answer, as a refresh's protection does for the same reason.
 * - An entry that changed or started since [before] stays: the phone or a refresh wrote it after
 *   the check's request went out, so the answer may be older than the row.
 * - Any other running entry the server no longer runs goes. The server stopped or deleted it, and
 *   the next refresh brings it back with its stop. The check does not know the stop.
 * - The server's running entry replaces the cached row of a running entry the server still runs,
 *   and is added when the cache does not hold it. A cached row of it that has stopped stays
 *   stopped: the phone's stop came after the answer was read. Nothing is added while a running
 *   entry on the phone has unsent changes, such as a start whose create is on its way, which the
 *   server may already run under its own id.
 */
internal fun runningCheckWrites(
    before: List<CachedTimeEntry>,
    now: List<CachedTimeEntry>,
    server: CachedTimeEntry?,
    unsent: (Long) -> Boolean,
    cached: (Long) -> CachedTimeEntry?,
    pendingAtStart: Set<Long> = emptySet(),
): RunningCheckWrites {
    val held = { id: Long -> id in pendingAtStart || unsent(id) }
    val unchanged = now.filter { it in before && !held(it.id) }
    val running = server?.takeIf { it.stop == null && !held(it.id) }
    // A timer with a change the server may lack, such as an unsent start, runs as the phone has
    // it, whatever runs on the server.
    val phoneRuns = now.any { held(it.id) }
    val upsert = running?.takeIf { !phoneRuns && now.all { it in before } }?.takeIf { entry ->
        val row = cached(entry.id)
        if (row == null) entry.id !in before.map { it.id } else row in unchanged
    }
    return RunningCheckWrites(
        delete = unchanged.filter { it.id != running?.id }.map { it.id },
        upsert = upsert?.takeIf { it != cached(it.id) },
    )
}
