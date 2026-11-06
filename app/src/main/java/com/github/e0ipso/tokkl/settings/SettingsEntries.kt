package com.github.e0ipso.tokkl.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import org.koin.compose.koinInject

/** Settings, and the Licences screen it opens above itself. */
internal object SettingsEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val navigator = host.navigator
        route(Route.Settings) {
            val flags by host.capabilities.collectAsState()
            SettingsScreen(
                model = koinInject(),
                reminder = koinInject(),
                pomodoro = koinInject(),
                pomodoroSharedWithWeb = flags.favorites,
                signOut = koinInject(),
                onOpenLicences = { navigator.push(Route.Licences) },
            )
        }
        route(Route.Licences) { LicencesScreen(onBack = { navigator.close(Route.Licences) }) }
    }
}
