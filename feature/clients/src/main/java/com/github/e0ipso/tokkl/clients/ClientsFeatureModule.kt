package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Clients page: its repository, its view model and its screen. */
val clientsFeatureModule = module {
    single<ClientsRepository> { ServerClientsRepository(get<Session>()) }

    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        ClientsViewModel(
            repository = get(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedClients = get<AccountCache>().clients,
        )
    }

    single { ClientsEntries } bind FeatureEntries::class
}
