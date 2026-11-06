package com.github.e0ipso.tokkl.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import kotlin.coroutines.CoroutineContext

/**
 * Keeps the history's older days across configuration changes, so turning the phone loads nothing
 * again, and the [actions] on its entries, so a message with Undo shows again after a turn.
 */
class HistoryViewModel(
    cache: AccountCache,
    accountSettings: AccountSettingsSource,
    changes: ChangeQueue,
    sender: ChangeSender,
    capabilities: CapabilitiesSource,
    session: Session,
    io: CoroutineContext,
) : ViewModel() {

    val model = HistoryModel(
        entries = cache.entries,
        projects = cache.projects,
        tasks = cache.tasks,
        accountSettings = accountSettings.state,
        client = session::client,
        scope = viewModelScope,
        io = io,
        currentId = changes::currentId,
    )

    val actions = EntryActionsModel(
        entries = cache.entries,
        changes = changes,
        undoDelete = sender::undoDelete,
        canRestore = { capabilities.state.value.restore },
        accountSettings = accountSettings.state,
        scope = viewModelScope,
    )
}
