package com.github.e0ipso.tokkl.tile

import android.database.sqlite.SQLiteException
import android.os.SystemClock
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.RunningCheckResults
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * One current-entry read when the shade opens, independent of the tile's display and taps.
 * The process owns both the request and its throttle: rebinding the service neither cancels a
 * request nor starts another. There is no periodic wakeup or retry. Failures also wait a minute
 * before another opening can try, so repeated openings cannot exceed N4's request cap.
 */
internal class TileRefresh(
    private val network: DefaultNetwork,
    private val session: Session,
    private val cache: AccountCache,
    private val results: RunningCheckResults,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {
    private val throttle = TileRefreshThrottle()

    fun onStartListening() {
        scope.launch(io) {
            val generation = results.generation()
            val online = network.reachesInternet()
            if (!online) return@launch
            val api = session.api() ?: return@launch
            if (!throttle.tryStart(generation, online, SystemClock.elapsedRealtime())) return@launch
            var succeeded = false
            try {
                val before = cache.runningCheckStart()
                val current = api.entries.current()
                if (current is ApiResult.Success) {
                    // Reuse the background check's sign-out, queued-change and newer-write guards.
                    results.write(generation) { cache.checkedRunning(before, current.value?.toCached()) }
                    succeeded = true
                }
            } catch (_: SQLiteException) {
                // Keep displaying the cached state if the disk cannot read it.
            } finally {
                throttle.finished(generation, succeeded, SystemClock.elapsedRealtime())
            }
        }
    }
}

/** Monotonic milliseconds, shared across listening intervals and scoped to the account generation. */
internal class TileRefreshThrottle {
    private var accountGeneration = Long.MIN_VALUE
    private var inFlight = false
    private var lastAttempt: Long? = null
    private var lastSuccess: Long? = null

    // A JVM lock, not a Mutex: TileService calls these on its binder thread without suspending.
    @Synchronized
    fun tryStart(generation: Long, online: Boolean, now: Long): Boolean {
        if (!online || generation < accountGeneration) return false
        if (generation != accountGeneration) {
            accountGeneration = generation
            inFlight = false
            lastAttempt = null
            lastSuccess = null
        }
        if (inFlight) return false
        if (lastAttempt?.let { now - it < MINUTE_MS } == true) return false
        if (lastSuccess?.let { now - it < MINUTE_MS } == true) return false
        inFlight = true
        lastAttempt = now
        return true
    }

    @Synchronized
    fun finished(generation: Long, succeeded: Boolean, now: Long) {
        if (generation != accountGeneration) return
        inFlight = false
        if (succeeded) lastSuccess = now
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
