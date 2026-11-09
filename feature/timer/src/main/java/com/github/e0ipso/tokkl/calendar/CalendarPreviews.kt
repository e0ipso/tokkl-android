package com.github.e0ipso.tokkl.calendar

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.entries.WeekSource
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Design-time previews of the calendar, one day and the whole week, with sample entries at a
// fixed noon.

@PreviewLightDark
@Composable
private fun CalendarDayPreview() {
    CalendarPreview(dayMode = true)
}

@PreviewLightDark
@Composable
private fun CalendarWeekPreview() {
    CalendarPreview(dayMode = false)
}

@Composable
private fun CalendarPreview(dayMode: Boolean) {
    TokklTheme {
        Surface {
            CalendarContent(
                state = SAMPLE_WEEK,
                now = NOON,
                onStep = {},
                onSelect = {},
                onToday = {},
                onRetry = {},
                dayMode = dayMode,
            )
        }
    }
}

private val NOON: Instant = Instant.parse("2026-10-15T12:00:00Z")

private fun entry(id: Long, description: String, project: String?, color: String?, start: String, stop: String?) =
    CalendarEntry(
        id = EntryId(id),
        description = description,
        project = project,
        projectColor = color,
        task = null,
        start = Instant.parse("$start:00Z"),
        stop = stop?.let { Instant.parse("$it:00Z") },
        unsent = stop == null,
    )

private val SAMPLE_WEEK = CalendarState.Shown(
    selected = LocalDate.of(2026, 10, 15),
    weekStart = LocalDate.of(2026, 10, 12),
    today = LocalDate.of(2026, 10, 15),
    zone = ZoneOffset.UTC,
    settings = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null),
    entries = listOf(
        entry(1, "Paint the mural", "Graffiti wall", "#d94182", "2026-10-12T09:00", "2026-10-12T12:30"),
        entry(2, "Sprint planning", "Website", "#0b83d9", "2026-10-13T08:30", "2026-10-13T12:00"),
        entry(3, "Stand-up", "Website", "#0b83d9", "2026-10-15T08:00", "2026-10-15T08:30"),
        entry(4, "Calendar grid", "Website", "#0b83d9", "2026-10-15T10:15", stop = null),
    ),
    week = WeekSource.Cached,
)
