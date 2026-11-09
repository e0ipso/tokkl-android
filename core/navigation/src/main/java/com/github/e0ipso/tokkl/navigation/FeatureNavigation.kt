package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What one feature shows in the signed-in frame around every page. Only a feature that shows
 * something there binds one, such as the sync status band and the discarded changes' notice.
 */
interface FrameContributions {

    /**
     * The band this feature shows right above the bottom bar, or at the bottom of the screen beside
     * the rail, or null while it shows none.
     */
    @Composable
    fun frameBand(host: NavigationHost): (@Composable () -> Unit)?

    /**
     * What this feature shows over every signed-in page, with the modifier that places it at the
     * bottom, below the timer's snackbars and the notification permission's prompt, or null while
     * it shows nothing.
     */
    @Composable
    fun frameNotice(host: NavigationHost): (@Composable (Modifier) -> Unit)?
}

/**
 * What the features hand the navigation host, each part from the features that give it: the
 * [routes] with their screens and pages, the [requests] of the taps from outside the app, and the
 * [frame]'s bands and notices. MainActivity gathers them from Koin.
 */
class FeatureNavigation(
    val routes: List<FeatureRoutes>,
    val requests: List<FeatureRequests>,
    val frame: List<FrameContributions>,
)
