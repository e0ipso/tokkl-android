package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.cacheModule
import com.github.e0ipso.tokkl.clients.clientsFeatureModule
import com.github.e0ipso.tokkl.lifecycle.AppStartup
import com.github.e0ipso.tokkl.notification.notificationFeatureModule
import com.github.e0ipso.tokkl.platform.platformModule
import com.github.e0ipso.tokkl.pomodoro.pomodoroFeatureModule
import com.github.e0ipso.tokkl.projects.projectsFeatureModule
import com.github.e0ipso.tokkl.reports.reportsFeatureModule
import com.github.e0ipso.tokkl.session.sessionModule
import com.github.e0ipso.tokkl.settings.settingsFeatureModule
import com.github.e0ipso.tokkl.shortcuts.shortcutsFeatureModule
import com.github.e0ipso.tokkl.signin.signInFeatureModule
import com.github.e0ipso.tokkl.storage.StartupLoads
import com.github.e0ipso.tokkl.sync.syncFeatureModule
import com.github.e0ipso.tokkl.tags.tagsFeatureModule
import com.github.e0ipso.tokkl.tile.tileFeatureModule
import com.github.e0ipso.tokkl.timer.timerFeatureModule
import com.github.e0ipso.tokkl.timernotice.timerNoticeModule
import com.github.e0ipso.tokkl.timerstate.timerStateModule
import com.github.e0ipso.tokkl.timersync.timerSyncModule
import com.github.e0ipso.tokkl.trash.trashFeatureModule
import com.github.e0ipso.tokkl.trust.trustModule
import com.github.e0ipso.tokkl.widget.widgetFeatureModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What [com.github.e0ipso.tokkl.TokklApplication] makes and runs at process start: every store
 * that loads then, which MainActivity's first frame waits for, and the hooks.
 */
internal val startupModule = module {
    single { StartupLoads(getAll()) }
    single { AppStartup(hooks = get()) }
}

/**
 * Every module of the app, for `startKoin`: each core module's and each feature's own, and the
 * shell's, which run the process start, join what spans the modules and host the navigation.
 */
internal val appModules: List<Module> = listOf(
    platformModule,
    sessionModule,
    trustModule,
    cacheModule,
    timerStateModule,
    timerNoticeModule,
    timerSyncModule,
    lifecycleModule,
    hooksModule,
    startupModule,
    navigationModule,
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
