package com.github.e0ipso.tokkl.projects

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.projects.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.koin.compose.viewmodel.koinViewModel
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The Projects page, with a view model of its own that goes when the page closes. */
internal object ProjectsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Projects) { ProjectsScreen(model = koinViewModel()) }
    }
}

/** The Projects page, in the bar after Pomodoro. */
val projectsPage = TopLevelDestination(
    route = Route.Projects,
    label = R.string.nav_projects,
    icon = UiR.drawable.ic_nav_projects,
    hue = SectionHues.projects,
    position = 30,
)
