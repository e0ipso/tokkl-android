package com.github.e0ipso.tokkl.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.catalog.ProblemBanner
import com.github.e0ipso.tokkl.catalog.ServerNeededNotice
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The client form, as the web app's ClientEditor: name and notes. Save with an empty name marks
 * the field with the web app's message and moves focus to it. While nothing can be changed, Save
 * is off and the form says why.
 */
@Composable
internal fun ClientFormContent(
    client: Client?,
    canSave: Boolean,
    sending: Boolean,
    problem: ServerProblem?,
    serverNeeded: Boolean,
    onRetry: () -> Unit,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    // A new form starts from its own values, not from the last one's.
    key(client?.id ?: NEW_FORM) {
        FormFields(client, canSave, sending, problem, serverNeeded, onRetry, onSave, onCancel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormFields(
    client: Client?,
    canSave: Boolean,
    sending: Boolean,
    problem: ServerProblem?,
    serverNeeded: Boolean,
    onRetry: () -> Unit,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(client?.name.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(client?.notes.orEmpty()) }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val save: () -> Unit = {
        if (name.isBlank()) {
            nameMissing = true
            nameFocus.requestFocus()
        } else {
            onSave(name, notes)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
        }
        PageTitle(stringResource(if (client == null) R.string.client_form_new else R.string.client_form_edit))
    }
    Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups)) {
        if (serverNeeded) {
            ServerNeededNotice(
                title = stringResource(R.string.clients_server_needed_title),
                message = stringResource(R.string.clients_server_needed_message),
                onRetry = onRetry,
            )
        }
        problem?.let { ProblemBanner(it) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(NAME_MAX)
                nameMissing = false
            },
            label = { Text(stringResource(R.string.client_name)) },
            supportingText = if (nameMissing) {
                { Text(stringResource(R.string.client_name_missing)) }
            } else {
                null
            },
            isError = nameMissing,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocus),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(NOTES_MAX) },
            label = { Text(stringResource(R.string.client_notes)) },
            minLines = NOTES_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TokklGhostButton(onClick = onCancel) { Text(stringResource(R.string.catalog_cancel)) }
            TokklButton(onClick = save, enabled = canSave && !sending) {
                Text(stringResource(if (client == null) R.string.client_create else R.string.client_save))
            }
        }
    }
}

private const val NEW_FORM = -1L

/** The web app's limits, which Tokkl enforces too. */
private const val NAME_MAX = 255
private const val NOTES_MAX = 10_000

private const val NOTES_MIN_LINES = 4
