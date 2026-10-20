package com.github.e0ipso.tokkl.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Material's window width classes, which decide how a signed-in screen lays out on a phone, a
 * tablet or a foldable (docs/PRD.md, Devices). Every class shows the same screens.
 */
enum class WindowWidth {
    /** Under 600dp, a phone held upright: the bottom bar. */
    COMPACT,

    /** From 600dp, such as a small tablet or an unfolded foldable held upright: the navigation rail. */
    MEDIUM,

    /**
     * From 840dp, such as a tablet or a phone held sideways: the rail, the entry editor beside the
     * history, and the week calendar across the extra width.
     */
    EXPANDED,
}

/** The width class of the app's window, from the Compose Material 3 adaptive library. */
@Composable
fun windowWidth(): WindowWidth {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidth.EXPANDED
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
}
