package com.github.e0ipso.tokkl.entry

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.ui.FillScreenWindow
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * No times, a switch across the whole row, as in Settings. TalkBack reads the row as one switch
 * with its label and state.
 */
@Composable
internal fun NoTimesSwitch(noTimes: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .toggleable(value = noTimes, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(stringResource(R.string.entry_no_times), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Switch(checked = noTimes, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A time or duration field, with its label above the value and an example while it is empty.
 * Losing focus calls [onDone], so the other times can follow it. A [mistake] shows under the
 * field as text, which TalkBack reads with the field and announces when it appears.
 */
@Composable
internal fun TimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    @StringRes label: Int,
    @StringRes hint: Int,
    mistake: TimeMistake?,
    enabled: Boolean,
    imeAction: ImeAction,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(hint)) },
        supportingText = mistake?.let {
            {
                Text(it.message(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        },
        isError = mistake != null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = imeAction),
        modifier = modifier.onFocusChanged { focus ->
            if (focused && !focus.isFocused) onDone()
            focused = focus.isFocused
        },
    )
}

@Composable
private fun TimeMistake.message(): String = when (this) {
    TimeMistake.TIMES -> stringResource(R.string.entry_mistake_times)
    TimeMistake.MANUAL_TIMES -> stringResource(R.string.entry_mistake_manual_times)
    TimeMistake.DURATION -> stringResource(R.string.entry_mistake_duration)
    TimeMistake.END_BEFORE_START -> stringResource(R.string.entry_mistake_end_before_start)
    TimeMistake.TOO_LONG -> stringResource(R.string.entry_mistake_too_long)
    TimeMistake.TOO_SHORT -> stringResource(R.string.entry_mistake_too_short)
    TimeMistake.NO_TIME_DURATION -> stringResource(R.string.entry_mistake_no_time_duration)
    is TimeMistake.NoTimeRoundsToZero -> stringResource(R.string.entry_mistake_no_time_rounds_to_zero, minutes)
    TimeMistake.NO_TIME_TOO_LONG -> stringResource(R.string.entry_mistake_no_time_too_long)
}

/**
 * The date, a button that opens Material's date picker, under its label. TalkBack reads it as
 * one button: "Date: Mon, 19 Oct 2026".
 */
@Composable
internal fun DateField(date: LocalDate, words: DateWords, enabled: Boolean, onDateChange: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val text = dateText(date, words, LocalResources.current)
    val spoken = stringResource(R.string.entry_date_spoken, text)
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
        Text(
            text = stringResource(R.string.entry_date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The button says it, so TalkBack reads the date once.
            modifier = Modifier.clearAndSetSemantics {},
        )
        TokklGhostButton(
            onClick = { picking = true },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = spoken },
        ) {
            Text(text)
        }
    }
    if (picking && enabled) {
        DateDialog(
            initial = date,
            onPick = {
                picking = false
                if (it != date) onDateChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Mon, 19 Oct 2026. */
internal fun dateText(date: LocalDate, words: DateWords, resources: Resources): String =
    resources.getString(R.string.entry_date_value, words.weekday(date), words.shortWithYear(date))

/**
 * Material's date picker over the whole screen, as the reports' custom range is, since a dialog
 * leaves its month grid no room at large font scales. Dates go in and out as UTC midnights, the
 * picker's own convention, so no zone of the phone touches them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.utcMillis())
    val picked = state.selectedDateMillis?.let(::utcDate)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FillScreenWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = TokklTheme.spacing.space1),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.entry_cancel))
                    }
                    Text(
                        text = stringResource(R.string.entry_date),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) {
                        Text(stringResource(R.string.entry_picker_done))
                    }
                }
                DatePicker(state = state, title = null, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

/** The narrowest a time field gets before the next one wraps below it. */
internal val TimeFieldMinWidth = 120.dp
