package com.github.e0ipso.tokkl.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Keeps the reports screen's state across configuration changes, so turning the phone sends no request. */
class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TokklApplication

    private val session = Session(application)
    private val exporter = ReportExportController(
        client = session::client,
        files = ReportExportFiles(File(application.cacheDir, ReportExportFiles.DIRECTORY)),
        account = Session.exports,
        csvMissing = app.capabilities::csvMissing,
        scope = viewModelScope,
        io = Dispatchers.IO,
    )

    val model = ReportsModel(
        accountSettings = app.accountSettings.state,
        client = session::client,
        exportCapabilities = app.capabilities.state,
        exporter = exporter,
        scope = viewModelScope,
        io = Dispatchers.IO,
        // From the Room cache, never from the server, and only while the screen reads them.
        filterOptions = combine(
            app.cache.projects,
            app.cache.clients,
            app.cache.tags,
            app.accountSettings.state,
            app.capabilities.state,
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
