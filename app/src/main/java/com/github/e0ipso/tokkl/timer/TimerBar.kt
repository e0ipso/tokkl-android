package com.github.e0ipso.tokkl.timer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.DescriptionSuggestion
import com.github.e0ipso.tokkl.entry.EntryFieldsEditor
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.descriptionSuggestions
import com.github.e0ipso.tokkl.favorites.FavoritesModel
import com.github.e0ipso.tokkl.favorites.TemplatesPopup
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklDangerButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.UnsentIcon
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * The project, task and tag picker for an entry in [workspaceId], as [EntityPicker] takes it.
 */
typealias WorkspacePicker =
    @Composable (workspaceId: Long, original: Picked, picked: Picked, onPickedChange: (Picked) -> Unit, modifier: Modifier) -> Unit

/**
 * The timer at the top of the Timer page (docs/PRD.md T1), from [model], with the cached
 * projects, tasks and tags of [cache] in its picker. While an entry runs, the clock counts from
 * its start on the phone, a tick at each whole second while the screen shows, and asks the server
 * nothing (principle 3). Its button for a past entry calls [onAddEntry] (M1). Its timer-templates
 * icon, when [favorites] is set, opens a popup of cached templates (T5). Choosing one starts it
 * through the timer. Save as template stores what the timer shows, and Manage templates calls
 * [onManageTemplates].
 */
@Composable
fun TimerBar(
    model: TimerModel,
    cache: AccountCache,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    favorites: FavoritesModel? = null,
    onManageTemplates: () -> Unit = {},
    clock: Clock = Clock.systemUTC(),
) {
    val state by model.state.collectAsState()
    // The next frame starts once the frame that composed a new state has drawn it. That ends the
    // trace section of the Start or Stop tap that made the state.
    LaunchedEffect(model, state) {
        withFrameNanos {}
        model.shown(state)
    }
    val running = state as? TimerState.Running
    val elapsed = (running?.accumulatedSeconds ?: 0) + rememberElapsedSeconds(running?.start, clock)
    val focusManager = LocalFocusManager.current
    val timerFields = when (val shown = state) {
        is TimerState.Running -> shown.fields
        is TimerState.Paused -> shown.fields
        is TimerState.Idle -> shown.fields
        TimerState.Loading -> null
    }
    Column(modifier) {
        TimerBarCore(model, cache, state, elapsed, onAddEntry) {
            val templates = favorites
            if (templates != null) {
                TemplatePopup(templates, timerFields, onManageTemplates) { fields ->
                    model.startFrom(fields)
                    settleDescription(focusManager)
                }
            }
        }
    }
}

/** The panel popup for [favorites]. A choice starts through [onStart], which ends a pause chain. */
@Composable
private fun TemplatePopup(
    favorites: FavoritesModel,
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
    model: TimerModel,
    cache: AccountCache,
    state: TimerState,
    elapsed: Long,
    onAddEntry: () -> Unit,
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
 * Pause and Stop. While paused (docs/PRD.md T1) come the "Paused" label, the paused total, which
 * stands still, Resume and Stop. The frame takes the running colour while an entry runs, and the
 * label and the Stop button's square say so too, so colour is never the only sign. Until the
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
            enabled = workspaceId != null,
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
                    RunningClock(elapsedSeconds)
                    TimerButton(R.drawable.ic_pause, R.string.timer_pause, R.string.timer_pause_spoken, TimerButtonLook.Neutral, enabled = true) {
                        onPause()
                        focusManager.clearFocus()
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
            templates()
        }
    }
}

/**
 * "Running" in the running colour beside a dot, and while the server lacks a change to the entry,
 * the unsent icon and NOT SENT, as the history marks it. TalkBack reads the words.
 */
@Composable
private fun RunningLabel(unsent: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val colour = TokklTheme.extraColors.running
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
        Canvas(Modifier.size(TokklTheme.spacing.space2)) { drawCircle(colour) }
        Text(stringResource(R.string.timer_running), style = MaterialTheme.typography.labelLarge, color = colour)
        if (unsent) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
                UnsentIcon(TokklTheme.smallCapsLabelTextStyle)
                Text(
                    text = stringResource(R.string.history_unsent).uppercase(locale),
                    style = TokklTheme.smallCapsLabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The running time, which TalkBack reads in words: "1 hour, 5 minutes elapsed". */
@Composable
private fun RunningClock(elapsedSeconds: Long) {
    val spoken = stringResource(R.string.timer_elapsed_spoken, spokenDuration(LocalResources.current, elapsedSeconds))
    Text(
        text = formatDuration(elapsedSeconds),
        style = TokklTheme.clockTextStyle,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

/** The clock at zero while no timer runs, which TalkBack skips, since Start says what it does. */
@Composable
private fun IdleClock() {
    Text(
        text = formatDuration(0),
        style = TokklTheme.clockTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** "Paused" in the muted colour, as the web app says it beside the frozen clock. */
@Composable
private fun PausedLabel() {
    Text(stringResource(R.string.timer_paused), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The paused total, which stands still, and which TalkBack reads as "1 hour elapsed, paused". */
@Composable
private fun PausedClock(seconds: Long) {
    val spoken = stringResource(R.string.timer_paused_elapsed_spoken, spokenDuration(LocalResources.current, seconds))
    Text(
        text = formatDuration(seconds),
        style = TokklTheme.clockTextStyle,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

/** How a [TimerButton] looks: Start and Resume on primary, Pause outlined, Stop on danger. */
private enum class TimerButtonLook { Primary, Neutral, Danger }

/** A timer button in its [look]: an icon and a word, and the whole action to TalkBack. */
@Composable
private fun TimerButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes spoken: Int,
    look: TimerButtonLook,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(spoken)
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(TokklTheme.spacing.space5))
        Text(stringResource(label), modifier = Modifier.padding(start = TokklTheme.spacing.space2))
    }
    val semantics = Modifier.semantics { contentDescription = description }
    when (look) {
        TimerButtonLook.Primary -> TokklButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
        TimerButtonLook.Neutral -> TokklGhostButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
        TimerButtonLook.Danger -> TokklDangerButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
    }
}

/**
 * Whole seconds from [start] to now on [clock], moved on at each whole second while the screen
 * shows, or zero without [start]. Nothing here asks the server.
 */
@Composable
private fun rememberElapsedSeconds(start: Instant?, clock: Clock): Long {
    var now by remember(clock) { mutableStateOf(clock.instant()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (start != null) {
        LaunchedEffect(start, clock, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = clock.instant()
                    delay(SECOND_MS - Math.floorMod(Duration.between(start, now).toMillis(), SECOND_MS))
                }
            }
        }
    }
    return if (start == null) 0L else elapsedSeconds(start, now)
}

/** Whole seconds from [start] to [now], as the web app's entrySeconds counts, and never below zero. */
internal fun elapsedSeconds(start: Instant, now: Instant): Long =
    maxOf(0L, Math.floorDiv(Duration.between(start, now).toMillis(), SECOND_MS))

private const val SECOND_MS = 1_000L
