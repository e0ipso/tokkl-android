package com.github.e0ipso.tokkl.history

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.entries.OlderDays
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Design-time previews of the history, with two sample days.

@PreviewLightDark
@Composable
private fun HistoryPreview() {
    TokklTheme { Surface { HistoryContent(state = SAMPLE_HISTORY, onLoadOlder = {}) } }
}

@PreviewLightDark
@Composable
private fun HistoryUnknownZonePreview() {
    TokklTheme { Surface { HistoryContent(state = HistoryState.NoAccountTime("Unresolved/Zone"), onLoadOlder = {}) } }
}

private fun entry(
    id: Long,
    description: String,
    project: String?,
    color: String?,
    day: Int,
    start: String,
    stop: String,
    unsent: Boolean = false,
) = HistoryEntry(
    id = EntryId(id),
    description = description,
    project = project,
    projectColor = color,
    task = null,
    tags = if (project == null) emptyList() else listOf("review"),
    billable = project != null,
    timing = EntryTiming.Stopped(Instant.parse("2026-10-${day}T$start:00Z"), Instant.parse("2026-10-${day}T$stop:00Z")),
    unsent = unsent,
)

private val SAMPLE_HISTORY = HistoryState.Shown(
    days = listOf(
        HistoryDay(
            LocalDate.of(2026, 10, 15),
            listOf(
                entry(1, "Code review", "Graffiti wall", "#d94182", 15, "10:45", "11:50", unsent = true),
                entry(2, "Write the history list", "Website", "#0b83d9", 15, "09:00", "10:30"),
            ),
        ),
        HistoryDay(LocalDate.of(2026, 10, 14), listOf(entry(3, "", null, null, 14, "16:00", "17:15"))),
    ),
    today = LocalDate.of(2026, 10, 15),
    zone = ZoneOffset.UTC,
    settings = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null),
    older = OlderDays.End,
)
