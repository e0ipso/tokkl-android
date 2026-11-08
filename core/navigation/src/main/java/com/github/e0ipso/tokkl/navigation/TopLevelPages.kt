package com.github.e0ipso.tokkl.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.ui.theme.SectionHue
import org.koin.core.module.Module
import org.koin.core.qualifier.named

/**
 * A top-level page that a feature puts in the bottom bar, the navigation rail or, [inMore], on
 * the More sheet: its [route], its [label] and [icon] there, and its section [hue]. The pages
 * stand in the order of their [position]s, which follow NAV in Tokkl's App.tsx (docs/PRD.md B1)
 * in steps of ten, so a new page fits between two others.
 */
@Immutable
data class TopLevelDestination(
    val route: Route,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val hue: SectionHue,
    val position: Int,
    val inMore: Boolean = false,
)

/**
 * Binds [destination] in this feature's Koin module. The navigation host collects every feature's
 * with `getAll()` into [TopLevelPages].
 */
fun Module.topLevelDestination(destination: TopLevelDestination) {
    single(named("top-level-page-${destination.route.contentKey}")) { destination }
}

/**
 * The app's top-level pages, from every feature's [TopLevelDestination], in the order of their
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

    /** The route of the page the screen on top of [stack] belongs to: itself, the nearest one below it, or Timer. */
    fun currentPage(stack: List<Route>): Route = stack.asReversed().firstOrNull { of(it) != null } ?: Route.Timer
}
