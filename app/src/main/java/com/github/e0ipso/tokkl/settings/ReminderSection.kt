package com.github.e0ipso.tokkl.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reminder.ReminderPreferences
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** Whole-row switch and stacked dialog buttons remain reachable at the largest font scale. */
@Composable
internal fun ReminderSection(preferences: ReminderPreferences, change: (ReminderPreferences) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.reminder_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .toggleable(preferences.enabled, role = Role.Switch, onValueChange = { change(preferences.copy(enabled = it)) }),
    ) {
        Text(stringResource(R.string.reminder_enabled), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Switch(checked = preferences.enabled, onCheckedChange = null)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current)
        .clickable(role = Role.Button) { hours = preferences.hours.toString(); editing = true }, verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(R.plurals.reminder_after_hours, preferences.hours, preferences.hours), style = MaterialTheme.typography.bodyLarge)
    }
    if (editing) {
        // A new state each time the dialog opens. An attached label is drawn halfway above the
        // field, and this scroll starts at the top, so it can never bring that half into view.
        val state = rememberTextFieldState(hours)
        val text = state.text.toString()
        val value = text.toIntOrNull()?.takeIf { it in ReminderPreferences.HOURS }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.reminder_hours_label), modifier = Modifier.semantics { heading() }) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                ) {
                    OutlinedTextField(
                        state = state,
                        label = { Text(stringResource(R.string.reminder_hours_label)) },
                        labelPosition = TextFieldLabelPosition.Above(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        isError = value == null, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.reminder_hours_range))
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                    TokklGhostButton(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reminder_cancel)) }
                    TokklButton(
                        onClick = { value?.let { change(preferences.copy(hours = it)); editing = false } },
                        enabled = value != null, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.reminder_save))
                    }
                }
            },
        )
    }
}
