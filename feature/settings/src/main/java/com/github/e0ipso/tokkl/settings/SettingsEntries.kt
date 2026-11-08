package com.github.e0ipso.tokkl.settings

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.settings.R
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
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

/** The Settings page, last in the bar before More. */
val settingsPage = TopLevelDestination(
    route = Route.Settings,
    label = R.string.settings,
    icon = R.drawable.ic_nav_settings,
    hue = SectionHues.settings,
    position = 80,
)
