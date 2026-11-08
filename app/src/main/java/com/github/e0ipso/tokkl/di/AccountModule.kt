package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.lifecycle.MainActivityForeground
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.AccountRefreshSteps
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower
import com.github.e0ipso.tokkl.trust.SessionTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The account's settings, its refresh and capabilities, trust and sign-out. */
internal val accountModule = module {
    single { AccountSettingsStore(androidContext()) } bind AccountDataOwner::class
    single { CapabilitiesStore(androidContext()) }
    // The account settings. The first read loads them from a file of six short values.
    single { AccountSettingsSource(get<AccountSettingsStore>()) }

    // What the server offers. The first read loads it from a file of a few flags.
    single { CapabilitiesSource(get<CapabilitiesStore>()) }

    single { AccountRefreshRequests() }
    single(named("hook-refresh-requests")) {
        val requests = get<AccountRefreshRequests>()
        val refresh = inject<AccountRefresh>()
        val scope = get<CoroutineScope>()
        val io = io()
        AppHook(AppHookOrder.REFRESH_REQUESTS, processStart = { scope.launch(io) { requests.forward(refresh) } })
    }

    // What each refresh does after `GET /api/v9/me` answered, in this order, with the same answers:
    // the capabilities refresh and feed the account's timer state what their probe read, and the
    // cache refreshes with those capabilities. Then the Timer view, the Pomodoro defaults and the
    // reminder take their preferences from `GET /api/v9/me/preferences`, the [ChangeSender] sends
    // the queue, since the server has just answered, and the timer sync follows the capabilities,
    // since any refresh can learn or lose the timer API.
    single {
        val capabilities = get<CapabilitiesSource>()
        val timerStates = get<AccountTimerStates>()
        val cache = get<AccountCache>()
        val timerView = get<TimerViewSetting>()
        val pomodoroDefaults = get<PomodoroDefaultsSetting>()
        val reminder = get<ReminderSetting>()
        val sender = get<ChangeSender>()
        val timerSync = get<TimerSyncFollower>()
        AccountRefreshSteps { api, me, preferences ->
            val flags = capabilities.refresh(api, me, timerStates.source)
            cache.refresh(api, me, preferences, flags, onAllDayMissing = capabilities::allDayMissing)
            timerView.refreshed(preferences, keepsTimerView = flags.favorites)
            pomodoroDefaults.refreshed(preferences, keepsPreferences = flags.favorites)
            reminder.refreshed(preferences, tokkl = flags.favorites)
            sender.send()
            timerSync.follow()
        }
    }

    single {
        AccountRefresh(
            client = get<Session>(),
            settings = get(),
            scope = get(),
            io = io(),
            steps = get(),
        )
    } bind AccountDataOwner::class

    // Refreshes the account, and with it the running entry and the cache, each time the app comes
    // to the foreground and each time the network comes back while it is there.
    // [MainActivityForeground] reports MainActivity's starts, stops and network changes. Nothing
    // refreshes on a timer.
    // Each read tries the queued changes first, then refreshes the account. The send does not wait
    // for the refresh, so an expired token still asks for sign-in when `/me` fails on opening the
    // app with a saved queue.
    single {
        val sender = get<ChangeSender>()
        val account = get<AccountRefresh>()
        ForegroundRefresh(
            refresh = {
                sender.send()
                account.refresh()
            },
            refreshOnReconnect = {
                sender.send()
                account.refreshOnReconnect()
            },
        )
    }

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

    // The trust refusals of the signed-in session's requests, which the [Session] reports and the
    // navigation host prompts about. Accepting one refreshes the account settings, the request
    // that ran into it.
    single {
        SessionTrust(
            trust = get<ServerTrustStore>(),
            retry = get<AccountRefreshRequests>()::refresh,
            scope = get(),
            io = io(),
        )
    } binds arrayOf(TrustListener::class, AccountDataOwner::class)

    signOutSteps()

    // Where each account begins and ends, which the timer state and the navigation follow.
    single { AccountLifetime() }

    // Sign-out (docs/PRD.md S6): every [AccountDataOwner], phase by phase in [SignOutPhase] order.
    single { SignOut(owners = getAll(), lifetime = get(), io = io()) }
}
