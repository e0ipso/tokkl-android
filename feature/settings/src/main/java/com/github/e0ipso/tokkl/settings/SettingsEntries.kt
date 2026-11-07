package com.github.e0ipso.tokkl.settings

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import org.koin.compose.viewmodel.koinViewModel

/** Settings, and the Licences screen it opens above itself. */
internal object SettingsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val navigator = host.navigator
        route(Route.Settings) {
            SettingsScreen(model = koinViewModel(), onOpenLicences = { navigator.push(Route.Licences) })
        }
        route(Route.Licences) { LicencesScreen(onBack = { navigator.close(Route.Licences) }) }
    }
}
