package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Trash page: its repository, its view model and its screen. */
val trashFeatureModule = module {
    single<TrashRepository> { ServerTrashRepository(get<Session>()) }

    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val scope = viewModelCoroutineScope()
        TrashViewModel(
            repository = get(),
            changes = get(),
            capabilities = get(),
            scope = scope,
            io = io(),
            settings = accountSettings.state,
            offline = get<DefaultNetwork>().offlineIn(scope),
            app = get(),
        )
    }

    single { TrashEntries } bind FeatureRoutes::class
}
