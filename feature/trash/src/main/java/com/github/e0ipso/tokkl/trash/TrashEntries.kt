package com.github.e0ipso.tokkl.trash

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.trash.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.koin.compose.viewmodel.koinViewModel
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The Trash page, with a view model of its own that goes when the page closes. */
internal object TrashEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Trash) { TrashScreen(model = koinViewModel()) }
    }
}

/** The Trash page, last on the More sheet. */
val trashPage = TopLevelDestination(
    route = Route.Trash,
    label = R.string.nav_trash,
    icon = UiR.drawable.ic_nav_trash,
    hue = SectionHues.more,
    position = 70,
    inMore = true,
)
