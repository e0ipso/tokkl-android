package com.github.e0ipso.tokkl.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import kotlin.coroutines.CoroutineContext

/** Keeps the projects page's state across configuration changes, so turning the phone sends no request. */
class ProjectsViewModel(
    session: Session,
    accountSettings: AccountSettingsSource,
    cache: AccountCache,
    io: CoroutineContext,
) : ViewModel() {

    val model = ProjectsModel(
        client = session::client,
        workspaceId = { accountSettings.state.value?.defaultWorkspaceId },
        refreshCache = { accountSettings.refreshAfterChange() },
        scope = viewModelScope,
        io = io,
        cachedProjects = cache.projects,
        cachedClients = cache.clients,
    )
}
