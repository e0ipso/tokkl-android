package com.github.e0ipso.tokkl.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import kotlin.coroutines.CoroutineContext

/** Keeps the trash page's state across configuration changes, so turning the phone sends no request. */
class TrashViewModel(
    session: Session,
    accountSettings: AccountSettingsSource,
    capabilities: CapabilitiesSource,
    io: CoroutineContext,
) : ViewModel() {

    val model = TrashModel(
        client = session::client,
        refreshCache = { accountSettings.refreshAfterChange() },
        trashMissing = capabilities::trashMissing,
        scope = viewModelScope,
        io = io,
        settings = accountSettings.state,
    )
}
