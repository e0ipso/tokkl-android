package com.github.e0ipso.tokkl.clients

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.clients.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.koin.compose.viewmodel.koinViewModel

/** The Clients page, with a view model of its own that goes when the page closes. */
internal object ClientsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Clients) { ClientsScreen(model = koinViewModel()) }
    }
}

/** The Clients page, on the More sheet after Timer templates. */
val clientsPage = TopLevelDestination(
    route = Route.Clients,
    label = R.string.nav_clients,
    icon = R.drawable.ic_nav_clients,
    hue = SectionHues.more,
    position = 50,
    inMore = true,
)
