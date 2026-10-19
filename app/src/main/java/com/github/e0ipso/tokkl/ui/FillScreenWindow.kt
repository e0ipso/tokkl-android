package com.github.e0ipso.tokkl.ui

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Makes the dialog's window as tall as the screen instead of as tall as its content. Compose
 * leaves a full-width dialog's window at WRAP_CONTENT height, and Android 11 then measured it in
 * every layout pass twice: at the width of `screenWidthDp`, which rounds to 1079 px on a 1080 px
 * screen, and again at the window's 1080 px. With the date range picker's month list inside,
 * that never settled: the dialog laid itself out on every frame and the main thread never went
 * idle. Without this, Android 12 (API 31) still never went idle there. The Surface inside
 * already fills the window, so the window keeps its size. Call it
 * first inside a Dialog with `usePlatformDefaultWidth = false`.
 */
@Composable
fun FillScreenWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null && window.attributes.height != MATCH_PARENT) window.setLayout(MATCH_PARENT, MATCH_PARENT)
    }
}
