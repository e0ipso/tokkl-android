package com.github.e0ipso.tokkl.timer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

// Design-time previews of the timer, its totals and the fullscreen timer, with sample states at a
// fixed noon.

@PreviewLightDark
@Composable
private fun IdleTimerPreview() {
    TimerPreview(IDLE, elapsedSeconds = 0)
}

@PreviewLightDark
@Composable
private fun RunningTimerPreview() {
    TimerPreview(RUNNING, elapsedSeconds = 2_710)
}

@PreviewLightDark
@Composable
private fun PausedTimerPreview() {
    TimerPreview(
        TimerState.Paused(workspaceId = 1, seconds = 1_500, fields = FIELDS, labels = LABELS, original = Picked(1, 11)),
        elapsedSeconds = 0,
    )
}

@PreviewLightDark
@Composable
private fun TimerTotalsUnknownZonePreview() {
    TokklTheme { Surface { TimerTotals(TimerTotalsState.UnknownZone("Unresolved/Zone"), Modifier.padding(16.dp)) } }
}

@PreviewLightDark
@Composable
private fun FullscreenTimerPreview() {
    TokklTheme {
        FullscreenTimerContent(
            state = RUNNING,
            actions = FullscreenTimerActions(
                onPause = {},
                onResume = {},
                onStop = {},
                isShown = { true },
                onEditRunning = {},
                onExit = {},
            ),
            clock = NOON,
        )
    }
}

@Composable
private fun TimerPreview(state: TimerState, elapsedSeconds: Long) {
    TokklTheme {
        Surface {
            Column(Modifier.padding(TokklTheme.spacing.space4)) {
                TimerBarContent(
                    state = state,
                    elapsedSeconds = elapsedSeconds,
                    onDescriptionChange = {},
                    onDescriptionFinish = {},
                    onPick = {},
                    onBillableChange = {},
                    onStart = {},
                    onStop = {},
                    onAddEntry = {},
                    picker = { _, _, _, _, _ -> },
                )
                TimerTotals(TOTALS)
            }
        }
    }
}

private val NOON: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

private val FIELDS = EntryFields(description = "Write the history list", projectId = 1, taskId = 11, billable = true)

private val LABELS = EntryLabels(project = "Website", projectColor = "#0b83d9", task = "Design")

private val IDLE = TimerState.Idle(fields = EntryFields(), labels = EntryLabels(), workspaceId = 1)

private val RUNNING = TimerState.Running(
    entryId = 1,
    workspaceId = 1,
    start = Instant.parse("2026-10-15T11:14:50Z"),
    fields = FIELDS,
    labels = LABELS,
    original = Picked(1, 11),
    unsent = true,
)

private val TOTALS = TimerTotalsState.Shown(
    today = "3:45:10",
    week = "18:02:00",
    todaySeconds = 13_510,
    weekSeconds = 64_920,
)
