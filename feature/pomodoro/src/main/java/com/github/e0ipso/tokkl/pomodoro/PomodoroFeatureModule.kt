package com.github.e0ipso.tokkl.pomodoro

import android.app.NotificationManager
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.timerstate.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timerstate.PomodoroRingingPhase
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.timerstate.TimerSyncWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
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
        AlarmClockPomodoroScheduler(androidContext(), timerSyncStarts = get<CapabilitiesSource>()::offersTimerApi)
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
            held = get<AccountTimerStates>()::held,
            available = get<CapabilitiesSource>()::offersTimerApi,
            alarms = get<AlarmClockPomodoroScheduler>(),
            ringing = get<ServicePomodoroRinging>(),
            io = io(),
        )
    }

    single {
        AccountPomodoroNotification(androidContext(), get(), get(), get(), get(), get(), get(), get(), io())
    } bind SignedInListener::class

    // Sign-out: the alarm, the session notification and the notification ID they share.
    single {
        val notifications = androidContext().getSystemService(NotificationManager::class.java)
        PomodoroSignOut(
            alarm = get(),
            notification = get(),
            cancelSharedId = { notifications.cancel(PomodoroSessionNotifier.NOTIFICATION_ID) },
        )
    } bind AccountDataOwner::class

    viewModel {
        val changes = get<ChangeQueue>()
        val sender = get<ChangeSender>()
        val alarms = get<PomodoroAlarmScheduler>()
        PomodoroViewModel(
            held = get<HeldTimerFlows>().held,
            controls = get<TimerControlsSource>().controls,
            actions = get<TimerActions>(),
            changes = changes,
            defaults = get<PomodoroDefaultsSetting>().defaults,
            settings = get<AccountSettingsSource>().state,
            unsent = changes.unsentCount,
            refused = changes.held,
            scope = viewModelCoroutineScope(),
            sendQueued = sender::sendAndWait,
            alarmProbe = alarms::permissionDenied,
        )
    }
    viewModel {
        PomodoroAlarmViewModel(
            held = get<HeldTimerFlows>().session,
            actions = get<TimerActions>(),
            scope = viewModelCoroutineScope(),
        )
    }

    single { PomodoroEntries } bind FeatureEntries::class
}
