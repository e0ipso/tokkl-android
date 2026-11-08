package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.topLevelDestination
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.SignOut
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Settings and Licences: the sign-out confirmation and the Settings page's view model. */
val settingsFeatureModule = module {
    // The Settings page's sign-out button and its warning about unsent changes.
    single {
        SignOutConfirmation(
            unsentCount = get<ChangeQueue>(),
            clear = get<SignOut>(),
            scope = get(),
        )
    }

    viewModel {
        SettingsViewModel(
            theme = get(),
            pomodoro = get(),
            reminder = get(),
            signOut = get(),
            capabilities = get<CapabilitiesSource>().state,
            scope = viewModelCoroutineScope(),
        )
    }

    single { SettingsEntries } bind FeatureEntries::class
    topLevelDestination(settingsPage)
}
