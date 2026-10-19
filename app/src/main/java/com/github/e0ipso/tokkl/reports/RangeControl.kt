package com.github.e0ipso.tokkl.reports

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.ui.RadioOptionRow
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The range of the report: buttons that step it by its own length on either side of a button
 * that names it and opens the presets and the custom range. As the web app's RangeControl, the
 * button names the preset the range matches today, when it matches one.
 *
 * [selection] is null while there is no range, and then every control is off. [time] gives
 * today and the week start in the account's timezone. [presets] are the ones the dialog offers.
 */
@Composable
internal fun RangeControl(
    selection: RangeSelection?,
    time: AccountTime?,
    onShift: (Int) -> Unit,
    onPreset: (RangePreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<RangePreset> = RangePreset.entries,
) {
    var presetsOpen by rememberSaveable { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val enabled = selection != null && time != null
    val today = time?.today()
    val active = selection?.preset
        ?: if (selection != null && time != null) RangePreset.matching(selection.range, time.today(), time.firstDayOfWeek) else null
    val words = DateWords(LocalResources.current)
    val rangeText = if (selection != null && today != null) words.range(selection.range, today) else ""
    val changeLabel = stringResource(R.string.reports_range_change)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = { onShift(-1) }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.reports_previous_period))
        }
        OutlinedButton(
            onClick = { presetsOpen = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .semantics { onClick(label = changeLabel) { presetsOpen = true; true } },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (active != null) {
                    Text(
                        text = stringResource(PRESET_LABELS.getValue(active)),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (active == null) FontWeight.Bold else FontWeight.Normal),
                    color = if (active == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        IconButton(onClick = { onShift(1) }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.reports_next_period))
        }
    }

    if (presetsOpen && enabled) {
        PresetDialog(
            presets = presets,
            active = active,
            onPreset = { presetsOpen = false; onPreset(it) },
            onCustom = { presetsOpen = false; customOpen = true },
            onDismiss = { presetsOpen = false },
        )
    }
    if (customOpen && selection != null && today != null) {
        CustomRangeDialog(
            initial = selection.range,
            today = today,
            onApply = { customOpen = false; onCustom(it) },
            onDismiss = { customOpen = false },
        )
    }
}

/** The presets as radio buttons, which apply on a tap, and a button that opens the custom range. */
@Composable
private fun PresetDialog(presets: List<RangePreset>, active: RangePreset?, onPreset: (RangePreset) -> Unit, onCustom: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reports_range_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Radio buttons: the selected one differs in shape, not only in colour.
                Column(Modifier.selectableGroup()) {
                    for (preset in presets) {
                        RadioOptionRow(
                            label = stringResource(PRESET_LABELS.getValue(preset)),
                            selected = preset == active,
                            onSelect = { onPreset(preset) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                TextButton(onClick = onCustom, modifier = Modifier.padding(top = TokklTheme.spacing.inGroup)) {
                    Text(stringResource(R.string.reports_custom_range))
                }
            }
        },
        // One button in the slot, so nothing can overlap at any font scale.
        confirmButton = { DialogButton(R.string.reports_cancel, onDismiss) },
    )
}

/**
 * Material's date range picker, full screen as Material lays it out, since a dialog leaves its
 * month grid no room at large font scales. The headline names the range in the screen's words.
 * Dates go in and out as UTC midnights, the picker's own convention, so no zone of the phone
 * touches them. Apply is off until the range has both ends and spans at most [MAX_RANGE_DAYS]
 * days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(initial: ReportRange, today: LocalDate, onApply: (ReportRange) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.from.utcMillis(),
        initialSelectedEndDateMillis = initial.to.utcMillis(),
    )
    val start = state.selectedStartDateMillis?.let(::utcDate)
    val end = state.selectedEndDateMillis?.let(::utcDate)
    val picked = if (start != null && end != null && !end.isBefore(start)) ReportRange(start, end) else null
    val tooLong = picked != null && picked.days > MAX_RANGE_DAYS
    val words = DateWords(LocalResources.current)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FillScreenWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = TokklTheme.spacing.space1),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.reports_cancel))
                    }
                    Text(
                        text = stringResource(R.string.reports_custom_range_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    TextButton(onClick = { picked?.let(onApply) }, enabled = picked != null && !tooLong) {
                        Text(stringResource(R.string.reports_apply))
                    }
                }
                DateRangePicker(
                    state = state,
                    title = null,
                    headline = {
                        Column(
                            modifier = Modifier.padding(
                                start = TokklTheme.spacing.space6,
                                end = TokklTheme.spacing.space3,
                                bottom = TokklTheme.spacing.space3,
                            ),
                            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
                        ) {
                            Text(
                                text = when {
                                    start == null -> stringResource(R.string.reports_custom_range_pick_start)
                                    picked == null -> stringResource(R.string.reports_custom_range_pick_end, words.shortWithYear(start))
                                    else -> words.range(picked, today)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            if (tooLong) {
                                Text(
                                    text = pluralStringResource(R.plurals.reports_custom_range_too_long, MAX_RANGE_DAYS, MAX_RANGE_DAYS),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Makes the dialog's window as tall as the screen instead of as tall as its content. Compose
 * leaves a full-width dialog's window at WRAP_CONTENT height, and Android 11 then measured it in
 * every layout pass twice: at the width of `screenWidthDp`, which rounds to 1079 px on a 1080 px
 * screen, and again at the window's 1080 px. With the date range picker's month list inside,
 * that never settled: the dialog laid itself out on every frame and the main thread never went
 * idle. Without this, Android 12 (API 31) still never went idle there. The Surface inside
 * already fills the window, so the window keeps its size.
 */
@Composable
private fun FillScreenWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null && window.attributes.height != MATCH_PARENT) window.setLayout(MATCH_PARENT, MATCH_PARENT)
    }
}

@Composable
private fun DialogButton(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

/** The web app's PRESET_LABELS. */
internal val PRESET_LABELS: Map<RangePreset, Int> = mapOf(
    RangePreset.TODAY to R.string.reports_preset_today,
    RangePreset.YESTERDAY to R.string.reports_preset_yesterday,
    RangePreset.THIS_WEEK to R.string.reports_preset_this_week,
    RangePreset.LAST_WEEK to R.string.reports_preset_last_week,
    RangePreset.THIS_MONTH to R.string.reports_preset_this_month,
    RangePreset.LAST_MONTH to R.string.reports_preset_last_month,
    RangePreset.LAST_30_DAYS to R.string.reports_preset_last_30_days,
    RangePreset.THIS_YEAR to R.string.reports_preset_this_year,
    RangePreset.LAST_YEAR to R.string.reports_preset_last_year,
)
