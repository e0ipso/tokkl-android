package com.github.e0ipso.tokkl.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Keeps the reports screen's state across configuration changes, so turning the phone sends no request. */
class ReportsViewModel(
    context: Context,
    session: Session,
    accountSettings: AccountSettingsSource,
    capabilities: CapabilitiesSource,
    cache: AccountCache,
    io: CoroutineContext,
) : ViewModel() {

    private val exporter = ReportExportController(
        client = session::client,
        files = ReportExportFiles(File(context.cacheDir, ReportExportFiles.DIRECTORY)),
        account = Session.exports,
        csvMissing = capabilities::csvMissing,
        scope = viewModelScope,
        io = io,
    )

    val model = ReportsModel(
        accountSettings = accountSettings.state,
        client = session::client,
        exportCapabilities = capabilities.state,
        exporter = exporter,
        scope = viewModelScope,
        io = io,
        // From the Room cache, never from the server, and only while the screen reads them.
        filterOptions = combine(
            cache.projects,
            cache.clients,
            cache.tags,
            accountSettings.state,
            capabilities.state,
        ) { projects, clients, tags, settings, capabilities ->
            FilterOptions.of(projects, clients, tags, settings?.defaultWorkspaceId, negation = capabilities.negatedFilters)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FilterOptions.NONE),
    )

    override fun onCleared() {
        exporter.cancel()
        super.onCleared()
    }
}

/** How long the cache's flows stay collected after the screen stops reading them, as across a rotation. */
private const val STOP_TIMEOUT_MS = 5_000L
