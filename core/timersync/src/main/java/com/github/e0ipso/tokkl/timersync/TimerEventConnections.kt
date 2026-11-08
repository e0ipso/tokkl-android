package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Opens and ends the timer event stream's calls for [TimerSync], which hands them the account's
 * timer state. Each call takes the signed-in client and feeds the source it was opened for, so a
 * stream of a retired account changes nothing. A refused credential pauses the queue as the
 * queue's own refusal does, which shows the existing prompt to sign in again, and stops the
 * service, as a stream with no account does.
 *
 * @param stopService asks [TimerSyncService] to stop, as [TimerSyncFollower.stopService] does.
 * @param scope where the calls run, on [io].
 */
class TimerEventConnections(
    private val session: ApiClientProvider,
    private val timerStates: AccountTimerStates,
    private val sender: ChangeSender,
    private val stopService: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : TimerSyncConnections<TimerStateSource> {

    override fun open(account: TimerStateSource, link: TimerSyncLink) {
        scope.launch(io) { link.ended(holdTimerEvents(session(), account, link)) }
    }

    override fun stopped(account: TimerStateSource, end: StreamEnd) {
        if (end == StreamEnd.Refused && account === timerStates.source) sender.signInRefused()
        stopService()
    }
}
