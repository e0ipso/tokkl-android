package com.github.e0ipso.tokkl

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.clients.clientsPage
import com.github.e0ipso.tokkl.navhost.SignedInFrame
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.pomodoro.pomodoroPage
import com.github.e0ipso.tokkl.projects.projectsPage
import com.github.e0ipso.tokkl.reports.reportsPage
import com.github.e0ipso.tokkl.settings.settingsPage
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.sync.SyncStatusBand
import com.github.e0ipso.tokkl.tags.tagsPage
import com.github.e0ipso.tokkl.timer.templatesPage
import com.github.e0ipso.tokkl.timer.timerPage
import com.github.e0ipso.tokkl.trash.trashPage
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import com.github.e0ipso.tokkl.feature.timer.R as TimerR

// Design-time previews of the signed-in frame: the bar on a phone and the rail on a wider window,
// around a sample page, with the features' pages and the sync status band. They sit beside
// MainActivity, which wires the features into the navigation host, so the host names none.

@PreviewLightDark
@Composable
private fun SignedInFrameBarPreview() {
    FramePreview(WindowWidth.COMPACT)
}

@PreviewLightDark
@Composable
private fun SignedInFrameRailPreview() {
    FramePreview(WindowWidth.MEDIUM)
}

@Composable
private fun FramePreview(width: WindowWidth) {
    val noon = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    TokklTheme {
        SignedInFrame(
            pages = previewPages,
            current = Route.Timer,
            onOpen = {},
            badges = mapOf(Route.Timer to TimerR.string.nav_timer_running),
            band = {
                SyncStatusBand(
                    SyncStatus(unsent = 1, lastAnswered = noon.instant().minusSeconds(480)),
                    onOpenNeedsAttention = null,
                    clock = noon,
                )
            },
            width = width,
        ) {
            Text("The page", Modifier.padding(TokklTheme.spacing.space4))
        }
    }
}

/** The pages the features bind, as Koin hands them to the navigation host. */
private val previewPages = TopLevelPages(
    listOf(
        timerPage,
        pomodoroPage,
        reportsPage,
        projectsPage,
        templatesPage,
        clientsPage,
        tagsPage,
        trashPage,
        settingsPage,
    ),
)
