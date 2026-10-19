package com.github.e0ipso.tokkl.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/** Keeps the clients page's state across configuration changes, so turning the phone sends no request. */
class ClientsViewModel(application: Application) : AndroidViewModel(application) {

    val model = (application as TokklApplication).let { app ->
        ClientsModel(
            client = Session(application)::client,
            workspaceId = { app.accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { app.accountSettings.refreshAfterChange() },
            scope = viewModelScope,
            io = Dispatchers.IO,
            cachedClients = app.cache.clients,
        )
    }
}
