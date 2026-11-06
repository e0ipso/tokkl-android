package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.pomodoro.PomodoroRinging
import com.github.e0ipso.tokkl.pomodoro.ServicePomodoroRinging
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignedInListener
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerSyncWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The Pomodoro defaults, the phase alarm, its ring and the session notification. */
internal val pomodoroModule = module {
    single { PomodoroDefaultsStore(androidContext()) }

    /**
     * The lengths a Pomodoro session starts with, kept in Tokkl's four `pomodoro_*` length
     * preferences, or on the phone only where the server lacks them. The first read loads them
     * from a file of five values.
     */
    single {
        PomodoroDefaultsSetting(
            store = get<PomodoroDefaultsStore>(),
            client = get<Session>(),
            keepsPreferences = get<CapabilitiesSource>()::keepsPreferences,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class

    /**
     * The exact alarm at a running Pomodoro phase's planned end.
     * [PomodoroAlarmScheduler.permissionDenied] is what the Pomodoro page reads when that alarm
     * cannot be set, from the platform. Its boot and update receiver also starts the timer sync
     * service.
     */
    single {
        AlarmClockPomodoroScheduler(androidContext(), timerSyncStarts = get<CapabilitiesSource>()::offersTimerApi)
    } binds arrayOf(PomodoroAlarmScheduler::class, TimerSyncWatcher::class)

    /** The ringing service at a Pomodoro phase's planned end, as [PomodoroPhaseAlarm] drives it. */
    single { ServicePomodoroRinging(androidContext()) } bind PomodoroRinging::class

    /**
     * The exact alarm and the ring of the server's Pomodoro session, which follow the held timer
     * state while the server offers the timer API (docs/PRD.md W7). Loaded at process start; its
     * first call loads the held state and the capabilities from disk.
     */
    single {
        PomodoroPhaseAlarm(
            held = get<AccountTimerStates>()::held,
            available = get<CapabilitiesSource>()::offersTimerApi,
            alarms = get<AlarmClockPomodoroScheduler>(),
            ringing = get<ServicePomodoroRinging>(),
            io = io(),
        )
    } bind AccountDataOwner::class

    single { AccountPomodoroNotification(androidContext(), get(), get(), get(), get(), get(), get(), get(), io()) } binds arrayOf(AccountDataOwner::class, SignedInListener::class)
}
