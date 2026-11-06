package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.pomodoro.ServicePomodoroRinging
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Pomodoro defaults, the phase alarm, its ring and the session notification. */
internal val pomodoroModule = module {
    single { PomodoroDefaultsStore(androidContext()) }

    /**
     * The lengths a Pomodoro session starts with, kept in Tokkl's four `pomodoro_*` length
     * preferences, or on the phone only where the server lacks them. Loaded on first use, which
     * reads a file of five values.
     */
    single {
        val store = get<PomodoroDefaultsStore>()
        val capabilities = inject<CapabilitiesSource>()
        PomodoroDefaultsSetting(
            initial = store.load(),
            save = store::save,
            client = get<Session>()::client,
            keepsPreferences = { capabilities.value.state.value.favorites },
            scope = get(),
            io = io(),
        )
    }

    /**
     * The exact alarm at a running Pomodoro phase's planned end.
     * [PomodoroAlarmScheduler.permissionDenied] is what the Pomodoro page reads when that alarm
     * cannot be set. Loaded on first use, which reads the permission from the platform. Its boot
     * and update receiver also starts the timer sync service.
     */
    single {
        val capabilities = inject<CapabilitiesSource>()
        AlarmClockPomodoroScheduler(androidContext(), timerSyncStarts = { capabilities.value.state.value.timerState })
    } bind PomodoroAlarmScheduler::class

    /** The ringing service at a Pomodoro phase's planned end, as [PomodoroPhaseAlarm] drives it. */
    single { ServicePomodoroRinging(androidContext()) }

    /**
     * The exact alarm and the ring of the server's Pomodoro session, which follow the held timer
     * state while the server offers the timer API (docs/PRD.md W7). Loaded at process start; its
     * first call loads the held state and the capabilities from disk.
     */
    single {
        val timerStates = get<AccountTimerStates>()
        val capabilities = inject<CapabilitiesSource>()
        PomodoroPhaseAlarm(
            held = { timerStates.source.state.value },
            available = { capabilities.value.state.value.timerState },
            alarms = get<AlarmClockPomodoroScheduler>(),
            ringing = get<ServicePomodoroRinging>(),
            io = io(),
        )
    }

    single { AccountPomodoroNotification(androidContext(), get(), get(), get(), get(), inject(), get(), get(), io()) }
}
