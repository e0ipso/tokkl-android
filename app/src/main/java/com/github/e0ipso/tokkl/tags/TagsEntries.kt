package com.github.e0ipso.tokkl.tags

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import org.koin.compose.viewmodel.koinViewModel

/** The Tags page, with a view model of its own that goes when the page closes. */
internal object TagsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Tags) { TagsScreen(model = koinViewModel()) }
    }
}
