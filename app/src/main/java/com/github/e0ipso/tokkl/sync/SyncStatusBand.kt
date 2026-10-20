package com.github.e0ipso.tokkl.sync

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.ui.UnsentIcon
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.Clock

/**
 * The status element above the bottom bar (docs/PRD.md O5): the unsent icon and how many changes
 * the server has not received, then when it last answered, in the account's timezone and clock
 * format. TalkBack reads both lines as one. The icon and the words mark the state, never colour
 * alone, and the text wraps at any font scale.
 *
 * While the server has refused changes, [SyncStatus.needsAttention], a button opens the "needs
 * attention" list through [onOpenNeedsAttention], which TKA-0073 builds. The element lists no
 * change itself.
 *
 * @param clock where today comes from, which decides whether the time needs its day.
 */
@Composable
fun SyncStatusBand(
    status: SyncStatus,
    onOpenNeedsAttention: (() -> Unit)?,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemUTC(),
) {
    val resources = LocalResources.current
    val bodyStyle = MaterialTheme.typography.bodyMedium
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .screenContent()
                .padding(vertical = TokklTheme.spacing.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
            ) {
                if (status.unsent > 0) UnsentIcon(bodyStyle)
                Column(Modifier.weight(1f)) {
                    if (status.unsent > 0) {
                        Text(
                            text = resources.getQuantityString(R.plurals.sync_unsent, status.unsent, status.unsent),
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    lastAnsweredText(resources, lastAnsweredLabel(status.lastAnswered, status.settings, clock))?.let { text ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (status.needsAttention > 0 && onOpenNeedsAttention != null) {
                TextButton(onClick = onOpenNeedsAttention) {
                    Text(resources.getQuantityString(R.plurals.sync_needs_attention, status.needsAttention, status.needsAttention))
                }
            }
        }
    }
}

/** The line about the server's last answer, or null without an account timezone to show it in. */
private fun lastAnsweredText(resources: Resources, label: LastAnsweredLabel): String? = when (label) {
    LastAnsweredLabel.Never -> resources.getString(R.string.sync_never_answered)
    LastAnsweredLabel.NoAccountTime -> null
    is LastAnsweredLabel.At -> when (label.daysAgo) {
        0L -> resources.getString(R.string.sync_last_answered_today, label.time)
        1L -> resources.getString(R.string.sync_last_answered_yesterday, label.time)
        else -> {
            val words = DateWords(resources)
            val day = resources.getString(R.string.history_day, words.weekday(label.day), words.shortWithYear(label.day))
            resources.getString(R.string.sync_last_answered_day, day, label.time)
        }
    }
}
