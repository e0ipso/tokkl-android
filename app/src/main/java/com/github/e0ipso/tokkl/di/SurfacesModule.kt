package com.github.e0ipso.tokkl.di

import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerControlsSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.notification.RunningNotificationStore
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.notification.RunningNotifier
import com.github.e0ipso.tokkl.notification.runningNotificationContent
import com.github.e0ipso.tokkl.reminder.ReminderInput
import com.github.e0ipso.tokkl.reminder.ReminderNotifier
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.reminder.ReminderWork
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.shortcuts.FavoriteShortcutSync
import com.github.e0ipso.tokkl.shortcuts.LauncherShortcuts
import com.github.e0ipso.tokkl.shortcuts.favoriteNames
import com.github.e0ipso.tokkl.shortcuts.favoriteShortcuts
import com.github.e0ipso.tokkl.tile.TileRefresh
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timer.TimerSurfaceAction
import com.github.e0ipso.tokkl.timer.TimerSurfaceActions
import com.github.e0ipso.tokkl.widget.PomodoroWidget
import com.github.e0ipso.tokkl.widget.PomodoroWidgetStateSource
import com.github.e0ipso.tokkl.widget.TokklWidget
import com.github.e0ipso.tokkl.widget.WidgetStateSource
import com.github.e0ipso.tokkl.widget.WidgetStore
import com.github.e0ipso.tokkl.widget.WidgetSync
import com.github.e0ipso.tokkl.widget.key
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
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
        val store = get<RunningNotificationStore>()
        val notifier = RunningNotifier(androidContext())
        val cache = get<AccountCache>()
        RunningNotificationSync(
            running = combine(get<TimerControlsSource>().controls, cache.projects, cache.tasks) { controls, projects, tasks ->
                runningNotificationContent(controls, projects, tasks)
            },
            loadShown = store::load,
            saveShown = store::save,
            currentId = get<ChangeQueue>()::currentId,
            post = notifier::post,
            remove = notifier::remove,
            scope = get(),
            io = io(),
        )
    }

    single { WidgetStateSource(androidApplication(), get(), get(), get(), get(), get(), clock = { get(WidgetClock) }, io = io()) }
    single(TimerWidgetQualifier) { WidgetStore(androidContext()) }

    /**
     * Updates the widget whenever what it shows changes, whichever surface changed it, from
     * process start. It builds the widget state off the main thread.
     */
    single(TimerWidgetQualifier) {
        val context = androidContext()
        val store = get<WidgetStore>(TimerWidgetQualifier)
        WidgetSync(
            state = { get<WidgetStateSource>().state },
            loadShown = store::load,
            saveShown = store::save,
            update = { TokklWidget().updateAll(context) },
            scope = get(),
            io = io(),
        )
    }

    single { PomodoroWidgetStateSource(get(), get(), get(), get(), get(), inject(), inject(), inject(), get(), io()) }
    single(PomodoroWidgetQualifier) { WidgetStore(androidContext(), keyName = WidgetStore.POMODORO_KEY) }

    /** The Pomodoro widget's redraws. Its key is independent of the Timer widget. */
    single(PomodoroWidgetQualifier) {
        val context = androidContext()
        val store = get<WidgetStore>(PomodoroWidgetQualifier)
        WidgetSync(
            keys = flow { emitAll(get<PomodoroWidgetStateSource>().state.map { it.key() }) },
            loadShown = store::load,
            saveShown = store::save,
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
        val context = androidContext()
        val launcher = LauncherShortcuts(context)
        val cache = get<AccountCache>()
        val capabilities = inject<CapabilitiesSource>()
        FavoriteShortcutSync(
            shortcuts = combine(flow { emitAll(capabilities.value.state) }, cache.favorites, cache.projects) { flags, favorites, projects ->
                favoriteShortcuts(flags.favorites, favorites, projects, launcher.limit, favoriteNames(context))
            },
            published = launcher::published,
            publish = launcher::publish,
            scope = get(),
            io = io(),
        )
    }

    single { TileRefresh(androidContext(), get(), inject(), get(), io()) }

    single { ReminderStore(androidContext()) }

    /** Shared reminder settings where both keys exist, account-local on older servers. */
    single {
        val store = get<ReminderStore>()
        ReminderSetting(store.load(), store::save, get<Session>()::client, get(), io())
    }

    /**
     * One reminder per running id/start, followed independently of any screen. Its Pause shows,
     * and goes to the server, only where the timer controls offer Pause for that entry.
     */
    single {
        val context = androidContext()
        val store = get<ReminderStore>()
        val notifier = ReminderNotifier(context)
        val session = get<Session>()
        val changes = get<ChangeQueue>()
        val cache = inject<AccountCache>()
        val settings = inject<ReminderSetting>()
        val controls = inject<TimerControlsSource>()
        val surfaceActions = inject<TimerSurfaceActions>()
        suspend fun runningNow(entryId: Long) =
            (controls.value.controls.first() as? TimerControls.Running)?.takeIf { it.entry.id == entryId }
        ReminderSync(
            latest = {
                ReminderInput(cache.value.running.first()?.entry?.takeIf { session.isSignedIn() }, settings.value.state.value)
            },
            currentId = changes::currentId,
            load = store::loadTimer,
            save = store::saveTimer,
            schedule = { ReminderWork.schedule(context, it) },
            cancel = { ReminderWork.cancel(context) },
            post = { timer -> notifier.post(timer, pausable = runningNow(changes.currentId(timer.id))?.pausable == true) },
            remove = notifier::remove,
            stop = { changes.stop(it) },
            pause = { entryId ->
                runningNow(entryId)?.let { surfaceActions.value.tap(TimerSurfaceAction.Pause, it.target) as? SurfaceTap.Request }
            },
        )
    }
}
