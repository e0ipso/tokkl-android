package com.github.e0ipso.tokkl.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.github.e0ipso.tokkl.catalog.ProblemBanner
import com.github.e0ipso.tokkl.catalog.ServerNeededNotice
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.feature.projects.R
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.ui.CheckboxOptionRow
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.util.Locale
import com.github.e0ipso.tokkl.core.catalog.R as CatalogR

/**
 * The project form, as the web app's ProjectEditor: name, colour, client, hourly rate, currency,
 * estimated hours and billable by default. The client list comes from the clients on the phone;
 * an archived client shows only when the project has it. Save checks the fields, marks each
 * mistake under its field with the web app's message and moves focus to the first one. While
 * nothing can be changed, Save is off and the form says why. Under the form of a project the
 * server has come its [tasks].
 */
@Composable
internal fun ProjectFormContent(
    form: ProjectForm,
    projectCount: Int,
    clients: List<Client>,
    defaults: WorkspaceDefaults?,
    canSave: Boolean,
    sending: Boolean,
    problem: ServerProblem?,
    serverNeeded: Boolean,
    onRetry: () -> Unit,
    onSave: (ProjectFields) -> Unit,
    onCancel: () -> Unit,
    tasks: @Composable (ProjectDetails) -> Unit = {},
) {
    val project = (form as? ProjectForm.Edit)?.project
    // A new form starts from its own values, not from the last one's.
    key(project?.id ?: NEW_FORM) {
        FormFields(
            project,
            projectCount,
            clients,
            defaults,
            canSave,
            sending,
            problem,
            serverNeeded,
            onRetry,
            onSave,
            onCancel,
            tasks,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormFields(
    project: ProjectDetails?,
    projectCount: Int,
    clients: List<Client>,
    defaults: WorkspaceDefaults?,
    canSave: Boolean,
    sending: Boolean,
    problem: ServerProblem?,
    serverNeeded: Boolean,
    onRetry: () -> Unit,
    onSave: (ProjectFields) -> Unit,
    onCancel: () -> Unit,
    tasks: @Composable (ProjectDetails) -> Unit,
) {
    val initial = remember { ProjectDraft.of(project, ProjectPalette.forNewProject(projectCount)) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var custom by rememberSaveable { mutableStateOf(ProjectPalette.of(initial.color) == null) }
    // The saved state keeps the id's number: a Bundle cannot hold a ClientId.
    var clientIdValue by rememberSaveable { mutableStateOf(initial.clientId?.value) }
    val clientId = clientIdValue?.let(::ClientId)
    var billable by rememberSaveable { mutableStateOf(initial.billable) }
    var rate by rememberSaveable { mutableStateOf(initial.rate) }
    var currency by rememberSaveable { mutableStateOf(initial.currency) }
    var estimate by rememberSaveable { mutableStateOf(initial.estimate) }
    var mistakes by remember { mutableStateOf(emptyMap<ProjectField, Int>()) }
    val focus = remember { ProjectField.entries.associateWith { FocusRequester() } }
    val save: () -> Unit = {
        when (val check = ProjectDraft(name, color, clientId, billable, rate, currency, estimate).check()) {
            is DraftCheck.Valid -> {
                mistakes = emptyMap()
                onSave(check.fields)
            }

            is DraftCheck.Invalid -> {
                mistakes = check.mistakes
                focus.getValue(check.mistakes.keys.first()).requestFocus()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups)) {
        if (serverNeeded) {
            ServerNeededNotice(
                title = stringResource(R.string.projects_server_needed_title),
                message = stringResource(R.string.projects_server_needed_message),
                onRetry = onRetry,
            )
        }
        problem?.let { ProblemBanner(it) }
        FormTextField(
            value = name,
            onValueChange = {
                name = it.take(NAME_MAX)
                mistakes -= ProjectField.NAME
            },
            label = R.string.project_name,
            mistake = mistakes[ProjectField.NAME],
            focus = focus.getValue(ProjectField.NAME),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        ColourField(
            color = color,
            custom = custom,
            mistake = mistakes[ProjectField.COLOR],
            focus = focus.getValue(ProjectField.COLOR),
            onPalette = {
                color = it.hex
                custom = false
                mistakes -= ProjectField.COLOR
            },
            onCustom = { custom = true },
            onCustomChange = {
                color = it.trim().take(HEX_LENGTH)
                mistakes -= ProjectField.COLOR
            },
        )
        ClientField(
            clients = clients.filter { !it.archived || it.id == clientId },
            clientId = clientId,
            onChoose = { clientIdValue = it?.value },
        )
        FormTextField(
            value = rate,
            onValueChange = {
                rate = it
                mistakes -= ProjectField.RATE
            },
            label = R.string.project_rate,
            placeholder = defaults?.hourlyRate?.let { stringResource(R.string.project_rate_workspace, amountText(it)) }
                ?: stringResource(R.string.project_rate_none),
            mistake = mistakes[ProjectField.RATE],
            focus = focus.getValue(ProjectField.RATE),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        FormTextField(
            value = currency,
            onValueChange = {
                currency = it.uppercase(Locale.ROOT).take(CURRENCY_LENGTH)
                mistakes -= ProjectField.CURRENCY
            },
            label = R.string.project_currency,
            placeholder = defaults?.currency,
            mistake = mistakes[ProjectField.CURRENCY],
            focus = focus.getValue(ProjectField.CURRENCY),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            value = estimate,
            onValueChange = {
                estimate = it
                mistakes -= ProjectField.ESTIMATE
            },
            label = R.string.project_estimate,
            placeholder = stringResource(R.string.project_estimate_none),
            mistake = mistakes[ProjectField.ESTIMATE],
            focus = focus.getValue(ProjectField.ESTIMATE),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
        Column {
            CheckboxOptionRow(
                label = stringResource(R.string.project_billable_default),
                checked = billable,
                onCheckedChange = { billable = it },
            )
            Text(
                text = stringResource(R.string.project_defaults_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TokklGhostButton(onClick = onCancel) { Text(stringResource(CatalogR.string.catalog_cancel)) }
            TokklButton(onClick = save, enabled = canSave && !sending) {
                Text(stringResource(if (project == null) R.string.project_create else R.string.project_save))
            }
        }
        if (project != null) tasks(project)
    }
}

/**
 * A one-line field with its label above the value, an optional [placeholder] while it is empty,
 * and [mistake] under it, which TalkBack reads with the field.
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    mistake: Int?,
    focus: FocusRequester,
    keyboardOptions: KeyboardOptions,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = mistake?.let { { Text(stringResource(it)) } },
        isError = mistake != null,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

private const val NEW_FORM = -1L

/** The web app's limit, which Tokkl enforces too. */
private const val NAME_MAX = 255
private const val CURRENCY_LENGTH = 3

/** `#rrggbb`. */
private const val HEX_LENGTH = 7
