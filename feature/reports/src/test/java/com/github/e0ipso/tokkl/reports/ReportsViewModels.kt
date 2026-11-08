package com.github.e0ipso.tokkl.reports

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.platform.AppForeground
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    choices: ReportChoices = ReportChoices(),
) = ReportsViewModel(
    choices = choices,
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

/**
 * The Reports route on the back stack in a test, as the navigation host keeps it: the route's own
 * store holds [model], which [make] builds in a scope of its own under [scope], and the session's
 * [choices] outlive it. [reopen] takes the route off the stack, which clears the model, and opens
 * it again with a new one.
 */
internal class TestReportsRoute(
    private val scope: CoroutineScope,
    private val make: (choices: ReportChoices, scope: CoroutineScope) -> ReportsViewModel,
) {
    val choices = ReportChoices()
    private val store = ViewModelStore()

    var model: ReportsViewModel = open()
        private set

    /** The route leaves the back stack and comes back: the model is a new one, the choices the same. */
    fun reopen(): ReportsViewModel {
        store.clear()
        model = open()
        return model
    }

    /** Takes the route off the back stack, which clears its model. */
    fun leave() = store.clear()

    private fun open(): ReportsViewModel {
        val own = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        return ViewModelProvider.create(store, viewModelFactory { initializer { make(choices, own) } })[
            ReportsViewModel::class,
        ]
    }
}
