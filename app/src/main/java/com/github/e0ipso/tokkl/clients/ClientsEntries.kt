package com.github.e0ipso.tokkl.clients

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import org.koin.compose.viewmodel.koinViewModel

/** The Clients page, with a view model of its own that goes when the page closes. */
internal object ClientsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Clients) { ClientsScreen(model = koinViewModel()) }
    }
}
