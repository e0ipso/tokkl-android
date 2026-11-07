package com.github.e0ipso.tokkl.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

// Design-time previews of the sync status, the discard notice and the "needs attention" list,
// with sample changes at a fixed noon.

@PreviewLightDark
@Composable
private fun SyncStatusPreview() {
    TokklTheme {
        Surface {
            Column {
                SyncStatusBand(
                    status = SyncStatus(2, lastAnswered = NOON.instant().minusSeconds(480), settings = SETTINGS),
                    onOpenNeedsAttention = null,
                    clock = NOON,
                )
                SyncStatusBand(
                    status = SyncStatus(unsent = 1, lastAnswered = null, needsAttention = 1, signInNeeded = true),
                    onOpenNeedsAttention = {},
                    onSignInAgain = {},
                    clock = NOON,
                )
                DiscardNotice(count = 2, onDismiss = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NeedsAttentionPreview() {
    TokklTheme {
        NeedsAttentionContent(
            state = NeedsAttentionState(listOf(SAMPLE_CHANGE), SETTINGS),
            onEdit = {},
            onSendAgain = {},
            onDiscard = {},
            onBack = {},
        )
    }
}

private val NOON: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

private val SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null)

private val SAMPLE_CHANGE = PendingChange(
    sequence = 1,
    kind = ChangeKind.UPDATE,
    entryId = 42,
    workspaceId = 7,
    projectId = null,
    taskId = null,
    description = "Sprint planning",
    start = Instant.parse("2026-10-14T08:30:00Z"),
    stop = Instant.parse("2026-10-14T12:00:00Z"),
    billable = false,
    tags = emptyList(),
    tagIds = emptyList(),
    held = true,
    refusal = "Time entries can not be longer than 999 hours",
)
