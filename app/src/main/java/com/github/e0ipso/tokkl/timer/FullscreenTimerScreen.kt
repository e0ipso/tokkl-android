package com.github.e0ipso.tokkl.timer

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
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
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.entry.EntryFieldsEditor
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
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

/** A presentation of the shared owner. It creates no timer, queue or cumulative clock state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenTimerScreen(
    model: TimerModel,
    cache: AccountCache,
    onEditRunning: (TimerTarget) -> Unit,
    onExit: () -> Unit,
    clock: Clock = Clock.systemUTC(),
) {
    val state by model.state.collectAsState()
    val running = state as? TimerState.Running
    val paused = state as? TimerState.Paused
    val token = state.target
    val elapsed = rememberElapsedSeconds(running?.start, clock) + (running?.accumulatedSeconds ?: 0)
    val seconds = paused?.seconds ?: elapsed
    var editingFields by rememberSaveable(token?.session, token?.chainId, token?.entryId) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun finishFields() {
        focus.clearFocus()
        if (token != null && model.isShown(token)) model.descriptionDone()
        editingFields = false
    }
    BackHandler(enabled = editingFields) { finishFields() }
    Scaffold(contentWindowInsets = ScrollingScreenInsets) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).screenContent()
                .padding(vertical = TokklTheme.spacing.betweenGroups).navigationBarsPadding()
                .testTag("fullscreen_timer"),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
        ) {
            TokklGhostButton(onClick = { finishFields(); onExit() }) {
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
                val primaryAction = stringResource(if (paused != null) R.string.timer_resume_spoken else R.string.timer_pause_spoken)
                val stopAction = stringResource(R.string.timer_stop_spoken)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                    verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                ) {
                    TokklButton(modifier = Modifier.semantics { contentDescription = primaryAction }, onClick = {
                        if (paused != null) model.resumeShown(token) else model.pauseShown(token)
                        focus.clearFocus()
                    }) { Text(stringResource(if (paused != null) R.string.timer_resume else R.string.timer_pause)) }
                    TokklDangerButton(modifier = Modifier.semantics { contentDescription = stopAction }, onClick = {
                        model.stopShown(token)
                        focus.clearFocus()
                    }) { Text(stringResource(R.string.timer_stop)) }
                    TokklIconButton(
                        icon = R.drawable.ic_edit,
                        description = stringResource(R.string.timer_focused_edit),
                        onClick = {
                            if (model.isShown(token)) {
                                focus.clearFocus()
                                if (paused != null) editingFields = true else onEditRunning(token)
                            }
                        },
                    )
                }
                if (paused != null && editingFields) {
                    // Field writes already use the paused snapshot revision fence. There is no new
                    // Save/Cancel transaction; accepted fields belong only to the next Resume clone.
                    key(token.session, token.chainId) {
                        EntryFieldsEditor(
                            fields = paused.fields, labels = paused.labels, original = paused.original,
                            descriptionHint = stringResource(R.string.timer_description_running),
                            onDescriptionChange = { if (model.isShown(token)) model.editDescription(it) },
                            onDescriptionDone = { if (model.isShown(token)) model.descriptionDone() },
                            onPick = { if (model.isShown(token)) model.pick(it) },
                            onBillableChange = { if (model.isShown(token)) model.setBillable(it) },
                            picker = { original, picked, onPickedChange, modifier ->
                                EntityPicker(cache, paused.workspaceId, original, picked, onPickedChange, modifier)
                            },
                        ) {
                            TokklGhostButton(onClick = ::finishFields) { Text(stringResource(R.string.timer_fields_done)) }
                        }
                    }
                }
            }
        }
    }
}
