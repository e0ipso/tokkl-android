package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderDelays
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.reminder.ReminderWork
import com.github.e0ipso.tokkl.reminder.ReminderWorker
import com.github.e0ipso.tokkl.reminder.accountReminderSync
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** The running timer's notification and the reminder of a long-running timer, with its setting and its worker. */
val notificationFeatureModule = module {
    single { RunningNotificationStore(androidContext()) }

    // The running timer's notification (docs/PRD.md N1), which follows the timer controls, the
    // cache's running entry and the server's pause chain (T1), from process start, so every
    // start, pause, resume, stop and edit from any surface or device reaches it.
    single {
        val notifier = RunningNotifier(androidContext())
        RunningNotificationSync(
            running = runningNotificationContents(get<AccountTimerStates>().controls, get()),
            shown = get<RunningNotificationStore>(),
            ids = get<ChangeQueue>(),
            notice = notifier,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class
    single(named("hook-running-notification")) {
        val sync = inject<RunningNotificationSync>()
        AppHook(
            AppHookOrder.RUNNING_NOTIFICATION,
            // Reads the cache off the main thread, and posts or removes nothing for the entry the
            // notification already followed, so opening the app never brings back a dismissed one.
            processStart = { sync.value.start() },
            // A grant in notification settings makes the already-running timer visible too.
            notificationsAllowed = { sync.value.repost() },
        )
    }

    single { ReminderStore(androidContext()) }

    // Shared reminder settings where both keys exist, account-local on older servers.
    single { ReminderSetting(get<ReminderStore>(), get<Session>(), get(), io()) } bind AccountDataOwner::class

    // The running entry's reminder, followed independently of any screen, with its delay in WorkManager.
    single { ReminderWork(get(), get()) } bind ReminderDelays::class
    single {
        accountReminderSync(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    } bind AccountDataOwner::class
    single(named("hook-reminder")) {
        val reminder = inject<ReminderSync>()
        val settings = inject<ReminderSetting>()
        val cache = inject<AccountCache>()
        val scope = get<CoroutineScope>()
        val io = io()
        AppHook(
            AppHookOrder.REMINDER,
            processStart = {
                reminder.value.follow(
                    combine(cache.value.running, flow { emitAll(settings.value.state) }) { running, preferences ->
                        running to preferences
                    },
                    scope,
                    io,
                )
            },
        )
    }

    worker { params -> ReminderWorker(androidContext(), params.get(), get(), io()) }
    worker { params -> TimerActionWorker(androidContext(), params.get(), get(), io()) }
}
