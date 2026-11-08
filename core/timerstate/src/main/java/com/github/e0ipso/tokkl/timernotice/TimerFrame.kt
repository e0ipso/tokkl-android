package com.github.e0ipso.tokkl.timernotice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the signed-in frame reads of the timer, which the Timer feature's view model gives it: when
 * the timer has settled idle, which closes the fullscreen timer, whether the Timer page shows its
 * List view, the starts that may ask for the notification permission, and the [notice] the timer's
 * pages show.
 */
interface TimerFrame {

    /** True while no entry runs, by the cache's word, and no tap of the timer's own is on its way. */
    val settledIdle: Flow<Boolean>

    /**
     * Whether the Timer page shows its List view, beside which an expanded window opens the entry
     * form. The Calendar view keeps the whole width.
     */
    val listView: StateFlow<Boolean>

    /** One item for each timer start made on the phone, for the notification permission's question. */
    val starts: Flow<Unit>

    /**
     * What the timer's screens should say now, or null. A screen [claim]s a notice before it shows
     * it and calls [noticeShown] after, so each notice shows on one screen.
     */
    val notice: StateFlow<TimerNotice?>

    /** Claims [notice] for the screen about to show it, and returns what to show, or null. */
    suspend fun claim(notice: TimerNotice): TimerNotice?

    /** The screen has shown [notice], or stopped showing it. */
    fun noticeShown(notice: TimerNotice)
}
