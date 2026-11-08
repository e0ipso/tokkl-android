package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navhost.AppNavigationViewModel
import com.github.e0ipso.tokkl.navhost.SignedInViewModel
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.sync.SyncStatusSource
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The top-level pages, the navigation host's view model, and the signed-in frame's, which lives in
 * the session's store.
 */
internal val navigationModule = module {
    // Every feature's top-level pages, in order.
    single { TopLevelPages(getAll()) }

    viewModel {
        AppNavigationViewModel(
            features = getAll(),
            pages = get(),
            signOuts = get<AccountLifetime>().signOuts,
            capabilities = get<CapabilitiesSource>().state,
            session = get(),
            pomodoroNotification = get(),
            io = io(),
            scope = viewModelCoroutineScope(),
        )
    }

    viewModel {
        SignedInViewModel(
            timerView = get<TimerViewSetting>().view,
            timerRunning = get<AccountCache>().running.map { it != null },
            pomodoroRunning = get<AccountTimerStates>().held.map { it?.state?.pomodoro != null },
            syncStatus = get<SyncStatusSource>().status,
            discarded = get(),
            changes = get(),
            trust = get(),
            servers = get(),
            settings = get<AccountSettingsSource>().state,
            io = io(),
            scope = viewModelCoroutineScope(),
        )
    }
}
