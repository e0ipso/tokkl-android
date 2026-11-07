package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.clients.clientsFeatureModule
import com.github.e0ipso.tokkl.lifecycle.AppStartup
import com.github.e0ipso.tokkl.notification.notificationFeatureModule
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.pomodoro.pomodoroFeatureModule
import com.github.e0ipso.tokkl.projects.projectsFeatureModule
import com.github.e0ipso.tokkl.reports.reportsFeatureModule
import com.github.e0ipso.tokkl.settings.settingsFeatureModule
import com.github.e0ipso.tokkl.shortcuts.shortcutsFeatureModule
import com.github.e0ipso.tokkl.signin.signInFeatureModule
import com.github.e0ipso.tokkl.sync.syncFeatureModule
import com.github.e0ipso.tokkl.tags.tagsFeatureModule
import com.github.e0ipso.tokkl.tile.tileFeatureModule
import com.github.e0ipso.tokkl.timer.timerFeatureModule
import com.github.e0ipso.tokkl.trash.trashFeatureModule
import com.github.e0ipso.tokkl.widget.PomodoroWidgetQualifier
import com.github.e0ipso.tokkl.widget.TimerWidgetQualifier
import com.github.e0ipso.tokkl.widget.widgetFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** What [com.github.e0ipso.tokkl.TokklApplication] runs at process start. */
internal val startupModule = module {
    single {
        AppStartup(
            context = androidContext(),
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

/**
 * Every module of the app, for `startKoin`: the shell's, which wire the core modules and the
 * process start, and each feature's own.
 */
internal val appModules: List<Module> = listOf(
    coreModule,
    dataModule,
    accountModule,
    timerModule,
    workerModule,
    startupModule,
    signInFeatureModule,
    timerFeatureModule,
    pomodoroFeatureModule,
    reportsFeatureModule,
    projectsFeatureModule,
    clientsFeatureModule,
    tagsFeatureModule,
    trashFeatureModule,
    settingsFeatureModule,
    syncFeatureModule,
    notificationFeatureModule,
    shortcutsFeatureModule,
    tileFeatureModule,
    widgetFeatureModule,
)
