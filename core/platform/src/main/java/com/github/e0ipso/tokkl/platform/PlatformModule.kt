package com.github.e0ipso.tokkl.platform

import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.time.Clock

val platformModule = module {
    single<CoroutineDispatcher>(MainDispatcher) { Dispatchers.Main.immediate }
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<CoroutineDispatcher>(DefaultDispatcher) { Dispatchers.Default }

    // The scope that app-wide components launch in. Like `MainScope()`: a [SupervisorJob] on the
    // main thread. Nothing cancels it, so it lasts as long as the process.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // The process's WorkManager, which TokklApplication initialises with Koin's worker factory.
    single { CurrentWorkManager(androidContext()) }

    single<DefaultNetwork> {
        ConnectivityDefaultNetwork(androidContext().getSystemService(ConnectivityManager::class.java))
    }

    singleOf(::processForeground)

    // The wall clock, for components that read the time on each event, such as the widget's day.
    single<Clock> { Clock.systemUTC() }
}
