package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits until the app's shared timer controls, which every timer surface draws, are [T] and pass
 * [done]. A timeout fails with [what], the last controls seen and the server state the phone
 * holds.
 */
inline fun <reified T : TimerControls> TokklApplication.awaitControls(
    what: String,
    timeoutMs: Long = 15_000,
    crossinline done: (T) -> Boolean = { true },
): T {
    var last: TimerControls? = null
    val reached = runBlocking {
        withTimeoutOrNull(timeoutMs) { timerControls.first { last = it; it is T && done(it) } }
    }
    return reached as? T ?: throw AssertionError("$what: last controls $last; held ${timerState.state.value?.state}")
}

/** The server's timer state, read with [client]'s own token, past the app. */
fun TogglApiClient.timerState(): TimerState = get(server.app("timer"), TimerState.serializer()).orFail("GET /api/app/timer")

/**
 * Treats the server as one without the timer API, as Toggl Track or a Tokkl release before 1.10.0,
 * and stops the timer event stream with its service. Tests of the paths such a server relies on,
 * the periodic running check and the tile's refresh on opening, then see no change arrive through
 * the stream first. The next account refresh learns the capability again. Blocks on disk I/O.
 */
fun TokklApplication.followNoTimerEvents() {
    capabilities.forget()
    followTimerSync()
    // A start still on its way decides for itself, against the forgotten capability, and stops.
    val deadline = System.nanoTime() + 10_000_000_000L
    while (timerSyncService != null) {
        if (System.nanoTime() > deadline) throw AssertionError("the timer sync service did not stop")
        Thread.sleep(50)
    }
}
