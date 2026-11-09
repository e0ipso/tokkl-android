package com.github.e0ipso.tokkl.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.ui.theme.SectionHue

/**
 * A top-level page that a feature puts in the bottom bar, the navigation rail or, [inMore], on
 * the More sheet: its [route], its [label] and [icon] there, and its section [hue]. The pages
 * stand in the order of their [position]s, which follow NAV in Tokkl's App.tsx (docs/PRD.md B1)
 * in steps of ten, so a new page fits between two others. A page whose server lacks what it
 * needs, as [isAvailable] says of the server's capabilities, shows nowhere, and the host opens
 * Timer in its place.
 */
@Immutable
data class TopLevelDestination(
    val route: Route,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val hue: SectionHue,
    val position: Int,
    val inMore: Boolean = false,
    val isAvailable: (Capabilities) -> Boolean = { true },
)

/**
 * The app's top-level pages, from every feature's [FeatureRoutes.pages], in the order of their
 * positions. As on the web app on a phone, the pages in More sit on the More sheet, not in the
 * bottom bar. Admin stays out, since the app has no admin features.
 */
@Immutable
class TopLevelPages(destinations: List<TopLevelDestination>) {

    /** Every page, in order. */
    val all: List<TopLevelDestination> = destinations.sortedBy { it.position }

    init {
        require(all.map { it.position }.distinct().size == all.size) { "two pages share a position: $all" }
        require(all.map { it.route }.distinct().size == all.size) { "two pages share a route: $all" }
    }

    /** The page [route] opens, or null for a screen opened from a page, such as Licences. */
    fun of(route: Route): TopLevelDestination? = all.firstOrNull { it.route == route }

    /** The routes of the pages the server's [flags] leave without a use ([TopLevelDestination.isAvailable]). */
    fun unavailable(flags: Capabilities): Set<Route> = all.filterNot { it.isAvailable(flags) }.map { it.route }.toSet()

    /** The route of the page the screen on top of [stack] belongs to: itself, the nearest one below it, or Timer. */
    fun currentPage(stack: List<Route>): Route = stack.asReversed().firstOrNull { of(it) != null } ?: Route.Timer
}
