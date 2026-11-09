package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.timerstate.HeldFieldsMemory
import com.github.e0ipso.tokkl.timerstate.PomodoroWidgetRedraw
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** The Timer and Pomodoro home screen widgets: what they show, the fields they remember and their redraws. */
val widgetFeatureModule = module {
    single {
        PomodoroWidgetFieldsStore(
            androidContext(),
            io(),
        )
    } binds arrayOf(HeldFieldsMemory::class, AccountDataOwner::class)

    single {
        WidgetStateSource(
            application = androidApplication(),
            cache = get(),
            accountSettings = get<AccountSettingsSource>().loaded,
            capabilities = get<CapabilitiesSource>().loaded,
            timerControls = get<TimerControlsSource>().controls,
            workManager = get(),
            clock = get(),
            io = io(),
        )
    }
    single { TimerWidgetHook(inject(), inject(), get(), io()) } bind AppHook::class

    single {
        PomodoroWidgetStateSource(
            fields = get(),
            timerStates = get(),
            projects = get<AccountCache>().projects,
            tasks = get<AccountCache>().tasks,
            ringing = get(),
            capabilities = get<CapabilitiesSource>().loaded,
            accountSettings = get<AccountSettingsSource>().loaded,
            pomodoroDefaults = get(),
            signedIn = get(),
            clock = get(),
            io = io(),
        )
    }

    // What the phase alarm redraws when it could not arm an exact alarm.
    factory<PomodoroWidgetRedraw> { PomodoroWidgetRedrawer(get(), get()) }
    single { PomodoroWidgetHook(inject(), inject(), get(), io()) } bind AppHook::class

    // The Pomodoro widget's Start, Next phase and End session, as expedited work that outlives
    // the widget's broadcast.
    worker { params ->
        PomodoroWidgetActionWorker(
            androidContext(),
            params.get(),
            timerStates = get(),
            client = get(),
            signIn = get(),
            widgetStates = get(),
            failures = get(),
            scope = get(),
            io = io(),
        )
    }
}
