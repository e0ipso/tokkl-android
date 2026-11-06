package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.ViewModelsAfterSignOuts
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmViewModel
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.trash.TrashViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The screens' view models. MainActivity keeps them in its store until a sign-out. */
internal val viewModelModule = module {
    viewModel { SignInViewModel(get(), get(), get(), get(), get(), io()) }
    viewModel { ReportsViewModel(androidContext(), get(), get(), get(), get(), io()) }
    viewModel { HistoryViewModel(get(), get(), get(), get(), get(), get(), io()) }
    viewModel { CalendarViewModel(get(), get(), get(), get(), inject(), io()) }
    viewModel { ProjectsViewModel(get(), get(), get(), io()) }
    viewModel { ClientsViewModel(get(), get(), get(), io()) }
    viewModel { TagsViewModel(get(), get(), get(), io()) }
    viewModel { TrashViewModel(get(), get(), get(), io()) }
    viewModel { TimerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), io()) }
    viewModelOf(::EntryFormViewModel)
    viewModelOf(::PomodoroViewModel)
    viewModelOf(::PomodoroAlarmViewModel)
    viewModelOf(::ViewModelsAfterSignOuts)
}
