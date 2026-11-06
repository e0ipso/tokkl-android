package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.session.SignOutSteps
import com.github.e0ipso.tokkl.settings.SignOutConfirmation
import com.github.e0ipso.tokkl.settings.ThemeSettingsRepository
import com.github.e0ipso.tokkl.settings.ThemeSettingsStore
import com.github.e0ipso.tokkl.settings.syncApplicationNightMode
import com.github.e0ipso.tokkl.signin.AccountSessionWriter
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.trust.SessionTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The account's settings and capabilities, the theme, trust, sign-in and sign-out. */
internal val accountModule = module {
    single { AccountSettingsStore(androidContext()) }
    single { CapabilitiesStore(androidContext()) }
    single { TimerViewStore(androidContext()) }
    single { ThemeSettingsStore(androidContext()) }
    single { SignInCooldownStore(androidContext()) }

    /**
     * Loaded on first use, which is MainActivity's onCreate, on the main thread. The first frame
     * has to know the theme, or launch would show the other mode's colours first. Android learns
     * the chosen mode too, for the starting window of the next launch.
     */
    single {
        val store = get<ThemeSettingsStore>()
        val scope = get<CoroutineScope>()
        ThemeSettingsRepository(initial = store.load(), save = store::save, scope = scope, io = io()).also { themes ->
            syncApplicationNightMode(androidContext(), themes.state.map { it.mode }, scope, io())
        }
    }

    /**
     * Loaded on first use, which reads the stored settings from disk. That is usually
     * MainActivity's onStart on the main thread, and the file holds six short values. Each
     * refresh refreshes the capabilities and the cache too, with the same `GET /api/v9/me`, and
     * the cache takes the favorites, the Timer view its view and the Pomodoro defaults their
     * lengths from the settings' `GET /api/v9/me/preferences`. Then the [ChangeSender] sends the
     * queue, since the server has just answered.
     */
    single {
        val store = get<AccountSettingsStore>()
        val capabilities = inject<CapabilitiesSource>()
        val timerStates = inject<AccountTimerStates>()
        val cache = inject<AccountCache>()
        val timerView = inject<TimerViewSetting>()
        val pomodoroDefaults = inject<PomodoroDefaultsSetting>()
        val reminderSettings = inject<ReminderSetting>()
        val sender = inject<ChangeSender>()
        val timerSync = inject<TimerSyncFollower>()
        AccountSettingsSource(
            initial = store.load(),
            save = store::save,
            client = get<Session>()::client,
            scope = get(),
            io = io(),
            onRefresh = { api, me, preferences ->
                val flags = capabilities.value.refresh(api, me, timerStates.value.source)
                cache.value.refresh(api, me, preferences, flags, onAllDayMissing = capabilities.value::allDayMissing)
                timerView.value.refreshed(preferences, keepsTimerView = flags.favorites)
                pomodoroDefaults.value.refreshed(preferences, keepsPreferences = flags.favorites)
                reminderSettings.value.refreshed(preferences, tokkl = flags.favorites)
                sender.value.send()
                // The first refresh after sign-in learns the capability, and any refresh can lose it.
                timerSync.value.follow()
            },
        )
    }

    /**
     * Refreshes the account, and with it the running entry and the cache, each time the app comes
     * to the foreground and each time the network comes back while it is there. MainActivity
     * reports its starts, stops and network changes. It also tries queued changes independently
     * of the refresh, so an expired token still asks for sign-in when `/me` fails on opening the
     * app with a saved queue. Nothing refreshes on a timer.
     */
    single {
        val sender = inject<ChangeSender>()
        val accountSettings = inject<AccountSettingsSource>()
        ForegroundRefresh(
            refresh = {
                sender.value.send()
                accountSettings.value.refresh()
            },
            refreshOnReconnect = {
                sender.value.send()
                accountSettings.value.refreshOnReconnect()
            },
        )
    }

    /** What the server offers. Loaded on first use from a file of four flags. */
    single {
        val store = get<CapabilitiesStore>()
        CapabilitiesSource(initial = store.load(), save = store::save)
    }

    /**
     * The Timer page's List or Calendar choice, kept in Tokkl's `timerView` preference. Loaded on
     * first use, which reads a file of two values: the Timer page's first frame on the main thread.
     */
    single {
        val store = get<TimerViewStore>()
        val capabilities = inject<CapabilitiesSource>()
        TimerViewSetting(
            initial = store.load(),
            save = store::save,
            client = get<Session>()::client,
            keepsTimerView = { capabilities.value.state.value.favorites },
            scope = get(),
            io = io(),
        )
    }

    /**
     * The trust refusals of the signed-in session's requests, which the [Session] reports and the
     * navigation host prompts about. Accepting one refreshes the account settings, the request
     * that ran into it.
     */
    single {
        val accountSettings = inject<AccountSettingsSource>()
        SessionTrust(
            trust = get(),
            retry = { accountSettings.value.refresh() },
            scope = get(),
            io = io(),
        )
    }

    single { AccountSessionWriter(inject(), get(), get(), get(), inject(), get()) } bind SessionWriter::class

    single {
        SignOutSteps(
            context = androidContext(),
            session = get(),
            timerStates = get(),
            pomodoroAlarm = get(),
            timerSync = inject(),
            timerSyncFollower = get(),
            pomodoroNotification = get(),
            accountSettings = inject(),
            sender = inject(),
            cache = inject(),
            reminder = inject(),
            reminderSettings = inject(),
            runningNotification = inject(),
            favoriteShortcuts = inject(),
            capabilities = inject(),
            alarmClock = get(),
            timerView = inject(),
            pomodoroDefaults = inject(),
            sessionTrust = get(),
            lastAnswer = inject(),
            discarded = inject(),
            stores = SignOutSteps.Stores(
                timerState = get(),
                pomodoroWidgetFields = get(),
                reminder = get(),
                accountSettings = get(),
                capabilities = get(),
                timerView = get(),
                pomodoroDefaults = get(),
                serverTrust = get(),
                discardedChanges = get(),
            ),
        )
    }

    /** Sign-out (docs/PRD.md S6), with the steps [SignOutSteps] lists. */
    single { SignOut(steps = get<SignOutSteps>().steps, io = io()) }

    /** The Settings page's sign-out button and its warning about unsent changes. */
    single {
        val changes = inject<ChangeQueue>()
        SignOutConfirmation(unsentCount = { changes.value.unsentCount.first() }, clear = get<SignOut>()::run, scope = get())
    }
}
