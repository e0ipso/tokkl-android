package com.github.e0ipso.tokkl.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.api.PhaseKind
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

// Design-time previews of the Pomodoro start form and the full-screen alarm, with sample states at
// a fixed noon.

@PreviewLightDark
@Composable
private fun PomodoroStartPreview() {
    FormPreview(IDLE)
}

@PreviewLightDark
@Composable
private fun PomodoroLimitPreview() {
    FormPreview(IDLE.copy(lengths = IDLE.lengths.copy(work = "200"), notificationsDenied = true))
}

@PreviewLightDark
@Composable
private fun PomodoroAlarmPreview() {
    TokklTheme {
        PomodoroAlarmContent(
            PomodoroAlarmState(
                ring = PomodoroRing(
                    phase = PomodoroPhaseKey(NOON.instant().minusSeconds(25 * 60 + 65)),
                    kind = PhaseKind.WORK,
                    sound = PomodoroSound.WORK_TO_SHORT_REST,
                    number = 3,
                    longRestEvery = 4,
                    end = NOON.instant().minusSeconds(65),
                    revision = 7,
                ),
            ),
            onNext = {},
            onEnd = {},
            clock = NOON,
        )
    }
}

@Composable
private fun FormPreview(state: PomodoroPageState) {
    TokklTheme {
        PomodoroContent(
            state = state,
            onLengths = {},
            onFields = {},
            onDescription = {},
            onDescriptionFinish = {},
            onStart = {},
            onNext = {},
            onEnd = {},
            clock = NOON,
        )
    }
}

private val NOON: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

private val IDLE = PomodoroPageState(
    loading = false,
    account = AccountSettings(
        timeZone = "UTC",
        beginningOfWeek = DayOfWeek.MONDAY,
        timeResolutionMinutes = null,
        defaultWorkspaceId = 1,
    ),
    fields = EntryFields(description = "Write the release notes", billable = true),
    lengths = PomodoroLengths("25", "5", "15", "4"),
)
