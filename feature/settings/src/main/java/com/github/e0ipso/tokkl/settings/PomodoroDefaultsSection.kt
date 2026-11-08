package com.github.e0ipso.tokkl.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.github.e0ipso.tokkl.feature.settings.R
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.ui.StepperCard
import com.github.e0ipso.tokkl.ui.StepperUnit
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** Each default has a labelled, validated editor; a draft never mutates the stored defaults. */
@Composable
internal fun ColumnScope.PomodoroDefaultsSection(
    defaults: PomodoroDefaults,
    sharedWithWeb: Boolean,
    change: (PomodoroDefaults) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Text(
        stringResource(R.string.settings_pomodoro),
        style = MaterialTheme.typography.titleMedium,
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
                .clickable(role = Role.Button) { editing = field.name }
                .padding(vertical = TokklTheme.spacing.space2),
        ) {
            Text(stringResource(field.label), style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(
                    if (field ==
                        PomodoroDefaultField.EVERY
                    ) {
                        R.plurals.settings_pomodoro_phases
                    } else {
                        R.plurals.settings_pomodoro_minutes
                    },
                    value,
                    value,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val field = editing?.let { name -> PomodoroDefaultField.entries.firstOrNull { it.name == name } }
    if (field != null) {
        // The dialog's own draft, a new one each time it opens: the dialog leaves the composition
        // on Cancel, on dismissal and on Save, and the draft goes with it. A refresh of the
        // stored defaults while the dialog is open does not touch it.
        var text by rememberSaveable(field.name) { mutableStateOf(field.value(defaults).toString()) }
        val valid = field.parse(text) != null
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(
                    stringResource(R.string.settings_pomodoro_edit),
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StepperCard(
                        label = stringResource(field.cardLabel),
                        value = text,
                        range = field.range,
                        unit = field.unit,
                        onChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
                ) {
                    TokklGhostButton(onClick = { editing = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_pomodoro_cancel))
                    }
                    TokklButton(
                        onClick = {
                            field.edited(defaults, text)?.let {
                                change(it)
                                editing = null
                            }
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_pomodoro_save)) }
                }
            },
        )
    }
}

/** The field's label in Settings' editor of the Pomodoro defaults. */
@get:StringRes
val PomodoroDefaultField.label: Int get() = when (this) {
    PomodoroDefaultField.WORK -> R.string.pomodoro_work_minutes
    PomodoroDefaultField.SHORT_REST -> R.string.pomodoro_short_rest_minutes
    PomodoroDefaultField.LONG_REST -> R.string.pomodoro_long_rest_minutes
    PomodoroDefaultField.EVERY -> R.string.pomodoro_long_rest_every
}

/** The short label on the field's stepper card, as the Pomodoro page's length cards have it. */
@get:StringRes
val PomodoroDefaultField.cardLabel: Int get() = when (this) {
    PomodoroDefaultField.WORK -> UiR.string.pomodoro_card_work
    PomodoroDefaultField.SHORT_REST -> UiR.string.pomodoro_card_short_rest
    PomodoroDefaultField.LONG_REST -> UiR.string.pomodoro_card_long_rest
    PomodoroDefaultField.EVERY -> UiR.string.pomodoro_card_long_rest_every
}

internal val PomodoroDefaultField.unit: StepperUnit get() =
    if (this == PomodoroDefaultField.EVERY) StepperUnit.WORK_PHASES else StepperUnit.MINUTES
