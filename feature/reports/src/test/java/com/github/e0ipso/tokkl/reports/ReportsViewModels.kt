package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * A Reports view model for tests that leave the filter sheet and the CSV export alone: no filter
 * options, no export capability, and an export that no test starts and so writes no file. The app
 * stays in the foreground with no network returns unless a test passes its own [app].
 */
internal fun reportsViewModel(
    accountSettings: StateFlow<AccountSettings?>,
    repository: ReportsRepository,
    scope: CoroutineScope,
    io: CoroutineContext,
    clock: Clock = Clock.systemUTC(),
    app: AppForeground = AppForeground(MutableStateFlow(true), emptyFlow()),
) = ReportsViewModel(
    accountSettings = accountSettings,
    repository = repository,
    scope = scope,
    io = io,
    clock = clock,
    filterOptions = MutableStateFlow(FilterOptions.NONE),
    exportCapabilities = MutableStateFlow(Capabilities.NONE),
    exporter = ReportExportFiles(File("unused-report-exports"), clock).let { files ->
        ReportExportController(
            client = { null },
            files = files,
            account = ExportAccount(files),
            csvMissing = {},
            scope = scope,
            io = io,
        )
    },
    app = app,
)
