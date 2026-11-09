package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.storage.StartupLoad
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Settings and Licences: the theme settings, the sign-out confirmation and the Settings page's view model. */
val settingsFeatureModule = module {
    single { ThemeSettingsStore(androidContext()) }

    // Loaded at process start, off the main thread. MainActivity draws its first frame once they
    // have loaded, so it knows the theme.
    single { themeSettingsWithNightMode(androidContext(), get(), get(), io()) }
    single { ThemeSettingsLoad(get()) } bind StartupLoad::class

    // The Settings page's sign-out button and its warning about unsent changes.
    single {
        SignOutConfirmation(
            unsentCount = get<ChangeQueue>()::unsentNow,
            clear = get<SignOut>()::run,
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
}
