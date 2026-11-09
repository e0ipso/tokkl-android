package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsDisk
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Settings and Licences: the theme settings, the sign-out confirmation and the Settings page's view model. */
val settingsFeatureModule = module {
    single { ThemeSettingsStore(androidContext(), io()) } bind ThemeSettingsDisk::class

    // Loaded at process start, off the main thread. MainActivity draws its first frame once they
    // have loaded, so it knows the theme.
    single { themeSettingsWithNightMode(androidContext(), get(), get(), io()) }
    singleOf(::ThemeSettingsLoad) bind StartupLoad::class

    single {
        SignOutConfirmation(
            unsentNow = get<ChangeQueue>()::unsentNow,
            signOut = get(),
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

    single { SettingsEntries } bind FeatureRoutes::class
}
