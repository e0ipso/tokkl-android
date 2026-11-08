package com.github.e0ipso.tokkl.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entryfields.DescriptionSuggestion
import com.github.e0ipso.tokkl.entryfields.DescriptionSuggestions
import com.github.e0ipso.tokkl.entryfields.EntryFieldsEditor
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.favorites.FavoritesProblem
import com.github.e0ipso.tokkl.favorites.FavoritesState
import com.github.e0ipso.tokkl.favorites.TemplatesPopup
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The project, task and tag picker for an entry in [workspaceId], as [EntityPicker] takes it.
 */
typealias WorkspacePicker =
    @Composable (
        workspaceId: Long,
        original: Picked,
        picked: Picked,
        onPickedChange: (Picked) -> Unit,
        modifier: Modifier,
    ) -> Unit

/**
 * What the timer at the top of the Timer page does: the timer's own taps, which go to the
 * session's timer, and the page's [onAddEntry], [onEditRunning], [onOpenFullscreen] and
 * [onManageTemplates]. [onShown] hears each state once the frame that drew it is on screen.
 */
class TimerBarActions(
    val onShown: (TimerState) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onDescriptionFinish: () -> Unit = {},
    val onPick: (Picked) -> Unit = {},
    val onBillableChange: (Boolean) -> Unit = {},
    val onFill: (EntryFields) -> Unit = {},
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onStartTemplate: (EntryFields) -> Unit = {},
    val onSaveTemplate: (EntryFields, (FavoritesProblem?) -> Unit) -> Unit = { _, _ -> },
    val onManageTemplates: () -> Unit = {},
    val onAddEntry: () -> Unit = {},
    val onEditRunning: () -> Unit = {},
    val onOpenFullscreen: () -> Unit = {},
)

/**
 * The timer at the top of the Timer page (docs/PRD.md T1) for [state], with [page]'s cached
 * projects, tasks and tags in its picker and its description suggestions, and the Today and This
 * week [totals] under it. While an entry runs, the clock counts from its start on the phone, a
 * tick at each whole second while the screen shows, and asks the server nothing (principle 3).
 * Its clock opens [TimerBarActions.onEditRunning]. Its button for a past entry calls
 * [TimerBarActions.onAddEntry] (M1). Its timer-templates icon, when [templates] has them, opens a
 * popup of cached templates (T5). Choosing one starts it through the timer. Save as template
 * stores what the timer shows, and Manage templates calls [TimerBarActions.onManageTemplates].
 */
@Composable
fun TimerBar(
    state: TimerState,
    page: TimerPageState,
    totals: TimerTotalsState,
    actions: TimerBarActions,
    modifier: Modifier = Modifier,
    templates: FavoritesState? = null,
    clock: Clock = Clock.systemUTC(),
) {
    val shown by rememberUpdatedState(actions.onShown)
    // The next frame starts once the frame that composed a new state has drawn it. That ends the
    // trace section of the Start or Stop tap that made the state.
    LaunchedEffect(state) {
        withFrameNanos {}
        shown(state)
    }
    val running = state as? TimerState.Running
    val elapsed = (running?.accumulatedSeconds ?: 0) + rememberElapsedSeconds(running?.countsFrom, clock)
    val focusManager = LocalFocusManager.current
    val timerFields = when (state) {
        is TimerState.Running -> state.fields
        is TimerState.Paused -> state.fields
        is TimerState.Idle -> state.fields
        TimerState.Loading -> null
    }
    Column(modifier) {
        TimerBarContent(
            suggestions = page.suggestions,
            onSuggestion = { actions.onFill(it.fields) },
            state = state,
            elapsedSeconds = elapsed,
            onDescriptionChange = actions.onDescriptionChange,
            onDescriptionFinish = actions.onDescriptionFinish,
            onPick = actions.onPick,
            onBillableChange = actions.onBillableChange,
            onStart = actions.onStart,
            onStop = actions.onStop,
            onAddEntry = actions.onAddEntry,
            onEditRunning = actions.onEditRunning,
            onOpenFullscreen = actions.onOpenFullscreen,
            picker = { workspaceId, original, picked, onPickedChange, pickerModifier ->
                EntityPicker(page.picker, page.offline, workspaceId, original, picked, onPickedChange, pickerModifier)
            },
            onPause = actions.onPause,
            onResume = actions.onResume,
            templates = {
                if (templates != null) {
                    TemplatesPopup(
                        state = templates,
                        timerFields = timerFields,
                        onStart = { fields ->
                            // A choice starts through the timer, which ends a pause chain.
                            actions.onStartTemplate(fields)
                            settleDescription(focusManager)
                        },
                        onSave = actions.onSaveTemplate,
                        onManage = actions.onManageTemplates,
                        offline = page.offline,
                    )
                }
            },
        )
        TimerTotals(totals)
    }
}

/**
 * Ends typing in the timer's description after a template or Continue has started an entry, as
 * Start and Stop do. A tap on those buttons leaves focus in the field, which keeps showing what
 * was typed, and would hand that text on to the new entry when it lost focus later. The start has
 * already given the text to the running entry it was typed for, which it stopped, or dropped the
 * idle timer's draft that the started entry replaces. Clear focus only after the start: then the
 * field's text matches what it last showed, so losing focus hands nothing on, and the field
 * shows the started entry's description.
 */
fun settleDescription(focusManager: FocusManager) = focusManager.clearFocus()

/**
 * The timer as the web app's TimerBar.tsx draws it, for [state]: the entry's fields, then the
 * clock and Start, or while an entry runs, the "Running" label, the clock at [elapsedSeconds],
 * Pause where the running state offers it, and Stop. While paused (docs/PRD.md T1) come the
 * "Paused" label, the paused total, which stands still, Resume and Stop, and the fields that
 * Resume starts, which are read-only. The frame takes the running colour while an entry runs, and
 * the label and the Stop button's square say so too, so colour is never the only sign. Until the
 * phone knows the account's workspace, which the first account settings bring, the fields and
 * Start are disabled. After the timer's buttons comes the one that adds a past entry by hand, as
 * the web app's bar holds its manual mode (M1), whatever the timer does. With [suggestions], the
 * description field suggests recent entries as it is typed in (T4), and a pick goes to
 * [onSuggestion].
 */
@Composable
fun TimerBarContent(
    state: TimerState,
    elapsedSeconds: Long,
    onDescriptionChange: (String) -> Unit,
    onDescriptionFinish: () -> Unit,
    onPick: (Picked) -> Unit,
    onBillableChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAddEntry: () -> Unit,
    picker: WorkspacePicker,
    modifier: Modifier = Modifier,
    suggestions: DescriptionSuggestions? = null,
    onSuggestion: (DescriptionSuggestion) -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    templates: @Composable () -> Unit = {},
    onEditRunning: () -> Unit = {},
    onOpenFullscreen: () -> Unit = {},
) {
    val running = state as? TimerState.Running
    val paused = state as? TimerState.Paused
    val idle = state as? TimerState.Idle
    val workspaceId = running?.workspaceId ?: paused?.workspaceId ?: idle?.workspaceId
    val spacing = TokklTheme.spacing
    val focusManager = LocalFocusManager.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            FrameWidth,
            if (running != null) TokklTheme.extraColors.running else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        EntryFieldsEditor(
            fields = running?.fields ?: paused?.fields ?: idle?.fields ?: EntryFields(),
            labels = running?.labels ?: paused?.labels ?: idle?.labels ?: EntryLabels(),
            original = running?.original ?: paused?.original ?: Picked(),
            descriptionHint = stringResource(
                if (running != null ||
                    paused != null
                ) {
                    R.string.timer_description_running
                } else {
                    R.string.timer_description_idle
                },
            ),
            onDescriptionChange = onDescriptionChange,
            onDescriptionFinish = onDescriptionFinish,
            onPick = onPick,
            onBillableChange = onBillableChange,
            picker = { original, picked, onPickedChange, pickerModifier ->
                if (workspaceId != null) picker(workspaceId, original, picked, onPickedChange, pickerModifier)
            },
            // The server keeps a paused chain's fields as they were at Pause.
            enabled = workspaceId != null && paused == null,
            suggestions = suggestions,
            onSuggestion = onSuggestion,
            modifier = Modifier.padding(spacing.space3),
        ) {
            val stop = {
                onStop()
                focusManager.clearFocus()
            }
            when {
                running != null -> {
                    RunningLabel(unsent = running.unsent)
                    RunningClock(elapsedSeconds) {
                        focusManager.clearFocus()
                        onEditRunning()
                    }
                    if (running.pausable) {
                        TimerButton(
                            UiR.drawable.ic_pause,
                            UiR.string.timer_pause,
                            UiR.string.timer_pause_spoken,
                            TimerButtonLook.Neutral,
                            enabled = true,
                        ) {
                            onPause()
                            focusManager.clearFocus()
                        }
                    }
                    TimerButton(
                        UiR.drawable.ic_stop,
                        UiR.string.timer_stop,
                        UiR.string.timer_stop_spoken,
                        TimerButtonLook.Danger,
                        enabled = true,
                        onClick = stop,
                    )
                }

                paused != null -> {
                    PausedLabel()
                    PausedClock(paused.seconds)
                    TimerButton(
                        UiR.drawable.ic_play,
                        UiR.string.timer_resume,
                        UiR.string.timer_resume_spoken,
                        TimerButtonLook.Primary,
                        enabled = true,
                    ) {
                        onResume()
                        focusManager.clearFocus()
                    }
                    TimerButton(
                        UiR.drawable.ic_stop,
                        UiR.string.timer_stop,
                        UiR.string.timer_stop_spoken,
                        TimerButtonLook.Danger,
                        enabled = true,
                        onClick = stop,
                    )
                }

                else -> {
                    IdleClock()
                    TimerButton(
                        UiR.drawable.ic_play,
                        R.string.timer_start,
                        R.string.timer_start_spoken,
                        TimerButtonLook.Primary,
                        enabled = workspaceId != null,
                    ) {
                        onStart()
                        focusManager.clearFocus()
                    }
                }
            }
            TokklGhostButton(onClick = onAddEntry) { Text(stringResource(R.string.timer_add_entry)) }
            if (running != null || paused != null) {
                TokklGhostButton(onClick = {
                    focusManager.clearFocus()
                    onOpenFullscreen()
                }) {
                    Text(stringResource(R.string.timer_open_fullscreen))
                }
            }
            templates()
        }
    }
}
