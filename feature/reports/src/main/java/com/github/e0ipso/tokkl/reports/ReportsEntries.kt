package com.github.e0ipso.tokkl.reports

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import com.github.e0ipso.tokkl.ui.theme.SectionHues

/** Reports. Its view model is the signed-in session's, so the report keeps its filters after the page closes. */
internal object ReportsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Reports) { ReportsScreen(model = sessionViewModel()) }
    }
}

/** The Reports page, first on the More sheet. It keeps its own hue there. */
val reportsPage = TopLevelDestination(
    route = Route.Reports,
    label = R.string.reports,
    icon = R.drawable.ic_nav_reports,
    hue = SectionHues.reports,
    position = 20,
    inMore = true,
)
