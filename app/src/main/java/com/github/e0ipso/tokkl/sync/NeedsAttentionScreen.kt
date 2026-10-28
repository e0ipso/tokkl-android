package com.github.e0ipso.tokkl.sync

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import com.github.e0ipso.tokkl.ui.TokklIconButton
import kotlinx.coroutines.flow.StateFlow

/**
 * The "needs attention" list (docs/PRD.md O6), opened from the status band. Each change the server
 * refused shows what it does, its entry's description and times in the account's zone and clock
 * format, and the server's reason. Edit entry opens the entry form through [onEdit] with the
 * entry's id, and saving it sends the fix. Send again and Discard go to [model]. A delete has no
 * entry left to edit.
 */
@Composable
fun NeedsAttentionScreen(
    model: NeedsAttentionModel,
    settings: StateFlow<AccountSettings?>,
    onEdit: (entryId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val held by model.held.collectAsState(initial = emptyList())
    val account by settings.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(stringResource(R.string.needs_attention), onBack = onBack) },
        contentWindowInsets = ScrollingScreenInsets,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        ) {
            Text(
                text = stringResource(if (held.isEmpty()) R.string.needs_attention_empty else R.string.needs_attention_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            held.forEach { change ->
                HorizontalDivider()
                HeldChange(
                    change = change,
                    settings = account,
                    onEdit = { onEdit(change.entryId) },
                    onSendAgain = { model.sendAgain(change) },
                    onDiscard = { model.discard(change) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeldChange(
    change: PendingChange,
    settings: AccountSettings?,
    onEdit: () -> Unit,
    onSendAgain: () -> Unit,
    onDiscard: () -> Unit,
) {
    val resources = LocalResources.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(change.kind.label()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = change.description.ifBlank { stringResource(R.string.needs_attention_no_description) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        spanText(resources, change, settings)?.let { span ->
            Text(span, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = change.refusal?.let { stringResource(R.string.needs_attention_reason, it) }
                ?: stringResource(R.string.needs_attention_no_reason),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = TokklTheme.spacing.space1),
        )
        FlowRow {
            if (change.kind != ChangeKind.DELETE) {
                TokklIconButton(
                    icon = R.drawable.ic_edit,
                    description = stringResource(R.string.needs_attention_edit_named, change.description.ifBlank { stringResource(R.string.needs_attention_no_description) }),
                    onClick = onEdit,
                )
            }
            TextButton(onClick = onSendAgain) { Text(stringResource(R.string.needs_attention_send_again)) }
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.needs_attention_discard)) }
        }
    }
}

private fun ChangeKind.label(): Int = when (this) {
    ChangeKind.CREATE -> R.string.needs_attention_kind_create
    ChangeKind.UPDATE -> R.string.needs_attention_kind_update
    ChangeKind.STOP -> R.string.needs_attention_kind_stop
    ChangeKind.RESTORE -> R.string.needs_attention_kind_restore
    ChangeKind.DELETE -> R.string.needs_attention_kind_delete
}

/** The entry's day and times in the account's zone, or null when the phone cannot resolve that zone. */
private fun spanText(resources: Resources, change: PendingChange, settings: AccountSettings?): String? {
    val time = settings?.time() ?: return null
    val start = time.local(change.start).toLocalDate()
    val words = DateWords(resources)
    val day = resources.getString(R.string.history_day, words.weekday(start), words.shortWithYear(start))
    val from = settings.formatTime(change.start, time.zone)
    val stop = change.stop ?: return resources.getString(R.string.needs_attention_running, day, from)
    return resources.getString(R.string.needs_attention_span, day, from, settings.formatTime(stop, time.zone))
}
