package com.github.e0ipso.tokkl.timer

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import kotlinx.coroutines.flow.filterNotNull

/**
 * Shows [model]'s notices in [host], one after another, while it is in the composition: a Pause,
 * Resume or Stop while paused that did not happen, and a pomodoro the server discarded as too
 * short. The navigation host puts it on each of the timer's pages, so every notice has one path
 * to the screen and a too-short pomodoro shows once, whichever page shows it.
 */
@Composable
fun TimerNotices(model: TimerViewModel, host: SnackbarHostState) {
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
