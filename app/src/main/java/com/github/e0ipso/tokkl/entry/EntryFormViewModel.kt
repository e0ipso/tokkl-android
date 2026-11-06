package com.github.e0ipso.tokkl.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import kotlinx.coroutines.flow.map

/** Keeps the entry form and what it holds across configuration changes. */
class EntryFormViewModel(
    cache: AccountCache,
    accountSettings: AccountSettingsSource,
    changes: ChangeQueue,
    capabilities: CapabilitiesSource,
) : ViewModel() {

    val model = EntryFormModel(
        entries = cache.entries,
        projects = cache.projects,
        tasks = cache.tasks,
        accountSettings = accountSettings.state,
        changes = changes,
        scope = viewModelScope,
        allDay = capabilities.state.map { it.allDay == true },
    )
}
