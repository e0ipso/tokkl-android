package com.github.e0ipso.tokkl

import android.app.Activity
import android.app.Application
import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.lifecycle.AppStartup
import com.github.e0ipso.tokkl.platform.MainActivityOwner
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * Starts Koin, which also sets up WorkManager with Koin's worker factory, then runs [AppStartup].
 * The manifest leaves out WorkManager's own startup initializer, so WorkManager starts here, still
 * at process start. A subclass can add modules through [koinModules]. It names [MainActivity] for
 * the notifications, widgets and alarms that open it from below the app shell.
 */
open class TokklApplication :
    Application(),
    MainActivityOwner {

    override val mainActivity: Class<out Activity> = MainActivity::class.java

    /** The modules Koin starts with. A later module's definition replaces an earlier one of the same type. */
    protected open fun koinModules(): List<Module> = appModules

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TokklApplication)
            modules(koinModules())
            // After the modules, so a worker WorkManager starts at once finds its definition.
            workManagerFactory()
        }
        get<AppStartup>().run()
    }
}
