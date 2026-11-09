package com.github.e0ipso.tokkl.timersync

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.timerstate.TimerStateIntake
import com.github.e0ipso.tokkl.timerstate.TimerStateIntakes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Opens and ends the timer event stream's calls for [TimerSync], which hands them the account's
 * timer state. Each call takes the signed-in client and feeds the intake it was opened for, so a
 * stream of a retired account changes nothing. A refused credential pauses the queue as the
 * queue's own refusal does, which shows the existing prompt to sign in again, and stops the
 * service, as a stream with no account does.
 *
 * @param follower asks [TimerSyncService] to stop ([TimerSyncFollower.stopService]). Lazy, since the
 *   follower drives the [TimerSync] these calls serve.
 * @param scope where the calls run, on [io].
 */
class TimerEventConnections(
    private val session: ApiClientProvider,
    private val timerStates: TimerStateIntakes,
    private val sender: ChangeSender,
    private val follower: Lazy<TimerSyncFollower>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : TimerSyncConnections<TimerStateIntake> {

    override fun open(account: TimerStateIntake, link: TimerSyncLink) {
        scope.launch(io) { link.ended(holdTimerEvents(session(), account, link)) }
    }

    override fun stopped(account: TimerStateIntake, end: StreamEnd) {
        if (end == StreamEnd.Refused && account === timerStates.intake()) sender.signInRefused()
        follower.value.stopService()
    }
}
