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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.DescriptionSuggestion
import com.github.e0ipso.tokkl.entry.EntryFieldsEditor
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.descriptionSuggestions
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.favorites.TemplatesPopup
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock

/**
 * The project, task and tag picker for an entry in [workspaceId], as [EntityPicker] takes it.
 */
typealias WorkspacePicker =
    @Composable (workspaceId: Long, original: Picked, picked: Picked, onPickedChange: (Picked) -> Unit, modifier: Modifier) -> Unit

/**
 * The timer at the top of the Timer page (docs/PRD.md T1), from [model], with the cached
 * projects, tasks and tags of [cache] in its picker. While an entry runs, the clock counts from
 * its start on the phone, a tick at each whole second while the screen shows, and asks the server
 * nothing (principle 3). Its clock opens [onEditRunning]. Its button for a past entry calls [onAddEntry]
 * (M1). Its timer-templates
 * icon, when [favorites] is set, opens a popup of cached templates (T5). Choosing one starts it
 * through the timer. Save as template stores what the timer shows, and Manage templates calls
 * [onManageTemplates].
 */
@Composable
fun TimerBar(
    model: TimerViewModel,
    cache: AccountCache,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    favorites: FavoritesViewModel? = null,
    onManageTemplates: () -> Unit = {},
    clock: Clock = Clock.systemUTC(),
    onEditRunning: () -> Unit = {},
    onOpenFullscreen: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    // The next frame starts once the frame that composed a new state has drawn it. That ends the
    // trace section of the Start or Stop tap that made the state.
    LaunchedEffect(model, state) {
        withFrameNanos {}
        model.shown(state)
    }
    val running = state as? TimerState.Running
    val elapsed = (running?.accumulatedSeconds ?: 0) + rememberElapsedSeconds(running?.countsFrom, clock)
    val focusManager = LocalFocusManager.current
    val timerFields = when (val shown = state) {
        is TimerState.Running -> shown.fields
        is TimerState.Paused -> shown.fields
        is TimerState.Idle -> shown.fields
        TimerState.Loading -> null
    }
    Column(modifier) {
        TimerBarCore(model, cache, state, elapsed, onAddEntry, onEditRunning, onOpenFullscreen) {
            val templates = favorites
            if (templates != null) {
                TemplatePopup(templates, timerFields, onManageTemplates) { fields ->
                    model.startFrom(fields)
                    settleDescription(focusManager)
                }
            }
        }
        TimerTotals(cache.timeEntries, model.accountSettings, clock)
    }
}

/** The panel popup for [favorites]. A choice starts through [onStart], which ends a pause chain. */
@Composable
private fun TemplatePopup(
    favorites: FavoritesViewModel,
    timerFields: EntryFields?,
    onManageTemplates: () -> Unit,
    onStart: (EntryFields) -> Unit,
) {
    val state by favorites.state.collectAsState()
    TemplatesPopup(
        state = state,
        timerFields = timerFields,
        onStart = onStart,
        onSave = { fields, onDone -> favorites.add(fields, onDone) },
        onManage = onManageTemplates,
    )
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

@Composable
private fun TimerBarCore(
    model: TimerViewModel,
    cache: AccountCache,
    state: TimerState,
    elapsed: Long,
    onAddEntry: () -> Unit,
    onEditRunning: () -> Unit,
    onOpenFullscreen: () -> Unit,
    templates: @Composable () -> Unit,
) {
    val entries by cache.timeEntries.collectAsState(initial = emptyList())
    val projects by cache.projects.collectAsState(initial = emptyList())
    val clients by cache.clients.collectAsState(initial = emptyList())
    TimerBarContent(
        suggestions = { query -> descriptionSuggestions(entries, projects, clients, query) },
        onSuggestion = { model.fill(it.fields) },
        state = state,
        elapsedSeconds = elapsed,
        onDescriptionChange = model::editDescription,
        onDescriptionDone = model::descriptionDone,
        onPick = model::pick,
        onBillableChange = model::setBillable,
        onStart = model::start,
        onStop = model::stop,
        onAddEntry = onAddEntry,
        onEditRunning = onEditRunning,
        onOpenFullscreen = onOpenFullscreen,
        picker = { workspaceId, original, picked, onPickedChange, pickerModifier ->
            EntityPicker(cache, workspaceId, original, picked, onPickedChange, pickerModifier)
        },
        onPause = model::pause,
        onResume = model::resume,
        templates = templates,
    )
}

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
    onDescriptionDone: () -> Unit,
    onPick: (Picked) -> Unit,
    onBillableChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAddEntry: () -> Unit,
    picker: WorkspacePicker,
    modifier: Modifier = Modifier,
    suggestions: ((String) -> List<DescriptionSuggestion>)? = null,
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
        border = BorderStroke(FrameWidth, if (running != null) TokklTheme.extraColors.running else MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        EntryFieldsEditor(
            fields = running?.fields ?: paused?.fields ?: idle?.fields ?: EntryFields(),
            labels = running?.labels ?: paused?.labels ?: idle?.labels ?: EntryLabels(),
            original = running?.original ?: paused?.original ?: Picked(),
            descriptionHint = stringResource(if (running != null || paused != null) R.string.timer_description_running else R.string.timer_description_idle),
            onDescriptionChange = onDescriptionChange,
            onDescriptionDone = onDescriptionDone,
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
                TapTrace.diagnostic?.invoke("timer.stop.handler.enter actual_touch_receipt=UNKNOWN")
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
                        TimerButton(R.drawable.ic_pause, R.string.timer_pause, R.string.timer_pause_spoken, TimerButtonLook.Neutral, enabled = true) {
                            onPause()
                            focusManager.clearFocus()
                        }
                    }
                    TimerButton(R.drawable.ic_stop, R.string.timer_stop, R.string.timer_stop_spoken, TimerButtonLook.Danger, enabled = true, onClick = stop)
                }
                paused != null -> {
                    PausedLabel()
                    PausedClock(paused.seconds)
                    TimerButton(R.drawable.ic_play, R.string.timer_resume, R.string.timer_resume_spoken, TimerButtonLook.Primary, enabled = true) {
                        onResume()
                        focusManager.clearFocus()
                    }
                    TimerButton(R.drawable.ic_stop, R.string.timer_stop, R.string.timer_stop_spoken, TimerButtonLook.Danger, enabled = true, onClick = stop)
                }
                else -> {
                    IdleClock()
                    TimerButton(R.drawable.ic_play, R.string.timer_start, R.string.timer_start_spoken, TimerButtonLook.Primary, enabled = workspaceId != null) {
                        onStart()
                        focusManager.clearFocus()
                    }
                }
            }
            TokklGhostButton(onClick = onAddEntry) { Text(stringResource(R.string.timer_add_entry)) }
            if (running != null || paused != null) {
                TokklGhostButton(onClick = { focusManager.clearFocus(); onOpenFullscreen() }) {
                    Text(stringResource(R.string.timer_open_fullscreen))
                }
            }
            templates()
        }
    }
}
