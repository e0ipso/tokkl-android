package com.github.e0ipso.tokkl.navhost

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

// Design-time previews of the signed-in frame: the bar on a phone and the rail on a wider window,
// around a sample page.

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
            current = TopLevelPage.TIMER,
            onOpen = {},
            timerRunning = true,
            syncStatus = SyncStatus(unsent = 1, lastAnswered = noon.instant().minusSeconds(480)),
            syncStatusClock = noon,
            width = width,
        ) {
            Text("The page", Modifier.padding(TokklTheme.spacing.space4))
        }
    }
}
