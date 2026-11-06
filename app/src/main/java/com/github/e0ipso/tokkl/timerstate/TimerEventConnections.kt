package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.session.Session
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens and ends the timer event stream's calls for [TimerSync], which hands them the account's
 * timer state. Each call takes the signed-in client and feeds the source it was opened for, so a
 * stream of a retired account changes nothing. A refused credential pauses the queue as the
 * queue's own refusal does, which shows the existing prompt to sign in again, and stops the
 * service, as a stream with no account does.
 *
 * @param scope where the calls run, on [io].
 */
internal class TimerEventConnections(
    private val session: Session,
    private val timerStates: AccountTimerStates,
    private val sender: ChangeSender,
    private val service: TimerSyncServiceSlot,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    /** For [TimerSync]'s `open`. */
    fun open(source: TimerStateSource, link: TimerSyncLink) {
        scope.launch(io) { link.ended(holdTimerEvents(session.client(), source, link)) }
    }

    /** For [TimerSync]'s `stopped`. */
    fun stopped(source: TimerStateSource, end: StreamEnd) {
        if (end == StreamEnd.Refused && source === timerStates.source) sender.signInRefused()
        service.stop()
    }
}
