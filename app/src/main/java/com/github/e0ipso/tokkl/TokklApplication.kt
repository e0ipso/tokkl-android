package com.github.e0ipso.tokkl

import android.app.Application
import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.lifecycle.AppStartup
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

/**
 * Starts Koin, which also sets up WorkManager with Koin's worker factory, then runs [AppStartup].
 * The manifest leaves out WorkManager's own startup initializer, so WorkManager starts here, still
 * at process start.
 */
class TokklApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TokklApplication)
            modules(appModules)
            // After the modules, so a worker WorkManager starts at once finds its definition.
            workManagerFactory()
        }
        get<AppStartup>().run()
    }
}
