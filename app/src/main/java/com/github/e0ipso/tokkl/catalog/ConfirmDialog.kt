package com.github.e0ipso.tokkl.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.TokklDangerButton
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * Asks before a change that touches more than the item, as the web app's ConfirmModal: [title] as
 * a heading, the [paragraphs] that say what the change does, and [problem] when the last try
 * failed. Cancel and [confirmLabel] sit in one slot, stacked at full width, so they cannot overlap
 * at any font scale; AlertDialog's own button row put one over the other at 200%. Back and a tap
 * outside cancel. [canConfirm] is false while the change cannot go to the server. A [danger]
 * change, one that cannot be undone, gets the danger colours, as the web app's ConfirmModal gives
 * it.
 */
@Composable
fun ConfirmDialog(
    title: String,
    paragraphs: List<String>,
    confirmLabel: String,
    canConfirm: Boolean,
    problem: ServerProblem?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
            ) {
                for (paragraph in paragraphs) Text(paragraph)
                problem?.let { ErrorBanner(problemText(it)) }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                TokklGhostButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.catalog_cancel), textAlign = TextAlign.Center)
                }
                val label = @Composable { Text(confirmLabel, textAlign = TextAlign.Center) }
                if (danger) {
                    TokklDangerButton(onClick = onConfirm, enabled = canConfirm, modifier = Modifier.fillMaxWidth()) { label() }
                } else {
                    TokklButton(onClick = onConfirm, enabled = canConfirm, modifier = Modifier.fillMaxWidth()) { label() }
                }
            }
        },
    )
}
