package com.github.e0ipso.tokkl.trash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.api.TrashEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek

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
        TrashContent(
            state = TrashState(entries = SAMPLE_ENTRIES),
            offline = offline,
            settings = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null),
            actions = TrashActions(),
        )
    }
}

private val SAMPLE_ENTRIES = listOf(
    TrashEntry(
        id = 1,
        description = "Sprint planning",
        start = "2026-10-14T08:30:00Z",
        stop = "2026-10-14T12:00:00Z",
        duration = 12_600,
        projectName = "Website",
        deletedAt = "2026-10-15T09:12:00Z",
    ),
    TrashEntry(id = 2, start = "2026-10-13T16:00:00Z", stop = "2026-10-13T16:45:00Z", duration = 2_700),
)
