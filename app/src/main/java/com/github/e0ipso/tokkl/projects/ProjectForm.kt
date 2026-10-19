package com.github.e0ipso.tokkl.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.catalog.ProblemBanner
import com.github.e0ipso.tokkl.catalog.ServerNeededNotice
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.picker.projectColor
import com.github.e0ipso.tokkl.ui.CheckboxOptionRow
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.util.Locale

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
    clients: List<CachedClient>,
    defaults: WorkspaceDefaults?,
    canSave: Boolean,
    sending: Boolean,
    problem: ServerProblem?,
    serverNeeded: Boolean,
    onRetry: () -> Unit,
    onSave: (ProjectFields) -> Unit,
    onCancel: () -> Unit,
    tasks: @Composable (Project) -> Unit = {},
) {
    val project = (form as? ProjectForm.Edit)?.project
    // A new form starts from its own values, not from the last one's.
    key(project?.id ?: NEW_FORM) {
        FormFields(project, projectCount, clients, defaults, canSave, sending, problem, serverNeeded, onRetry, onSave, onCancel, tasks)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormFields(
    project: Project?,
    projectCount: Int,
    clients: List<CachedClient>,
    defaults: WorkspaceDefaults?,
    canSave: Boolean,
    sending: Boolean,
    problem: ServerProblem?,
    serverNeeded: Boolean,
    onRetry: () -> Unit,
    onSave: (ProjectFields) -> Unit,
    onCancel: () -> Unit,
    tasks: @Composable (Project) -> Unit,
) {
    val initial = remember { ProjectDraft.of(project, ProjectPalette.forNewProject(projectCount)) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var custom by rememberSaveable { mutableStateOf(ProjectPalette.of(initial.color) == null) }
    var clientId by rememberSaveable { mutableStateOf(initial.clientId) }
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
        }
        PageTitle(stringResource(if (project == null) R.string.project_form_new else R.string.project_form_edit))
    }
    Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
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
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
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
            onChoose = { clientId = it },
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
            TokklGhostButton(onClick = onCancel) { Text(stringResource(R.string.catalog_cancel)) }
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
private fun FormTextField(
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

/**
 * The colour, as the web app's ColorField: Toggl's 14 swatches and Custom, one choice. Each swatch
 * shows its colour with its name, and the chosen one carries a check mark and a heavier frame, so
 * the choice never rests on colour. Custom shows a field for any `#rrggbb` colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourField(
    color: String,
    custom: Boolean,
    mistake: Int?,
    focus: FocusRequester,
    onPalette: (ProjectPalette) -> Unit,
    onCustom: () -> Unit,
    onCustomChange: (String) -> Unit,
) {
    val chosen = if (custom) null else ProjectPalette.of(color)
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        Text(
            text = stringResource(R.string.project_colour),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            modifier = Modifier.selectableGroup(),
        ) {
            for (swatch in ProjectPalette.entries) {
                ColourOption(
                    label = stringResource(swatch.label),
                    hex = swatch.hex,
                    selected = swatch == chosen,
                    onSelect = { onPalette(swatch) },
                )
            }
            ColourOption(
                label = stringResource(R.string.project_colour_custom),
                hex = if (custom) color else null,
                selected = custom,
                onSelect = onCustom,
            )
        }
        if (custom) {
            FormTextField(
                value = color,
                onValueChange = onCustomChange,
                label = R.string.project_colour_custom_field,
                mistake = mistake,
                focus = focus,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
        }
    }
}

/** One colour choice: a dot in [hex], the [label] and, when [selected], a check mark in a heavier frame. */
@Composable
private fun ColourOption(label: String, hex: String?, selected: Boolean, onSelect: () -> Unit) {
    val frame = if (selected) {
        BorderStroke(FrameWidth, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(DividerDefaults.Thickness, MaterialTheme.colorScheme.outline)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(MaterialTheme.shapes.small)
            .border(frame, MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = TokklTheme.spacing.space3),
    ) {
        Box(
            Modifier
                .size(TokklTheme.spacing.space4)
                .background(projectColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(DividerDefaults.Thickness, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (selected) Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null)
    }
}

/** The client, from the clients on the phone, or No client. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientField(clients: List<CachedClient>, clientId: Long?, onChoose: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val noClient = stringResource(R.string.projects_no_client)
    val chosen = clients.firstOrNull { it.id == clientId }
    val shown = chosen?.let { clientLabel(it) } ?: noClient
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.project_client)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noClient) },
                onClick = {
                    onChoose(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            for (client in clients) {
                DropdownMenuItem(
                    text = { Text(clientLabel(client)) },
                    onClick = {
                        onChoose(client.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun clientLabel(client: CachedClient): String =
    if (client.archived) stringResource(R.string.project_client_archived, client.name) else client.name

private const val NEW_FORM = -1L

/** The web app's limit, which Tokkl enforces too. */
private const val NAME_MAX = 255
private const val CURRENCY_LENGTH = 3

/** `#rrggbb`. */
private const val HEX_LENGTH = 7
