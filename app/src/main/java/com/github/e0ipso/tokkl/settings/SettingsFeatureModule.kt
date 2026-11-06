package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.session.SignOut
import org.koin.dsl.bind
import org.koin.dsl.module

/** Settings and Licences. */
val settingsFeatureModule = module {
    /** The Settings page's sign-out button and its warning about unsent changes. */
    single { SignOutConfirmation(unsentCount = get<ChangeQueue>()::unsentNow, clear = get<SignOut>()::run, scope = get()) }

    single { SettingsEntries } bind FeatureEntries::class
}
