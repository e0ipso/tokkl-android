package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.lifecycle.AppRefreshSteps
import com.github.e0ipso.tokkl.lifecycle.MainActivityForeground
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountRefreshSteps
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountTransition
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

internal val lifecycleModule = module {
    // A sign-in's account transition, in core:session, with the steps of the modules above it.
    single {
        val queue = get<ChangeSender>()
        AccountTransition(
            storeSettings = get<AccountSettingsSource>()::store,
            clearTimerState = get<AccountTimerStates>()::clearForSignIn,
            account = get(),
            storeSession = get<Session>()::save,
            signedIn = getAll(),
            queuePaused = { queue.signInNeeded.value },
            resumeQueue = queue::resume,
            followTimerSync = get<TimerSyncFollower>()::follow,
        )
    }

    single {
        AppRefreshSteps(
            capabilities = get(),
            timerStates = get(),
            cache = get(),
            timerView = get(),
            pomodoroDefaults = get(),
            reminder = get(),
            sendQueue = get<ChangeSender>()::send,
            timerSync = get(),
        )
    } bind AccountRefreshSteps::class

    // MainActivity's lifecycle observer: the foreground refresh and every module's
    // foreground hooks. One for each MainActivity.
    factory {
        MainActivityForeground(
            context = androidContext(),
            refresh = get(),
            hooks = get(),
            http = get(),
            defaultNetwork = get(),
            scope = get(),
            main = get(MainDispatcher),
            io = io(),
        )
    }
}
