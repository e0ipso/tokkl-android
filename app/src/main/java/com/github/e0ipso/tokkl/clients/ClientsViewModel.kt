package com.github.e0ipso.tokkl.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import kotlin.coroutines.CoroutineContext

/** Keeps the clients page's state across configuration changes, so turning the phone sends no request. */
class ClientsViewModel(
    session: Session,
    accountSettings: AccountSettingsSource,
    cache: AccountCache,
    io: CoroutineContext,
) : ViewModel() {

    val model = ClientsModel(
        client = session::client,
        workspaceId = { accountSettings.state.value?.defaultWorkspaceId },
        refreshCache = { accountSettings.refreshAfterChange() },
        scope = viewModelScope,
        io = io,
        cachedClients = cache.clients,
    )
}
