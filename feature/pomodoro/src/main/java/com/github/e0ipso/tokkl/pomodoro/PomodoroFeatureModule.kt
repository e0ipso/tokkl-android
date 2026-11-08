package com.github.e0ipso.tokkl.pomodoro

import android.app.AlarmManager
import android.app.NotificationManager
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.topLevelDestination
import com.github.e0ipso.tokkl.picker.pickerSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.offlineIn
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timerstate.PomodoroRingingPhase
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.timerstate.TimerSyncWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The Pomodoro defaults, the phase alarm, its ring, the session notification and the Pomodoro page. */
val pomodoroFeatureModule = module {
    single { PomodoroDefaultsStore(androidContext()) }

    // The lengths a Pomodoro session starts with, kept in Tokkl's four `pomodoro_*` length
    // preferences, or on the phone only where the server lacks them. The first read loads them
    // from a file of five values.
    single {
        PomodoroDefaultsSetting(
            store = get<PomodoroDefaultsStore>(),
            client = get<Session>(),
            keepsPreferences = get<CapabilitiesSource>()::keepsPreferences,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class

    // The exact alarm at a running Pomodoro phase's planned end.
    // [PomodoroAlarmScheduler.permissionDenied] is what the Pomodoro page reads when that alarm
    // cannot be set, from the platform. Its boot and update receiver also starts the timer sync
    // service.
    single {
        val manager = androidContext().getSystemService(AlarmManager::class.java)
        AlarmClockPomodoroScheduler(
            androidContext(),
            timerSyncStarts = get<CapabilitiesSource>()::offersTimerApi,
            exactAlarms = { !manager.canScheduleExactAlarms() },
        )
    } binds arrayOf(PomodoroAlarmScheduler::class, TimerSyncWatcher::class)

    // The ringing service at a Pomodoro phase's planned end, as [PomodoroPhaseAlarm] drives it and the Pomodoro widget
    // reads it.
    single {
        ServicePomodoroRinging(
            androidContext(),
        )
    } binds arrayOf(PomodoroRinging::class, PomodoroRingingPhase::class)

    // The exact alarm and the ring of the server's Pomodoro session, which follow the held timer
    // state while the server offers the timer API (docs/PRD.md W7). Loaded at process start; its
    // first call loads the held state and the capabilities from disk.
    single {
        PomodoroPhaseAlarm(
            held = get<AccountTimerStates>()::heldNow,
            available = get<CapabilitiesSource>()::offersTimerApi,
            alarms = get<AlarmClockPomodoroScheduler>(),
            ringing = get<ServicePomodoroRinging>(),
            io = io(),
            clock = get(),
        )
    } bind PomodoroRingCheck::class
    single(named("hook-pomodoro-alarm")) {
        val alarm = inject<PomodoroPhaseAlarm>()
        val timerStates = inject<AccountTimerStates>()
        val capabilities = inject<CapabilitiesSource>()
        val scope = get<CoroutineScope>()
        val io = io()
        AppHook(
            AppHookOrder.POMODORO_ALARM,
            // Arms, moves and drops the phase alarm and its ring on every held state and capability
            // change, from the stored state at process start on, also with the app in the background.
            processStart = {
                scope.launch(io) {
                    combine(timerStates.value.held, flow { emitAll(capabilities.value.state) }) { _, _ -> Unit }
                        .collect { alarm.value.follow() }
                }
            },
        )
    }

    // What the boot, update, clock and exact-alarm broadcasts do: arm the held phase again, and
    // start the timer sync service after boot and an update.
    single { PomodoroAlarmRearm(alarm = get(), timerSync = get<TimerSyncFollower>()) }

    // What the ringing notification's buttons do.
    single<PomodoroRingingButtons> {
        AlarmRingingButtons(androidContext(), alarm = get(), actions = get<TimerActions>())
    }

    // The session notification, made at process start, which restores what the last process
    // showed. It starts following each account that signs in.
    single {
        val store = PomodoroSessionNotificationStore(androidContext())
        val notifier = PomodoroSessionNotifier(androidContext())
        val held = get<AccountTimerStates>()
        val capabilities = get<CapabilitiesSource>()
        val ringing = get<ServicePomodoroRinging>()
        val session = get<Session>()
        PomodoroSessionNotificationSync(
            alarm = get(),
            actions = get<TimerActions>(),
            changes = combine(held.held, flow { emitAll(capabilities.state) }, ringing.state) { _, _, _ -> Unit },
            session = session,
            serviceRunning = { ringing.state.value.serviceRunning },
            memory = store,
            notifications = notifier,
            scope = get(),
            io = io(),
        )
    } bind SignedInListener::class
    single(named("hook-pomodoro-notification")) {
        val sync = inject<PomodoroSessionNotificationSync>()
        AppHook(
            AppHookOrder.POMODORO_NOTIFICATION,
            processStart = { sync.value.start() },
            notificationsAllowed = { sync.value.refresh(force = true) },
        )
    }

    // What the session notification's buttons do.
    single<PomodoroSessionButtons> { SessionNotificationButtons(androidContext(), notification = get()) }

    // Sign-out: the alarm, the session notification and the notification ID they share.
    single {
        val notifications = androidContext().getSystemService(NotificationManager::class.java)
        PomodoroSignOut(
            alarm = get(),
            notification = get(),
            sharedId = { notifications.cancel(PomodoroSessionNotifier.NOTIFICATION_ID) },
        )
    } bind AccountDataOwner::class

    viewModel {
        val changes = get<ChangeQueue>()
        val sender = get<ChangeSender>()
        val alarms = get<PomodoroAlarmScheduler>()
        val scope = viewModelCoroutineScope()
        PomodoroViewModel(
            held = get<AccountTimerStates>().held,
            controls = get<AccountTimerStates>().controls,
            actions = get<TimerActions>(),
            changes = changes,
            defaults = get<PomodoroDefaultsSetting>().defaults,
            settings = get<AccountSettingsSource>().state,
            unsent = changes.unsentCount,
            refused = changes.held,
            scope = scope,
            sendQueued = sender::sendQueued,
            alarmProbe = { alarms.permissionDenied },
            picker = get<AccountCache>().pickerSource(),
            offline = get<DefaultNetwork>().offlineIn(scope),
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

    single { PomodoroEntries } bind FeatureEntries::class
    topLevelDestination(pomodoroPage)
}
