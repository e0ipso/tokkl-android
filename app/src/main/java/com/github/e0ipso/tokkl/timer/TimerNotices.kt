package com.github.e0ipso.tokkl.timer

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.problemText
import com.github.e0ipso.tokkl.catalog.toServerProblem
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.flow.filterNotNull

/**
 * Shows [model]'s notices in [host], one after another, while it is in the composition: a Pause,
 * Resume or Stop while paused that did not happen, and a pomodoro the server discarded as too
 * short. The navigation host puts it on each of the timer's pages, so every notice has one path
 * to the screen and a too-short pomodoro shows once, whichever page shows it.
 */
@Composable
fun TimerNotices(model: TimerModel, host: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(model, host, resources) {
        model.notice.filterNotNull().collect { notice ->
            val claimed = model.claim(notice) ?: return@collect
            try {
                host.showSnackbar(timerNoticeText(resources, claimed))
            } finally {
                model.noticeShown(claimed)
            }
        }
    }
}

/**
 * What [notice] says, in a sentence: the action's own message for a refusal or no answer, and
 * otherwise why the server failed, as the catalog pages say it.
 */
internal fun timerNoticeText(resources: Resources, notice: TimerNotice): String = when (notice) {
    is TimerNotice.PomodoroDiscarded -> resources.getString(R.string.timer_pomodoro_too_short)
    is TimerNotice.ActionFailed -> when (val result = notice.result) {
        TimerActionResult.Refused -> resources.getString(TimerActionResult.Refused.message)
        TimerActionResult.Offline -> resources.getString(TimerActionResult.Offline.message)
        is TimerActionResult.Failed -> problemText(resources, result.error.toServerProblem())
        // The model never makes a notice of an action that went through.
        TimerActionResult.Done -> problemText(resources, ServerProblem.Unexpected(null))
    }
}
