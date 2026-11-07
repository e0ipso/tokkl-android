package com.github.e0ipso.tokkl.pomodoro

import android.content.ActivityNotFoundException
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.feature.pomodoro.R
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.api.PhaseKind
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.catalog.CatalogPage
import com.github.e0ipso.tokkl.entryfields.EntryFieldsEditor
import com.github.e0ipso.tokkl.entryfields.EntryPicker
import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.entryfields.picked
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.picker.EntityPicker
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.timernotice.TimerNotice
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.StepperCard
import com.github.e0ipso.tokkl.ui.StepperUnit
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import kotlinx.coroutines.delay

/** The destination can be opened directly; the navigation item is added separately. */
@Composable
fun PomodoroScreen(
    model: PomodoroViewModel,
    cache: AccountCache,
    modifier: Modifier = Modifier,
    onNotificationStart: () -> Unit = {},
    onNotificationRetry: () -> Unit = {},
    notificationPermissionRevision: Int = 0,
) {
    val state by model.state.collectAsState()
    val projects by cache.projects.collectAsState(initial = emptyList())
    val tasks by cache.tasks.collectAsState(initial = emptyList())
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var fullScreenSettingsUnavailable by remember { mutableStateOf(false) }
    var exactAlarmSettingsUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(model, context, notificationPermissionRevision) {
        model.permissionsChanged(pomodoroPermissions(context))
    }
    DisposableEffect(model, lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.permissionsChanged(pomodoroPermissions(context))
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Capture the revision and pomodoro drawn by this composition, before any later state
    // emission or tap, so a tap on a page another device has moved on from is refused.
    val displayed = state.shown
    PomodoroContent(
        state = state,
        onLengths = model::editLengths,
        onFields = { model.editFields(it, displayed?.entryId) },
        onDescription = { model.editFields(it, displayed?.entryId, send = false) },
        onDescriptionDone = { model.fieldsDone(displayed?.entryId) },
        onStart = {
            model.permissionsChanged(pomodoroPermissions(context))
            model.start(onNotificationStart)
        },
        onNext = { model.next(displayed) },
        onEnd = { model.end(displayed) },
        projects = projects,
        tasks = tasks,
        picker = { original, picked, onPicked, pickerModifier ->
            state.workspaceId?.let { workspace ->
                EntityPicker(cache, workspace, original, picked, onPicked, pickerModifier)
            }
        },
        modifier = modifier,
        onFullScreenSettings = {
            pomodoroFullScreenSettings(context)?.let { settings ->
                try {
                    context.startActivity(settings)
                    fullScreenSettingsUnavailable = false
                } catch (_: ActivityNotFoundException) {
                    fullScreenSettingsUnavailable = true
                } catch (_: SecurityException) {
                    fullScreenSettingsUnavailable = true
                }
            }
        },
        fullScreenSettingsUnavailable = fullScreenSettingsUnavailable,
        onNotificationRetry = onNotificationRetry,
        onExactAlarmSettings = {
            try {
                context.startActivity(pomodoroExactAlarmSettings(context))
                exactAlarmSettingsUnavailable = false
            } catch (_: ActivityNotFoundException) {
                exactAlarmSettingsUnavailable = true
            } catch (_: SecurityException) {
                exactAlarmSettingsUnavailable = true
            }
        },
        exactAlarmSettingsUnavailable = exactAlarmSettingsUnavailable,
    )
}

/** One scrolling column keeps every action reachable at the largest font scale and with IME. */
@Composable
fun PomodoroContent(
    state: PomodoroPageState,
    onLengths: (PomodoroLengths) -> Unit,
    onFields: (EntryFields) -> Unit,
    onDescription: (EntryFields) -> Unit,
    onDescriptionDone: () -> Unit,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    projects: List<CachedProject> = emptyList(),
    tasks: List<CachedTask> = emptyList(),
    picker: EntryPicker = { _, _, _, _ -> },
    clock: Clock = Clock.systemUTC(),
    onFullScreenSettings: () -> Unit = {},
    fullScreenSettingsUnavailable: Boolean = false,
    onNotificationRetry: () -> Unit = {},
    onExactAlarmSettings: () -> Unit = {},
    exactAlarmSettingsUnavailable: Boolean = false,
) {
    val spacing = TokklTheme.spacing
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settle: (() -> Unit) -> Unit = { action ->
        // Flush the editor's focused description before the command captures its fields.
        focus.clearFocus()
        action()
    }
    CatalogPage(title = stringResource(SharedR.string.pomodoro_title), done = null, onDoneShown = {}, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.betweenGroups)) {
            PomodoroFeedback(state)
            if (state.notificationsDenied) {
                ErrorBanner(stringResource(R.string.pomodoro_notifications_denied))
                TokklGhostButton(onClick = onNotificationRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pomodoro_notifications_settings))
                }
            }
            if (state.alarmDenied) {
                TokklGhostButton(onClick = onExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pomodoro_exact_alarm_settings))
                }
                if (exactAlarmSettingsUnavailable) ErrorBanner(stringResource(R.string.pomodoro_exact_alarm_settings_unavailable))
            }
            if (state.fullScreenDenied) {
                ErrorBanner(stringResource(R.string.pomodoro_fullscreen_denied))
                TokklGhostButton(onClick = onFullScreenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pomodoro_fullscreen_settings))
                }
                if (fullScreenSettingsUnavailable) {
                    ErrorBanner(stringResource(R.string.pomodoro_fullscreen_settings_unavailable))
                }
            }
            if (state.loading) {
                Text(stringResource(R.string.pomodoro_loading))
            } else {
                val session = state.pomodoro
                val held = state.held
                if (session == null || held == null) {
                    PomodoroLengthForm(state.lengths, enabled = !state.busy, onChange = onLengths)
                } else {
                    var now by remember(clock, session.phase.start) { mutableStateOf(clock.instant()) }
                    LaunchedEffect(clock, session.phase.start, lifecycle) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            while (true) {
                                now = clock.instant()
                                delay(1000L - Math.floorMod(now.toEpochMilli(), 1000L))
                            }
                        }
                    }
                    val reading = held.phaseClockAt(now) ?: PomodoroClock(overtime = false, seconds = 0)
                    Text(
                        stringResource(phaseLabel(session.phase.kind)),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(SharedR.string.pomodoro_number, session.number, session.longRestEvery))
                    val duration = state.account?.formatDuration(reading.seconds) ?: clockDuration(reading.seconds)
                    val spoken = stringResource(
                        if (reading.overtime) R.string.pomodoro_overtime_clock else R.string.pomodoro_remaining_clock,
                        duration,
                    )
                    Text(stringResource(if (reading.overtime) SharedR.string.pomodoro_overtime else SharedR.string.pomodoro_remaining))
                    Text(
                        duration,
                        style = TokklTheme.clockTextStyle,
                        // The clock is readable on demand, without an announcement every second.
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
                    )
                    Text(stringResource(R.string.pomodoro_up_next, stringResource(phaseLabel(session.upcomingPhase))))
                }
                EntryFieldsEditor(
                    fields = state.fields,
                    labels = entryLabels(state.fields, projects, tasks),
                    original = state.fields.picked,
                    descriptionHint = stringResource(R.string.pomodoro_description_hint),
                    onDescriptionChange = { onDescription(state.fields.copy(description = it)) },
                    onDescriptionDone = onDescriptionDone,
                    onPick = { onFields(state.fields.withPicked(it, projects)) },
                    onBillableChange = { onFields(state.fields.copy(billable = it)) },
                    picker = picker,
                    enabled = !state.busy && state.workspaceId != null,
                )
                if (state.workspaceId == null) Text(stringResource(R.string.pomodoro_workspace_missing))
                if (session == null || held == null) {
                    TokklButton(onClick = { settle(onStart) }, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(SharedR.string.pomodoro_start))
                    }
                } else {
                    TokklButton(onClick = { settle(onNext) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(nextLabel(session.upcomingPhase)))
                    }
                    TokklGhostButton(onClick = { settle(onEnd) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(SharedR.string.pomodoro_end))
                    }
                }
            }
        }
    }
}

/** The form's width from which its cards sit two to a row, measured on the form and not the window. */
private val TwoColumnFormWidth = 360.dp

/** One stepper card of the length form: its short [label], [range], [unit], the raw draft and the change to it. */
private class LengthCard(
    @StringRes val label: Int,
    val range: IntRange,
    val unit: StepperUnit,
    val value: String,
    val onChange: (String) -> Unit,
)

/**
 * The four lengths as stepper cards, in a grid of two columns once the form itself is
 * [TwoColumnFormWidth] wide and of one below. The count follows the form's own constraints, so a
 * pane beside another screen gets the layout its width needs. A row's cards share its height.
 */
@Composable
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
fun PomodoroLengthForm(lengths: PomodoroLengths, enabled: Boolean, onChange: (PomodoroLengths) -> Unit) {
    val cards = listOf(
        LengthCard(SharedR.string.pomodoro_card_work, PomodoroDefaults.WORK_MINUTES, StepperUnit.MINUTES, lengths.work) { onChange(lengths.copy(work = it)) },
        LengthCard(SharedR.string.pomodoro_card_short_rest, PomodoroDefaults.SHORT_REST_MINUTES, StepperUnit.MINUTES, lengths.shortRest) { onChange(lengths.copy(shortRest = it)) },
        LengthCard(SharedR.string.pomodoro_card_long_rest, PomodoroDefaults.LONG_REST_MINUTES, StepperUnit.MINUTES, lengths.longRest) { onChange(lengths.copy(longRest = it)) },
        LengthCard(SharedR.string.pomodoro_card_long_rest_every, PomodoroDefaults.LONG_REST_EVERY, StepperUnit.WORK_PHASES, lengths.every) { onChange(lengths.copy(every = it)) },
    )
    BoxWithConstraints {
        val columns = if (maxWidth >= TwoColumnFormWidth) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
            for (row in cards.chunked(columns)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    for (card in row) {
                        StepperCard(
                            label = stringResource(card.label),
                            value = card.value,
                            range = card.range,
                            unit = card.unit,
                            onChange = card.onChange,
                            enabled = enabled,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PomodoroFeedback(state: PomodoroPageState) {
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        if (state.alarmDenied) ErrorBanner(stringResource(R.string.pomodoro_alarm_denied))
        // The standard message for a refusal, no connection or another failure, as the Timer page says it.
        state.problem?.let { problem ->
            ErrorBanner(timerNoticeText(LocalResources.current, TimerNotice.ActionFailed(problem.result, 0)))
        }
        state.unsentCount?.takeIf { it > 0 }?.let { count ->
            Text(
                pluralStringResource(R.plurals.pomodoro_unsent, count, count),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        for (change in state.refused) {
            ErrorBanner(stringResource(
                R.string.pomodoro_send_error,
                change.description.ifEmpty { stringResource(R.string.pomodoro_no_description) },
                change.refusal ?: stringResource(R.string.pomodoro_unknown_refusal),
            ))
        }
    }
}

@StringRes
private fun phaseLabel(kind: PhaseKind): Int = when (kind) {
    PhaseKind.WORK -> SharedR.string.pomodoro_work
    PhaseKind.SHORT_REST -> SharedR.string.pomodoro_short_rest
    PhaseKind.LONG_REST -> SharedR.string.pomodoro_long_rest
}

@StringRes
private fun nextLabel(kind: PhaseKind): Int = when (kind) {
    PhaseKind.WORK -> SharedR.string.pomodoro_start_work
    PhaseKind.SHORT_REST -> SharedR.string.pomodoro_start_short_rest
    PhaseKind.LONG_REST -> SharedR.string.pomodoro_start_long_rest
}

private fun clockDuration(seconds: Long): String = "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
