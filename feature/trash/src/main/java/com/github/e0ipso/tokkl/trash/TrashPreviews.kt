package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant

// Design-time previews of the trash page, with sample deleted entries.

@PreviewLightDark
@Composable
private fun TrashPreview() {
    TrashPagePreview(offline = false)
}

@PreviewLightDark
@Composable
private fun TrashOfflinePreview() {
    TrashPagePreview(offline = true)
}

@Composable
private fun TrashPagePreview(offline: Boolean) {
    TokklTheme {
        TrashScreen(
            TrashPageState(
                TrashState(entries = SAMPLE_ENTRIES),
                offline,
                AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null),
            ),
            actions = TrashActions(),
        )
    }
}

private val SAMPLE_ENTRIES = listOf(
    DeletedEntry(
        id = EntryId(1),
        description = "Sprint planning",
        projectId = ProjectId(1),
        projectName = "Website",
        start = Instant.parse("2026-10-14T08:30:00Z"),
        duration = Duration.ofSeconds(12_600),
        deletedAt = Instant.parse("2026-10-15T09:12:00Z"),
    ),
    DeletedEntry(id = EntryId(2), start = Instant.parse("2026-10-13T16:00:00Z"), duration = Duration.ofSeconds(2_700)),
)
