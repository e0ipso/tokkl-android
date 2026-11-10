package com.github.e0ipso.tokkl.timernotice

import android.content.res.Resources
import com.github.e0ipso.tokkl.core.timersync.R
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.problemText
import com.github.e0ipso.tokkl.session.toServerProblem
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the timer's screens say about it: a Pause, Resume or Stop while paused that did not
 * happen, and a pomodoro the server discarded as too short. The navigation host shows them in one
 * place, whichever of the timer's pages is on screen.
 */
sealed interface TimerNotice {

    /**
     * A pause chain action the server refused, or that got no answer, as [result] says.
     * [number] tells two failures with the same result apart.
     */
    data class ActionFailed(val result: TimerActionResult, val number: Long) : TimerNotice

    data class PomodoroDiscarded(val entryId: Long) : TimerNotice
}

/**
 * What [notice] says, in a sentence: the timer's own words for a refusal or no answer, and
 * otherwise why the server failed, in the words of the catalog pages ([problemText]).
 */
fun timerNoticeText(resources: Resources, notice: TimerNotice): String = when (notice) {
    is TimerNotice.PomodoroDiscarded -> resources.getString(R.string.timer_pomodoro_too_short)

    is TimerNotice.ActionFailed -> when (val result = notice.result) {
        TimerActionResult.Refused -> resources.getString(R.string.timer_action_refused)

        TimerActionResult.Offline -> resources.getString(R.string.timer_action_offline)

        is TimerActionResult.Failed -> problemText(resources, result.error.toServerProblem())

        // The model never makes a notice of an action that went through.
        TimerActionResult.Done -> problemText(resources, ServerProblem.Unexpected(null))
    }
}

/**
 * What a timer control outside the app's screens says when the phone's storage refused its action,
 * so nothing changed ([TimerFailureNotice.showNotStored]).
 */
fun timerNotStoredText(resources: Resources): String = resources.getString(R.string.timer_action_not_stored)

/** What the signed-in frame reads of the timer, which the Timer feature's view model gives it. */
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
