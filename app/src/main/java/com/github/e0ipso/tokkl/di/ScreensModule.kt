package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.clients.ClientsEntries
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.pomodoro.PomodoroEntries
import com.github.e0ipso.tokkl.projects.ProjectsEntries
import com.github.e0ipso.tokkl.reports.ReportsEntries
import com.github.e0ipso.tokkl.settings.SettingsEntries
import com.github.e0ipso.tokkl.signin.SignInEntries
import com.github.e0ipso.tokkl.sync.SyncEntries
import com.github.e0ipso.tokkl.tags.TagsEntries
import com.github.e0ipso.tokkl.timer.TimerEntries
import com.github.e0ipso.tokkl.trash.TrashEntries
import org.koin.dsl.bind
import org.koin.dsl.module

/** Each feature's screens, which the navigation host adds to the entries of its back stack. */
internal val screensModule = module {
    single { SignInEntries } bind FeatureEntries::class
    single { TimerEntries } bind FeatureEntries::class
    single { PomodoroEntries } bind FeatureEntries::class
    single { ReportsEntries } bind FeatureEntries::class
    single { ProjectsEntries } bind FeatureEntries::class
    single { ClientsEntries } bind FeatureEntries::class
    single { TagsEntries } bind FeatureEntries::class
    single { TrashEntries } bind FeatureEntries::class
    single { SettingsEntries } bind FeatureEntries::class
    single { SyncEntries } bind FeatureEntries::class
}
