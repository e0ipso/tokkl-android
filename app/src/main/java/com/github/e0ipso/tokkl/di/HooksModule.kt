package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.RunningCheckHook
import com.github.e0ipso.tokkl.lifecycle.AppHooks
import com.github.e0ipso.tokkl.notification.RunningNotificationHook
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmHook
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationHook
import com.github.e0ipso.tokkl.reminder.ReminderHook
import com.github.e0ipso.tokkl.session.RefreshRequestsHook
import com.github.e0ipso.tokkl.shortcuts.FavoriteShortcutsHook
import com.github.e0ipso.tokkl.timersync.TimerSyncHook
import com.github.e0ipso.tokkl.widget.PomodoroWidgetHook
import com.github.e0ipso.tokkl.widget.TimerWidgetHook
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Every module's [AppHook] in the order they run at process start, on MainActivity's starts and
 * after a notification grant. The surfaces that show the running timer follow the cache before the
 * running check and the timer sync can write to it, so none misses a first change. Each module
 * binds its hooks as [AppHook]; [AppHooks] refuses one this list leaves out.
 */
private fun Scope.hooksInOrder(): List<AppHook> = listOf(
    // Forwards the refreshes the other hooks ask for, before any of them asks.
    get<RefreshRequestsHook>(),
    // The running timer's notification, which later hooks may change.
    get<RunningNotificationHook>(),
    // The Pomodoro phase alarm, armed from the held state before the notification shows the phase.
    get<PomodoroAlarmHook>(),
    get<PomodoroNotificationHook>(),
    // The running entry's reminder, after the notifications and before the widgets.
    get<ReminderHook>(),
    get<TimerWidgetHook>(),
    // After the Timer widget's redraws.
    get<PomodoroWidgetHook>(),
    // The favorites as launcher shortcuts, after the widgets.
    get<FavoriteShortcutsHook>(),
    // The running entry check, once every surface follows the cache.
    get<RunningCheckHook>(),
    // Starts or stops the timer sync on MainActivity's starts, last.
    get<TimerSyncHook>(),
)

/** The hooks in the order they run, which the process start and MainActivity's lifecycle share. */
internal val hooksModule = module {
    single { AppHooks(inOrder = hooksInOrder(), bound = getAll()) }
}
