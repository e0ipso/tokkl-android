package com.github.e0ipso.tokkl.di

import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.notification.RunningNotificationStore
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.notification.RunningNotifier
import com.github.e0ipso.tokkl.notification.runningNotificationContents
import com.github.e0ipso.tokkl.timerstate.PomodoroWidgetRedraw
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.accountReminderSync
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.shortcuts.FavoriteShortcutSync
import com.github.e0ipso.tokkl.shortcuts.LauncherShortcuts
import com.github.e0ipso.tokkl.shortcuts.favoriteShortcutLists
import com.github.e0ipso.tokkl.tile.TileRefresh
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import com.github.e0ipso.tokkl.widget.PomodoroWidget
import com.github.e0ipso.tokkl.widget.PomodoroWidgetRedrawer
import com.github.e0ipso.tokkl.widget.PomodoroWidgetStateSource
import com.github.e0ipso.tokkl.widget.TokklWidget
import com.github.e0ipso.tokkl.widget.WidgetStateSource
import com.github.e0ipso.tokkl.widget.WidgetStore
import com.github.e0ipso.tokkl.widget.WidgetSync
import com.github.e0ipso.tokkl.widget.key
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** The Timer widget's store and sync. */
val TimerWidgetQualifier: Qualifier = named("TimerWidget")

/** The Pomodoro widget's store and sync. */
val PomodoroWidgetQualifier: Qualifier = named("PomodoroWidget")

/** The running notification, the widgets, the launcher shortcuts, the tile and the reminder. */
internal val surfacesModule = module {
    single { RunningNotificationStore(androidContext()) }

    /**
     * The running timer's notification (docs/PRD.md N1), which follows the timer controls, the
     * cache's running entry and the server's pause chain (T1), from process start, so every
     * start, pause, resume, stop and edit from any surface or device reaches it.
     */
    single {
        val notifier = RunningNotifier(androidContext())
        RunningNotificationSync(
            running = runningNotificationContents(get<TimerControlsSource>().controls, get()),
            shown = get<RunningNotificationStore>(),
            currentId = get<ChangeQueue>()::currentId,
            post = notifier::post,
            remove = notifier::remove,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class

    single { WidgetStateSource(androidApplication(), get(), get(), get(), get(), get(), clock = { get(WidgetClock) }, io = io()) }
    single(TimerWidgetQualifier) { WidgetStore(androidContext()) }

    /**
     * Updates the widget whenever what it shows changes, whichever surface changed it, from
     * process start. It builds the widget state off the main thread.
     */
    single(TimerWidgetQualifier) {
        val context = androidContext()
        WidgetSync(
            state = { get<WidgetStateSource>().state },
            shown = get<WidgetStore>(TimerWidgetQualifier),
            update = { TokklWidget().updateAll(context) },
            scope = get(),
            io = io(),
        )
    }

    single { PomodoroWidgetStateSource(get(), get(), get(), get(), get(), get(), get(), get(), get(), io()) }

    /** What the phase alarm redraws when it could not arm an exact alarm. */
    factory<PomodoroWidgetRedraw> { PomodoroWidgetRedrawer(get(), get()) }
    single(PomodoroWidgetQualifier) { WidgetStore(androidContext(), keyName = WidgetStore.POMODORO_KEY) }

    /** The Pomodoro widget's redraws. Its key is independent of the Timer widget. */
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

    /**
     * The favorites as launcher shortcuts (docs/PRD.md N6), which follow the cached favorites and
     * the capabilities from process start. The capabilities load on the first collection, off the
     * main thread.
     */
    single {
        val launcher = LauncherShortcuts(androidContext())
        FavoriteShortcutSync(
            shortcuts = favoriteShortcutLists(androidContext(), launcher, get(), get()),
            published = launcher::published,
            publish = launcher::publish,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class

    single { TileRefresh(androidContext(), get(), get(), get(), io()) }

    single { ReminderStore(androidContext()) }

    /** Shared reminder settings where both keys exist, account-local on older servers. */
    single { ReminderSetting(get<ReminderStore>(), get<Session>(), get(), io()) } bind AccountDataOwner::class

    /** The running entry's reminder, followed independently of any screen. */
    single { accountReminderSync(androidContext(), get(), get(), get(), get(), get(), get(), get()) } bind AccountDataOwner::class
}
