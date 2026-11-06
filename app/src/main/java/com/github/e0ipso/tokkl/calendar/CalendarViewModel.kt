package com.github.e0ipso.tokkl.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Keeps the chosen day and the weeks loaded from the server across configuration changes, so turning the phone loads nothing again. */
class CalendarViewModel(
    cache: AccountCache,
    accountSettings: AccountSettingsSource,
    changes: ChangeQueue,
    session: Session,
    capabilities: Lazy<CapabilitiesSource>,
    io: CoroutineContext,
) : ViewModel() {

    val model = CalendarModel(
        entries = cache.entries,
        projects = cache.projects,
        tasks = cache.tasks,
        accountSettings = accountSettings.state,
        changes = changes,
        client = session::client,
        scope = viewModelScope,
        io = io,
        // The capabilities load from disk on first read, off the main thread.
        allDayOffered = flow { emitAll(capabilities.value.state) }.map { it.allDay == true }.distinctUntilChanged(),
    )
}
