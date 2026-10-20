package com.github.e0ipso.tokkl.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.entry.SplitProblem
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * Asks where to split an entry (docs/PRD.md H6), as the "Split at" field of the web app's
 * EntryEditor.tsx: the entry and its times, the field, labelled for TalkBack, with the halves'
 * durations under it once the time cuts the entry, or why it does not after Split. Cancel and
 * Split sit stacked at full width, as in [com.github.e0ipso.tokkl.catalog.ConfirmDialog], so they
 * cannot overlap at any font scale. The field takes focus, so the keyboard opens with the dialog.
 */
@Composable
internal fun SplitEntryDialog(
    dialog: SplitDialog,
    settings: AccountSettings,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val name = dialog.source.description.ifBlank { stringResource(R.string.history_no_description) }
    val start = settings.formatTime(dialog.whole.start, dialog.zone)
    val stop = settings.formatTime(dialog.whole.stop, dialog.zone)
    val spoken = stringResource(R.string.history_split_entry, name, stringResource(R.string.history_time_range_spoken, start, stop))
    val problem = dialog.problem
    val halves = dialog.halves
    val note: String? = when {
        problem != null -> problemText(problem)
        halves != null -> stringResource(R.string.history_split_preview, duration(halves.first, settings), duration(halves.second, settings))
        else -> null
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.history_split_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
            ) {
                Text(
                    text = stringResource(R.string.history_split_entry, name, stringResource(R.string.history_time_range, start, stop)),
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
                OutlinedTextField(
                    value = dialog.text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.history_split_field)) },
                    placeholder = { Text(stringResource(R.string.history_split_placeholder)) },
                    supportingText = note?.let { { Text(it) } },
                    isError = problem != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                TokklGhostButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.catalog_cancel), textAlign = TextAlign.Center)
                }
                TokklButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_split_confirm), textAlign = TextAlign.Center)
                }
            }
        },
    )
}

@Composable
private fun problemText(problem: SplitProblem): String = stringResource(
    when (problem) {
        SplitProblem.UNREADABLE -> R.string.history_split_unreadable
        SplitProblem.OUTSIDE -> R.string.history_split_outside
        SplitProblem.CLOCK_CHANGE -> R.string.history_split_clock_change
    },
)

/** The whole seconds of [span], in the account's duration format, as the web app's preview shows them. */
private fun duration(span: SnappedSpan, settings: AccountSettings): String =
    settings.formatDuration(span.stop.epochSecond - span.start.epochSecond)
