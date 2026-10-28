package com.github.e0ipso.tokkl.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklDangerButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import com.github.e0ipso.tokkl.ui.TokklIconButton
import java.time.Clock

/**
 * A presentation of the shared owner. It creates no timer, queue or cumulative clock state. While
 * paused, the fields Resume starts show read-only, and Edit is hidden: the server keeps them as
 * they were at Pause. Pause shows only where the running state offers it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenTimerScreen(
    model: TimerModel,
    onEditRunning: (TimerTarget) -> Unit,
    onExit: () -> Unit,
    clock: Clock = Clock.systemUTC(),
) {
    val state by model.state.collectAsState()
    val running = state as? TimerState.Running
    val paused = state as? TimerState.Paused
    val token = state.target
    val elapsed = rememberElapsedSeconds(running?.countsFrom, clock) + (running?.accumulatedSeconds ?: 0)
    val seconds = paused?.seconds ?: elapsed
    val focus = LocalFocusManager.current
    Scaffold(contentWindowInsets = ScrollingScreenInsets) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).screenContent()
                .padding(vertical = TokklTheme.spacing.betweenGroups).navigationBarsPadding()
                .testTag("fullscreen_timer"),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
        ) {
            TokklGhostButton(onClick = { focus.clearFocus(); onExit() }) {
                Text(stringResource(R.string.timer_exit_fullscreen))
            }
            if (token != null && (running != null || paused != null)) {
                Text(stringResource(if (paused != null) R.string.timer_paused else R.string.timer_running), style = MaterialTheme.typography.titleLarge)
                val spoken = stringResource(if (paused != null) R.string.timer_paused_elapsed_spoken else R.string.timer_elapsed_spoken, spokenDuration(LocalResources.current, seconds))
                Text(
                    formatDuration(seconds), style = TokklTheme.clockTextStyle.copy(fontSize = 56.sp, lineHeight = 64.sp),
                    modifier = Modifier.testTag("fullscreen_elapsed").semantics { contentDescription = spoken },
                )
                val fields = running?.fields ?: paused!!.fields
                val labels = running?.labels ?: paused!!.labels
                Text(fields.description.ifEmpty { stringResource(R.string.timer_suggestion_no_description) }, style = MaterialTheme.typography.headlineSmall)
                labels.project?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                labels.task?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (fields.tags.isNotEmpty()) Text(fields.tags.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                if (fields.billable) Text(stringResource(R.string.entry_billable), style = MaterialTheme.typography.bodyLarge)
                if (running?.unsent == true) Text(stringResource(R.string.history_unsent), style = MaterialTheme.typography.labelLarge)
                val stopAction = stringResource(R.string.timer_stop_spoken)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                    verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                ) {
                    if (paused != null) {
                        val resumeAction = stringResource(R.string.timer_resume_spoken)
                        TokklButton(modifier = Modifier.semantics { contentDescription = resumeAction }, onClick = {
                            model.resumeShown(token)
                            focus.clearFocus()
                        }) { Text(stringResource(R.string.timer_resume)) }
                    } else if (running?.pausable == true) {
                        val pauseAction = stringResource(R.string.timer_pause_spoken)
                        TokklButton(modifier = Modifier.semantics { contentDescription = pauseAction }, onClick = {
                            model.pauseShown(token)
                            focus.clearFocus()
                        }) { Text(stringResource(R.string.timer_pause)) }
                    }
                    TokklDangerButton(modifier = Modifier.semantics { contentDescription = stopAction }, onClick = {
                        model.stopShown(token)
                        focus.clearFocus()
                    }) { Text(stringResource(R.string.timer_stop)) }
                    if (running != null) {
                        TokklIconButton(
                            icon = R.drawable.ic_edit,
                            description = stringResource(R.string.timer_focused_edit),
                            onClick = {
                                if (model.isShown(token)) {
                                    focus.clearFocus()
                                    onEditRunning(token)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
