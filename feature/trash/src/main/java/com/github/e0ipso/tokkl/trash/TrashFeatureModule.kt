package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Trash page: its view model and its screen. */
val trashFeatureModule = module {
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        TrashViewModel(
            client = get<Session>(),
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            trashMissing = get<CapabilitiesSource>()::trashMissing,
            scope = viewModelCoroutineScope(),
            io = io(),
            settings = accountSettings.state,
        )
    }

    single { TrashEntries } bind FeatureEntries::class
}
