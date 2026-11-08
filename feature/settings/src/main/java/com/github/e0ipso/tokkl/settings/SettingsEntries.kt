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
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

/** Settings, and the Licences screen it opens above itself. */
internal object SettingsEntries : FeatureEntries {
    override val pages get() = listOf(settingsPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(SettingsRoute::class)
        subclass(LicencesRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val navigator = host.navigator
        route(SettingsRoute) {
            SettingsScreen(model = koinViewModel(), onOpenLicences = { navigator.push(LicencesRoute) })
        }
        route(LicencesRoute) { LicencesScreen(onBack = { navigator.close(LicencesRoute) }) }
    }
}

/** The Settings page, last in the bar before More. */
val settingsPage = TopLevelDestination(
    route = SettingsRoute,
    label = R.string.settings,
    icon = R.drawable.ic_nav_settings,
    hue = SectionHues.settings,
    position = 80,
)

/** The Settings page. */
@Serializable
data object SettingsRoute : Route

/** The Licences screen, which Settings opens above itself. */
@Serializable
data object LicencesRoute : Route
