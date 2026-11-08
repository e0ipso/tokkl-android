package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.timernotice.TimerNotice
import kotlinx.coroutines.flow.StateFlow

/**
 * The timer's notices for its screens, which [TimerViewModel] keeps. A screen [claim]s a notice
 * before it shows it and calls [noticeShown] after, so each notice shows on one screen.
 */
interface TimerNoticeQueue {

    /** What the timer's screens should say now, or null. */
    val notice: StateFlow<TimerNotice?>

    /** Claims [notice] for the screen about to show it, and returns what to show, or null. */
    suspend fun claim(notice: TimerNotice): TimerNotice?

    /** The screen has shown [notice], or stopped showing it. */
    fun noticeShown(notice: TimerNotice)
}
