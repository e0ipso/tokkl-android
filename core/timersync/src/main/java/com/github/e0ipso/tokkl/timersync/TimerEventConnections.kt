package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Opens the timer event stream's calls for [TimerSync], which hands them the account's timer
 * state. Each call takes the signed-in client and feeds the intake it was opened for, so a stream
 * of a retired account changes nothing. How a call ends goes back through its link; what decides
 * whether the service runs hears the ends no reconnect fixes from [TimerSync.terminations].
 *
 * @param scope where the calls run, on [io].
 */
internal class TimerEventConnections(
    private val session: ApiClientProvider,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    fun open(account: TimerStateSource, link: TimerSyncLink) {
        scope.launch(io) { link.ended(holdTimerEvents(session(), account, link)) }
    }
}
