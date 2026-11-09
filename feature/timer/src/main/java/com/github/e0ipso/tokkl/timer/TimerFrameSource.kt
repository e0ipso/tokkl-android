package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.timernotice.TimerFrame
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timerstate.DiscardNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * What the signed-in frame reads of the timer, apart from the Timer page: whether it is idle,
 * which view the page shows, the starts made on the phone and the timer's notices.
 *
 * @param notices the failed pause chain actions the taps report, and the too-short pomodoro a
 *   screen claims, from [discards].
 */
internal class TimerFrameSource(
    override val settledIdle: Flow<Boolean>,
    override val listView: StateFlow<Boolean>,
    override val starts: SharedFlow<Unit>,
    private val notices: TimerNoticeBoard,
    discards: DiscardNotices,
    scope: CoroutineScope,
) : TimerFrame {

    /**
     * What the timer's screens should say now, or null: a claimed notice until it has shown, then
     * the latest failed pause chain action, then a too-short pomodoro no screen has shown yet.
     */
    override val notice: StateFlow<TimerNotice?> = notices.notices(discards.discardNotice)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Claims [notice] for the screen about to show it, and returns what to show, or null when
     * there is nothing left to show. A too-short pomodoro is recorded as shown here, once, so a
     * replay of its state or a restart does not show it again.
     */
    override suspend fun claim(notice: TimerNotice): TimerNotice? = notices.claim(notice)

    override fun noticeShown(notice: TimerNotice) = notices.shown(notice)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
