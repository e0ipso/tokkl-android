package com.github.e0ipso.tokkl.widget

import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.HeldFieldsMemory
import com.github.e0ipso.tokkl.timerstate.PomodoroWidgetRedraw
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/** The Timer widget's store and sync. */
val TimerWidgetQualifier: Qualifier = named("TimerWidget")

/** The Pomodoro widget's store and sync. */
val PomodoroWidgetQualifier: Qualifier = named("PomodoroWidget")

/** The Timer and Pomodoro home screen widgets: what they show, the fields they remember and their redraws. */
val widgetFeatureModule = module {
    single {
        PomodoroWidgetFieldsStore(
            androidContext(),
        )
    } binds arrayOf(HeldFieldsMemory::class, AccountDataOwner::class)

    single {
        WidgetStateSource(
            application = androidApplication(),
            cache = get(),
            database = get(),
            accountSettings = get(),
            capabilities = get(),
            timerControls = get(),
            workManager = get(),
            clock = get(),
            io = io(),
        )
    }
    single(TimerWidgetQualifier) { WidgetStore(androidContext()) }

    // Updates the widget whenever what it shows changes, whichever surface changed it, from
    // process start. It builds the widget state off the main thread.
    single(TimerWidgetQualifier) {
        val context = androidContext()
        WidgetSync(
            keys = flow { emitAll(get<WidgetStateSource>().state.map { it.key() }) },
            shown = get<WidgetStore>(TimerWidgetQualifier),
            update = { TokklWidget().updateAll(context) },
            scope = get(),
            io = io(),
        )
    }
    single(named("hook-timer-widget")) {
        val sync = inject<WidgetSync>(TimerWidgetQualifier)
        AppHook(AppHookOrder.TIMER_WIDGET, processStart = { sync.value.start() })
    }

    single {
        PomodoroWidgetStateSource(
            fields = get(),
            timerStates = get(),
            cache = get(),
            ringing = get(),
            capabilities = get(),
            accountSettings = get(),
            pomodoroDefaults = get(),
            signedIn = get<Session>()::isSignedIn,
            clock = get(),
            io = io(),
        )
    }

    // What the phase alarm redraws when it could not arm an exact alarm.
    factory<PomodoroWidgetRedraw> { PomodoroWidgetRedrawer(get(), get()) }
    single(PomodoroWidgetQualifier) { WidgetStore(androidContext(), keyName = WidgetStore.POMODORO_KEY) }

    // The Pomodoro widget's redraws. Its key is independent of the Timer widget.
    single(PomodoroWidgetQualifier) {
        val context = androidContext()
        WidgetSync(
            keys = flow { emitAll(get<PomodoroWidgetStateSource>().state.map { it.key() }) },
            shown = get<WidgetStore>(PomodoroWidgetQualifier),
            update = { PomodoroWidget().updateAll(context) },
            scope = get(),
            io = io(),
        )
    }
    single(named("hook-pomodoro-widget")) {
        val sync = inject<WidgetSync>(PomodoroWidgetQualifier)
        AppHook(AppHookOrder.POMODORO_WIDGET, processStart = { sync.value.start() })
    }
}
