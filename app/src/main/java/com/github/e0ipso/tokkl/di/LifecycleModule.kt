package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.lifecycle.AppRefreshSteps
import com.github.e0ipso.tokkl.lifecycle.MainActivityForeground
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountRefreshSteps
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * What spans the modules: what each account refresh does after its answer, and MainActivity's
 * lifecycle observer.
 */
internal val lifecycleModule = module {
    single {
        AppRefreshSteps(
            capabilities = get(),
            timerStates = get(),
            cache = get(),
            timerView = get(),
            pomodoroDefaults = get(),
            reminder = get(),
            sender = get(),
            timerSync = get(),
        )
    } bind AccountRefreshSteps::class

    // MainActivity's lifecycle observer: the foreground refresh and every module's
    // foreground hooks. One for each MainActivity.
    factory {
        MainActivityForeground(
            context = androidContext(),
            refresh = get(),
            hooks = getAll(),
            http = get(),
            defaultNetwork = get(),
            scope = get(),
            main = get(MainDispatcher),
            io = io(),
        )
    }
}
