package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.account.AccountRefreshPipeline
import com.github.e0ipso.tokkl.account.SendThenRefresh
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.lifecycle.MainActivityForeground
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.AccountRefreshSteps
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.AppEvents
import com.github.e0ipso.tokkl.session.ForegroundReads
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.session.SignOutOrder
import com.github.e0ipso.tokkl.settings.SignOutConfirmation
import com.github.e0ipso.tokkl.signin.AccountSessionWriter
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.signin.SignInCooldowns
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.trust.SessionTrust
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsStore
import com.github.e0ipso.tokkl.ui.theme.themeSettingsWithNightMode
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The account's settings, its refresh and capabilities, the theme, trust, sign-in and sign-out. */
internal val accountModule = module {
    single { AccountSettingsStore(androidContext()) } bind AccountDataOwner::class
    single { CapabilitiesStore(androidContext()) }
    single { TimerViewStore(androidContext()) }
    single { ThemeSettingsStore(androidContext()) }
    single { SignInCooldownStore(androidContext()) } bind SignInCooldowns::class

    /**
     * Loaded on first use, which is MainActivity's onCreate, on the main thread, so the first frame
     * knows the theme.
     */
    single { themeSettingsWithNightMode(androidContext(), get(), get(), io()) }

    /** The account settings. The first read loads them from a file of six short values. */
    single { AccountSettingsSource(get<AccountSettingsStore>()) }

    /** What the server offers. The first read loads it from a file of a few flags. */
    single { CapabilitiesSource(get<CapabilitiesStore>()) }

    single { AccountRefreshRequests() }

    /**
     * Each refresh refreshes the capabilities and the cache too, with the same `GET /api/v9/me`.
     * Then, in this order, the Timer view, the Pomodoro defaults and the reminder take their
     * preferences from the settings' `GET /api/v9/me/preferences`, the [ChangeSender] sends the
     * queue, since the server has just answered, and the timer sync follows the capabilities.
     */
    single {
        AccountRefreshPipeline(
            capabilities = get(),
            timerStates = get(),
            cache = get(),
            listener = AccountRefreshListener.inOrder(
                listOf(
                    get<TimerViewSetting>(),
                    get<PomodoroDefaultsSetting>(),
                    get<ReminderSetting>(),
                    get<ChangeSender>(),
                    get<TimerSyncFollower>(),
                ),
            ),
        )
    } bind AccountRefreshSteps::class

    single { AccountRefresh(client = get<Session>(), settings = get(), scope = get(), io = io(), steps = get<AccountRefreshPipeline>()) } bind AccountDataOwner::class

    /**
     * Refreshes the account, and with it the running entry and the cache, each time the app comes
     * to the foreground and each time the network comes back while it is there.
     * [MainActivityForeground] reports MainActivity's starts, stops and network changes. Nothing
     * refreshes on a timer.
     */
    single<ForegroundReads> { SendThenRefresh(get(), get()) }
    single { ForegroundRefresh(get()) }

    /** MainActivity's starts and stops and the network's reports, for the screens' view models. */
    single { AppEvents() }

    /** MainActivity's lifecycle observer: the foreground refresh, the timer sync and [AppEvents]. One for each MainActivity. */
    factory {
        MainActivityForeground(
            context = androidContext(),
            refresh = get(),
            events = get(),
            timerSync = get(),
            runningNotification = get(),
            pomodoroNotification = get(),
            scope = get(),
            io = io(),
        )
    }

    /**
     * The Timer page's List or Calendar choice, kept in Tokkl's `timerView` preference. The first
     * read loads it from a file of two values: the Timer page's first frame on the main thread.
     */
    single {
        TimerViewSetting(
            store = get<TimerViewStore>(),
            client = get<Session>(),
            keepsTimerView = get<CapabilitiesSource>()::keepsPreferences,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class

    /**
     * The trust refusals of the signed-in session's requests, which the [Session] reports and the
     * navigation host prompts about. Accepting one refreshes the account settings, the request
     * that ran into it.
     */
    single { SessionTrust(trust = get<ServerTrustStore>(), retry = get<AccountRefreshRequests>()::refresh, scope = get(), io = io()) } bind AccountDataOwner::class

    single { AccountSessionWriter(get(), get(), get(), get(), get(), get()) } bind SessionWriter::class

    signOutSteps()

    /** Sign-out (docs/PRD.md S6): every [AccountDataOwner], in [SignOutOrder]. */
    single { SignOut(owners = getAll(), io = io()) }

    /** The Settings page's sign-out button and its warning about unsent changes. */
    single { SignOutConfirmation(unsentCount = get<ChangeQueue>()::unsentNow, clear = get<SignOut>()::run, scope = get()) }
}
