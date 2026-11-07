package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.reports.ReportExportFiles
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.trust.SessionTrust
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module
import java.time.Clock

/** Dispatchers, the application scope, the wall clock and the signed-in [Session]. */
internal val coreModule = module {
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<CoroutineDispatcher>(DefaultDispatcher) { Dispatchers.Default }

    /**
     * The scope that app-wide components launch in. Like `MainScope()`: a [SupervisorJob] on the
     * main thread. Nothing cancels it, so it lasts as long as the process.
     */
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    /** The wall clock, for components that read the time on each event, such as the widget's day. */
    single<Clock> { Clock.systemUTC() }

    single { ServerConfigStore(androidContext()) }
    single { ApiTokenStore(androidContext()) }
    single { ServerTrustStore(androidContext()) } binds arrayOf(ServerTrust::class, AccountDataOwner::class)

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
    } binds arrayOf(ApiClientProvider::class, AccountDataOwner::class)
}
