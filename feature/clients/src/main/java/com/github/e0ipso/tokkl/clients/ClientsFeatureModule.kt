package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Clients page: its repository, its view model and its screen. */
val clientsFeatureModule = module {
    singleOf(::ServerClientsRepository)

    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val scope = viewModelCoroutineScope()
        ClientsViewModel(
            repository = get(),
            settings = accountSettings,
            changes = get(),
            scope = scope,
            io = io(),
            cachedClients = get<AccountCache>().clients,
            offline = get<DefaultNetwork>().offlineIn(scope),
            app = get(),
            saved = get(),
        )
    }

    single { ClientsEntries } bind FeatureRoutes::class
}
