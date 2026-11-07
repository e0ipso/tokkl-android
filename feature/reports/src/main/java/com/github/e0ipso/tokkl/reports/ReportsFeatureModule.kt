package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AppEvents
import com.github.e0ipso.tokkl.session.ExportFiles
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/** Reports: the report CSV files, which a sign-out deletes, the view model and the screen. */
val reportsFeatureModule = module {
    single { ReportExportFiles(File(androidContext().cacheDir, ReportExportFiles.DIRECTORY)) } bind ExportFiles::class

    viewModel {
        val session = get<Session>()
        val accountSettings = get<AccountSettingsSource>()
        val capabilities = get<CapabilitiesSource>()
        val cache = get<AccountCache>()
        // The exporter shares the view model's scope, so clearing the view model cancels it too.
        val scope = viewModelCoroutineScope()
        ReportsViewModel(
            accountSettings = accountSettings.state,
            client = session,
            exportCapabilities = capabilities.state,
            exporter = ReportExportController(
                client = session,
                files = get(),
                account = get(),
                csvMissing = capabilities::csvMissing,
                scope = scope,
                io = io(),
            ),
            scope = scope,
            io = io(),
            // Only while the screen reads them.
            filterOptions = reportFilterOptions(cache, accountSettings.state, capabilities.state)
                .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FilterOptions.NONE),
            appEvents = get<AppEvents>().events,
        )
    }

    single { ReportsEntries } bind FeatureEntries::class
}

/** How long the cache's flows stay collected after the screen stops reading them, as across a rotation. */
private const val STOP_TIMEOUT_MS = 5_000L
