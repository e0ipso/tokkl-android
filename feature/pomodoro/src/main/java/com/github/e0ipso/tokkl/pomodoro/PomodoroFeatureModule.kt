package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.picker.pickerSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timersync.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timersync.PomodoroRingingPhase
import com.github.e0ipso.tokkl.timersync.TimerSyncWatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

val pomodoroFeatureModule = module {
    single { PomodoroDefaultsStore(androidContext(), io()) }

    // The lengths a Pomodoro session starts with, kept in Tokkl's four `pomodoro_*` length
    // preferences, or on the phone only where the server lacks them. They load at process start
    // from a file of five values.
    single {
        PomodoroDefaultsSetting(
            store = get<PomodoroDefaultsStore>(),
            client = get<Session>(),
            capabilities = get(),
            account = get(),
            scope = get(),
            io = io(),
        )
    } binds arrayOf(AccountDataOwner::class, StartupLoad::class)

    single<ExactAlarmAccess> {
        PlatformExactAlarms(androidContext().getSystemService(AlarmManager::class.java))
    }
    single {
        AlarmClockPomodoroScheduler(
            androidContext(),
            timerSyncStarts = get<CapabilitiesSource>()::offersTimerApi,
            exactAlarms = get(),
        )
    } binds arrayOf(PomodoroAlarmScheduler::class, TimerSyncWatcher::class)

    single {
        ServicePomodoroRinging(
            androidContext(),
            get<CoroutineDispatcher>(MainDispatcher),
        )
    } binds arrayOf(PomodoroRinging::class, PomodoroRingingPhase::class)

    // The exact alarm and the ring of the server's Pomodoro session, which follow the held timer
    // state while the server offers the timer API (docs/PRD.md W7). Loaded at process start; its
    // first call loads the held state from disk, and waits for the capabilities to load.
    single {
        PomodoroPhaseAlarm(
            timerStates = get(),
            available = get<CapabilitiesSource>()::offersTimerApi,
            alarms = get<AlarmClockPomodoroScheduler>(),
            ringing = get<ServicePomodoroRinging>(),
            io = io(),
            clock = get(),
        )
    } bind PomodoroRingCheck::class
    single { PomodoroAlarmHook(inject(), inject(), inject(), get(), io()) } bind AppHook::class

    singleOf(::PomodoroAlarmRearm)

    singleOf(::AlarmRingingButtons) bind PomodoroRingingButtons::class

    // The session notification, made at process start, which restores what the last process
    // showed. It starts following each account that signs in.
    single {
        val store = PomodoroSessionNotificationStore(androidContext(), io())
        val notifier = PomodoroSessionNotifier(androidContext())
        val held = get<AccountTimerStates>()
        val capabilities = get<CapabilitiesSource>()
        val ringing = get<ServicePomodoroRinging>()
        PomodoroSessionNotificationSync(
            alarm = get(),
            actions = get<TimerActions>(),
            changes = combine(held.held, capabilities.loaded, ringing.state) { _, _, _ -> Unit },
            isSignedIn = get<Session>()::isSignedIn,
            serviceRunning = ringing::running,
            memory = store,
            notifications = notifier,
            scope = get(),
            io = io(),
        )
    } bind SignedInListener::class
    single { PomodoroNotificationHook(inject()) } bind AppHook::class

    singleOf(::SessionNotificationButtons) bind PomodoroSessionButtons::class

    single {
        PomodoroSignOut(alarm = get(), notification = get(), sharedId = PomodoroSessionNotifier(androidContext()))
    } bind AccountDataOwner::class

    viewModel {
        val changes = get<ChangeQueue>()
        val cache = get<AccountCache>()
        val scope = viewModelCoroutineScope()
        val held = get<AccountTimerStates>().held
        PomodoroViewModel(
            held = held,
            controls = get<AccountTimerStates>().controls,
            session = PomodoroSessionEdits(
                held,
                actions = get<TimerActions>(),
                changes = changes,
                sender = get<ChangeSender>()::sendQueued,
            ),
            defaults = get<PomodoroDefaultsSetting>().defaults,
            settings = get<AccountSettingsSource>().state,
            unsent = changes.unsentCount,
            refused = changes.held,
            scope = scope,
            exactAlarms = get(),
            picker = pickerSource(cache.projects, cache.clients, cache.tasks, cache.tags),
            offline = get<DefaultNetwork>().offlineIn(scope),
            saved = get(),
        )
    }
    viewModel {
        PomodoroAlarmViewModel(
            held = get<AccountTimerStates>().session,
            actions = get<TimerActions>(),
            scope = viewModelCoroutineScope(),
            clock = get(),
        )
    }

    single { PomodoroEntries } bind FeatureRoutes::class
    single { PomodoroRequests(notification = inject(), io = io()) } bind FeatureRequests::class

    single { PomodoroBadge(get<AccountTimerStates>().held) } bind PageBadge::class
}
