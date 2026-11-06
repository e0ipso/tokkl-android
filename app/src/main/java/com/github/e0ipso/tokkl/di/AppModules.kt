package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.AppStartup
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** What [com.github.e0ipso.tokkl.TokklApplication] runs at process start. */
internal val startupModule = module {
    single {
        AppStartup(
            context = androidContext(),
            servers = get(),
            trust = get(),
            refreshRequests = get(),
            accountRefresh = inject(),
            runningNotification = inject(),
            held = inject(),
            capabilities = inject(),
            pomodoroAlarm = inject(),
            pomodoroNotification = inject(),
            reminder = inject(),
            reminderSettings = inject(),
            cache = inject(),
            widgetSync = inject(TimerWidgetQualifier),
            pomodoroWidgetSync = inject(PomodoroWidgetQualifier),
            favoriteShortcuts = inject(),
            scope = get(),
            io = io(),
        )
    }
}

/** Every module of the app, for `startKoin`. */
internal val appModules: List<Module> = listOf(
    coreModule,
    dataModule,
    accountModule,
    timerModule,
    pomodoroModule,
    surfacesModule,
    workerModule,
    viewModelModule,
    startupModule,
)
