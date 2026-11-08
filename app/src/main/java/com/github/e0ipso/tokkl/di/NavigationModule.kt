package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.navhost.NavigationSession
import com.github.e0ipso.tokkl.navhost.SignedInViewModel
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationSync
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.sync.SyncStatusSource
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Every feature's screens, as the navigation host adds them to its entry provider. */
internal val FeatureScreens = named("feature-screens")

/**
 * The features' screens and top-level pages, what the navigation host asks about the stored
 * session, and the signed-in frame's view model, which lives in the session's store.
 */
internal val navigationModule = module {
    single(FeatureScreens) { getAll<FeatureEntries>() }

    // Every feature's top-level pages, in order.
    single { TopLevelPages(get<List<FeatureEntries>>(FeatureScreens).flatMap { it.pages }) }

    // What the navigation host asks about the stored session, off the main thread: whether somebody
    // is signed in, which the session reads on the IO dispatcher itself, and whether a Pomodoro
    // notification's tap still belongs to the account and its phase on the server.
    single {
        val session = get<Session>()
        val pomodoroNotification = get<PomodoroSessionNotificationSync>()
        val io = io()
        NavigationSession(
            isSignedIn = session::isSignedIn,
            acceptsPomodoroTap = { tap -> withContext(io) { pomodoroNotification.acceptsTap(tap.account, tap.phase) } },
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
