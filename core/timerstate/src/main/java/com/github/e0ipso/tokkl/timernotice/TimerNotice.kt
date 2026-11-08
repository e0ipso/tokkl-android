package com.github.e0ipso.tokkl.timernotice

import android.content.res.Resources
import com.github.e0ipso.tokkl.core.timerstate.R
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.problemText
import com.github.e0ipso.tokkl.session.toServerProblem
import com.github.e0ipso.tokkl.timerstate.TimerActionResult

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

    /** The server discarded the pomodoro [entryId] as too short to save. */
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
