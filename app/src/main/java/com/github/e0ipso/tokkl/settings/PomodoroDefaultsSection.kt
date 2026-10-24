package com.github.e0ipso.tokkl.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** Each default has a labelled, validated editor; a draft never mutates the stored defaults. */
@Composable
internal fun PomodoroDefaultsSection(defaults: PomodoroDefaults, sharedWithWeb: Boolean, change: (PomodoroDefaults) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    Text(
        stringResource(R.string.settings_pomodoro), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(if (sharedWithWeb) R.string.settings_pomodoro_shared else R.string.settings_pomodoro_local),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(TokklTheme.spacing.inGroup))
    for (field in PomodoroDefaultField.entries) {
        val value = field.value(defaults)
        Column(
            Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(role = Role.Button) { input = value.toString(); editing = field.name }
                .padding(vertical = TokklTheme.spacing.space2),
        ) {
            Text(stringResource(field.label), style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(if (field == PomodoroDefaultField.EVERY) R.plurals.settings_pomodoro_phases else R.plurals.settings_pomodoro_minutes, value, value),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val field = editing?.let { name -> PomodoroDefaultField.entries.firstOrNull { it.name == name } }
    if (field != null) {
        val valid = field.parse(input) != null
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.settings_pomodoro_edit), modifier = Modifier.semantics { heading() }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        // A minimized attached label is placed halfway above this field. The dialog
                        // scroll starts at the top and cannot bring that part into view.
                        label = { Text(stringResource(field.label)) },
                        labelPosition = TextFieldLabelPosition.Above(),
                        supportingText = { Text(stringResource(R.string.pomodoro_length_range, field.range.first, field.range.last)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                    TokklGhostButton(onClick = { editing = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_pomodoro_cancel))
                    }
                    TokklButton(
                        onClick = { field.edited(defaults, input)?.let { change(it); editing = null } },
                        enabled = valid, modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_pomodoro_save)) }
                }
            },
        )
    }
}

@get:StringRes
internal val PomodoroDefaultField.label: Int get() = when (this) {
    PomodoroDefaultField.WORK -> R.string.pomodoro_work_minutes
    PomodoroDefaultField.SHORT_REST -> R.string.pomodoro_short_rest_minutes
    PomodoroDefaultField.LONG_REST -> R.string.pomodoro_long_rest_minutes
    PomodoroDefaultField.EVERY -> R.string.pomodoro_long_rest_every
}
