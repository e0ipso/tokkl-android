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
import com.github.e0ipso.tokkl.favorites.Favorites
import com.github.e0ipso.tokkl.favorites.FavoritesModel
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
 * nothing (principle 3). Its button for a past entry calls [onAddEntry] (M1). Under it come the
 * [favorites] (T5), which start through the timer and pin what it shows.
 */
@Composable
fun TimerBar(
    model: TimerModel,
    cache: AccountCache,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    favorites: FavoritesModel? = null,
    clock: Clock = Clock.systemUTC(),
) {
    val state by model.state.collectAsState()
    // The next frame starts once the frame that composed a new state has drawn it. That ends the
    // trace section of the Start or Stop tap that made the state.
    LaunchedEffect(model, state) {
        withFrameNanos {}
        model.shown(state)
    }
    val elapsed = rememberElapsedSeconds((state as? TimerState.Running)?.start, clock)
    Column(modifier) {
        TimerBarCore(model, cache, state, elapsed, onAddEntry)
        if (favorites != null) {
            val shown = state
            Favorites(
                model = favorites,
                timerFields = (shown as? TimerState.Running)?.fields ?: (shown as? TimerState.Idle)?.fields,
                onStart = model::startFrom,
                modifier = Modifier.padding(top = TokklTheme.spacing.betweenGroups),
            )
        }
    }
}

@Composable
private fun TimerBarCore(model: TimerModel, cache: AccountCache, state: TimerState, elapsed: Long, onAddEntry: () -> Unit) {
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
    )
}

/**
 * The timer as the web app's TimerBar.tsx draws it, for [state]: the entry's fields, then the
 * clock and Start, or while an entry runs, the "Running" label, the clock at [elapsedSeconds] and
 * Stop. The frame takes the running colour while an entry runs, and the label and the Stop
 * button's square say so too, so colour is never the only sign. Until the phone knows the
 * account's workspace, which the first account settings bring, the fields and Start are disabled.
 * After Start or Stop comes the button that adds a past entry by hand, as the web app's bar holds
 * its manual mode (M1), whether an entry runs or not. With [suggestions], the description field
 * suggests recent entries as it is typed in (T4), and a pick goes to [onSuggestion].
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
) {
    val running = state as? TimerState.Running
    val idle = state as? TimerState.Idle
    val workspaceId = running?.workspaceId ?: idle?.workspaceId
    val spacing = TokklTheme.spacing
    val focusManager = LocalFocusManager.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(FrameWidth, if (running != null) TokklTheme.extraColors.running else MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        EntryFieldsEditor(
            fields = running?.fields ?: idle?.fields ?: EntryFields(),
            labels = running?.labels ?: idle?.labels ?: EntryLabels(),
            original = running?.original ?: Picked(),
            descriptionHint = stringResource(if (running != null) R.string.timer_description_running else R.string.timer_description_idle),
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
            if (running != null) {
                RunningLabel(unsent = running.unsent)
                RunningClock(elapsedSeconds)
                TimerButton(R.drawable.ic_stop, R.string.timer_stop, R.string.timer_stop_spoken, danger = true, enabled = true) {
                    onStop()
                    focusManager.clearFocus()
                }
            } else {
                IdleClock()
                TimerButton(R.drawable.ic_play, R.string.timer_start, R.string.timer_start_spoken, danger = false, enabled = workspaceId != null) {
                    onStart()
                    focusManager.clearFocus()
                }
            }
            TokklGhostButton(onClick = onAddEntry) { Text(stringResource(R.string.timer_add_entry)) }
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

/** Start, on primary, or Stop, on danger: an icon and a word, and the whole action to TalkBack. */
@Composable
private fun TimerButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes spoken: Int,
    danger: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(spoken)
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(TokklTheme.spacing.space5))
        Text(stringResource(label), modifier = Modifier.padding(start = TokklTheme.spacing.space2))
    }
    val semantics = Modifier.semantics { contentDescription = description }
    if (danger) {
        TokklDangerButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
    } else {
        TokklButton(onClick = onClick, enabled = enabled, modifier = semantics, content = content)
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
