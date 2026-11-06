package com.github.e0ipso.tokkl.reports

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.navigation.sessionViewModel

/** Reports. Its view model is the signed-in session's, so the report keeps its filters after the page closes. */
internal object ReportsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Reports) { ReportsScreen(model = sessionViewModel()) }
    }
}
