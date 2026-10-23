package com.github.e0ipso.tokkl.tile

import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.ConnectivityManager
import android.os.SystemClock
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.reachesInternet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.nullable

/**
 * One current-entry read when the shade opens, independent of the tile's display and taps.
 * The process owns both the request and its throttle: rebinding the service neither cancels a
 * request nor starts another. There is no periodic wakeup or retry. Failures also wait a minute
 * before another opening can try, so repeated openings cannot exceed N4's request cap.
 */
internal object TileRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val throttle = TileRefreshThrottle()

    fun onStartListening(context: Context) {
        val application = context.applicationContext as TokklApplication
        scope.launch {
            val generation = RunningCheck.results.generation()
            val online = application.getSystemService(ConnectivityManager::class.java).reachesInternet()
            RunningCheck.currentRequestDiagnostic?.invoke("producer=TileRefresh connectivity.decision generation=$generation online=$online")
            if (!online) return@launch
            val api = Session(application).client() ?: return@launch
            if (!throttle.tryStart(generation, online, SystemClock.elapsedRealtime())) return@launch
            var succeeded = false
            try {
                val cache = application.cache
                val before = cache.runningEntries()
                RunningCheck.currentRequestDiagnostic?.invoke(
                    "producer=TileRefresh current.enter host=${api.server.api("me").host} port=${api.server.api("me").port} generation=$generation online=$online",
                )
                val current = api.get(api.server.api("me", "time_entries", "current"), ServerTimeEntry.serializer().nullable)
                RunningCheck.currentRequestDiagnostic?.invoke("producer=TileRefresh current.return success=${current is ApiResult.Success}")
                if (current is ApiResult.Success) {
                    // Reuse the background check's sign-out, queued-change and newer-write guards.
                    RunningCheck.results.write(generation) { cache.checkedRunning(before, current.value?.toCached()) }
                    succeeded = true
                }
            } catch (e: SQLiteException) {
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
