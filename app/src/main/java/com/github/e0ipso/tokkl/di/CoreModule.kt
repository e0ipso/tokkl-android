package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.reports.ReportExportFiles
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.trust.SessionTrust
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

/** [Dispatchers.IO], for disk and network work. */
val IoDispatcher: Qualifier = named("IoDispatcher")

/** [Dispatchers.Default], which the timer event stream's clock runs on. */
val DefaultDispatcher: Qualifier = named("DefaultDispatcher")

/** The clock the home screen widget reads on each of its events. */
val WidgetClock: Qualifier = named("WidgetClock")

/** The IO dispatcher, for definitions. */
internal fun Scope.io(): CoroutineDispatcher = get(IoDispatcher)

/** Dispatchers, the application scope, the clock and the signed-in [Session]. */
internal val coreModule = module {
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<CoroutineDispatcher>(DefaultDispatcher) { Dispatchers.Default }

    /**
     * The scope that app-wide components launch in. Like `MainScope()`: a [SupervisorJob] on the
     * main thread. Nothing cancels it, so it lasts as long as the process.
     */
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    single<Clock>(WidgetClock) { Clock.systemUTC() }

    single { ServerConfigStore(androidContext()) }
    single { ApiTokenStore(androidContext()) }
    single { ServerTrustStore(androidContext()) } bind ServerTrust::class

    single { ReportExportFiles(File(androidContext().cacheDir, ReportExportFiles.DIRECTORY)) }

    /**
     * Reports trust refusals to the [SessionTrust], and answers to the [LastAnswer], which
     * loads on the first answer, off the main thread.
     */
    single {
        Session(
            servers = get(),
            tokens = get(),
            trustListener = get<SessionTrust>(),
            successListener = get<LastAnswer>(),
            clearExports = get<ReportExportFiles>()::clear,
        )
    } bind ApiClientProvider::class
}
