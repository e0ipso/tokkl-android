package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.timerstate.DiscardedPomodoro
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * The timer's notices: failed pause chain actions, and the too-short pomodoro a screen claims
 * before it shows it.
 *
 * @param takeDiscardNotice records that a screen shows the too-short pomodoro notice, and returns
 *   it, or null when none is left to show.
 */
internal class TimerNoticeBoard(private val takeDiscardNotice: suspend () -> DiscardedPomodoro?) {

    private val failures = MutableStateFlow<TimerNotice.ActionFailed?>(null)
    private var failureCount = 0L

    /** The too-short pomodoro notice a screen has claimed and not finished showing. */
    private val claimedDiscard = MutableStateFlow<TimerNotice.PomodoroDiscarded?>(null)

    /**
     * What the timer's screens should say now, or null: a claimed notice until it has shown, then
     * the latest failed pause chain action, then a too-short pomodoro from [discardNotices] no
     * screen has shown yet.
     */
    fun notices(discardNotices: Flow<DiscardedPomodoro?>): Flow<TimerNotice?> =
        combine(claimedDiscard, failures, discardNotices) { claimed, failure, discarded ->
            claimed ?: failure ?: discarded?.let { TimerNotice.PomodoroDiscarded(it.entryId) }
        }

    /** A pause chain action ended with [result], which is not done. */
    fun failed(result: TimerActionResult) {
        failures.value = TimerNotice.ActionFailed(result, ++failureCount)
    }

    /** See [TimerViewModel.claim]. */
    suspend fun claim(notice: TimerNotice): TimerNotice? = when (notice) {
        is TimerNotice.ActionFailed -> notice.takeIf { failures.value == it }
        is TimerNotice.PomodoroDiscarded ->
            if (claimedDiscard.value == notice) {
                notice
            } else {
                takeDiscardNotice()?.let { taken -> TimerNotice.PomodoroDiscarded(taken.entryId).also { claimedDiscard.value = it } }
            }
    }

    /** See [TimerViewModel.noticeShown]. */
    fun shown(notice: TimerNotice) {
        when (notice) {
            is TimerNotice.ActionFailed -> failures.compareAndSet(notice, null)
            is TimerNotice.PomodoroDiscarded -> claimedDiscard.compareAndSet(notice, null)
        }
    }
}
