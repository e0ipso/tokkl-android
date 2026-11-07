package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.ReminderWorker
import com.github.e0ipso.tokkl.reminder.accountReminderSync
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
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
            running = runningNotificationContents(get<TimerControlsSource>().controls, get()),
            shown = get<RunningNotificationStore>(),
            currentId = get<ChangeQueue>()::currentId,
            post = notifier::post,
            remove = notifier::remove,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class

    single { ReminderStore(androidContext()) }

    // Shared reminder settings where both keys exist, account-local on older servers.
    single { ReminderSetting(get<ReminderStore>(), get<Session>(), get(), io()) } bind AccountDataOwner::class

    // The running entry's reminder, followed independently of any screen.
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
        )
    } bind AccountDataOwner::class

    worker { params -> ReminderWorker(androidContext(), params.get(), get(), io()) }
}
