package com.github.e0ipso.tokkl.di

import android.net.ConnectivityManager
import androidx.work.WorkManager
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.StoredServer
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.platform.ConnectivityDefaultNetwork
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.platform.DefaultDispatcher
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.ExportAccount
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
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import java.time.Clock

/** Dispatchers, the application scope, the wall clock, the shared HTTP client and the signed-in [Session]. */
internal val coreModule = module {
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<CoroutineDispatcher>(DefaultDispatcher) { Dispatchers.Default }

    // The scope that app-wide components launch in. Like `MainScope()`: a [SupervisorJob] on the
    // main thread. Nothing cancels it, so it lasts as long as the process.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // The process's WorkManager, which TokklApplication initialises with Koin's worker factory.
    single { CurrentWorkManager { WorkManager.getInstance(androidContext()) } }

    // The default network, which the activity, the pickers and the timer sync service follow.
    single<DefaultNetwork> {
        ConnectivityDefaultNetwork(androidContext().getSystemService(ConnectivityManager::class.java))
    }

    // The wall clock, for components that read the time on each event, such as the widget's day.
    single<Clock> { Clock.systemUTC() }

    single { ServerConfigStore(androidContext()) } bind StoredServer::class
    single { ApiTokenStore(androidContext()) }
    single { ServerTrustStore(androidContext()) } binds arrayOf(ServerTrust::class, AccountDataOwner::class)

    // Every process sends requests only to the stored server, and to a plain http:// server only
    // once the user has confirmed it, through the one HTTP client and its connection pool.
    single { ConfiguredServer(get()) }
    single { SharedHttpClient(server = get(), trust = get<ServerTrust>()) }

    // The report exports' account, which the Session invalidates on every change of account.
    single { ExportAccount() }

    // Reports trust refusals to the [SessionTrust], and answers to the [LastAnswer], which
    // loads on the first answer, off the main thread.
    single {
        Session(
            servers = get<ServerConfigStore>(),
            tokens = get<ApiTokenStore>(),
            http = get(),
            io = get(IoDispatcher),
            trustListener = get<SessionTrust>(),
            successListener = get<LastAnswer>(),
            exports = get(),
            exportFiles = get(),
        )
    } binds arrayOf(ApiClientProvider::class, AccountDataOwner::class)
}
