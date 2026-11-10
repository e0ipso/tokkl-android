package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File

val reportsFeatureModule = module {
    singleOf(::ServerReportsRepository)
    single {
        ReportExportFiles(File(androidContext().cacheDir, ReportExportFiles.DIRECTORY), get())
    }

    // The exports' account, which every sign-in and sign-out invalidates, deleting the files.
    singleOf(::ExportFilesCleanup) binds arrayOf(AccountDataOwner::class, SignedInListener::class)

    // The tab and the filters, in the signed-in session's store, which the next visit opens on.
    viewModelOf(::ReportChoices)

    // The Reports route's own view model, which goes when the route leaves the back stack.
    viewModel { (choices: ReportChoices) ->
        val session = get<Session>()
        val accountSettings = get<AccountSettingsSource>()
        val capabilities = get<CapabilitiesSource>()
        val cache = get<AccountCache>()
        // The exporter shares the view model's scope, so clearing the view model cancels it too.
        val scope = viewModelCoroutineScope()
        ReportsViewModel(
            choices = choices,
            accountSettings = accountSettings.state,
            repository = get(),
            clock = get(),
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
            app = get(),
            saved = get(),
        )
    }

    single { ReportsEntries } bind FeatureRoutes::class
    single { ReportsRequests } bind FeatureRequests::class
}

/** How long the cache's flows stay collected after the screen stops reading them, as across a rotation. */
private const val STOP_TIMEOUT_MS = 5_000L
