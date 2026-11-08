package com.github.e0ipso.tokkl.projects

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.topLevelDestination
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AppEvents
import com.github.e0ipso.tokkl.session.Session
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Projects page: its repositories, its view model and its screen. */
val projectsFeatureModule = module {
    single { ProjectsRepository(get<Session>()) }
    single { TasksRepository(get<Session>()) }

    viewModel {
        val accountSettings = get<AccountSettingsSource>()
        val cache = get<AccountCache>()
        val scope = viewModelCoroutineScope()
        ProjectsViewModel(
            repository = get(),
            tasksRepository = get(),
            workspaceId = accountSettings::defaultWorkspaceId,
            refreshCache = get<AccountRefresh>()::refreshAfterChange,
            scope = scope,
            io = io(),
            cachedProjects = cache.projects,
            cachedClients = cache.clients,
            offline = get<DefaultNetwork>().offlineIn(scope),
            appEvents = get<AppEvents>().events,
        )
    }

    single { ProjectsEntries } bind FeatureEntries::class
    topLevelDestination(projectsPage)
}
