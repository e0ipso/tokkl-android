package com.github.e0ipso.tokkl.projects

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

/** The Projects page: its view model and its screen. */
val projectsFeatureModule = module {
    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val cache = get<AccountCache>()
        ProjectsViewModel(
            client = get<Session>(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = viewModelCoroutineScope(),
            io = io(),
            cachedProjects = cache.projects,
            cachedClients = cache.clients,
        )
    }

    single { ProjectsEntries } bind FeatureEntries::class
}
