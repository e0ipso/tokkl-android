package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.MainDispatcher
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.reminder.AccountReminder
import com.github.e0ipso.tokkl.reminder.ReminderAccount
import com.github.e0ipso.tokkl.reminder.ReminderDelays
import com.github.e0ipso.tokkl.reminder.ReminderHook
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.ReminderWork
import com.github.e0ipso.tokkl.reminder.ReminderWorker
import com.github.e0ipso.tokkl.reminder.accountReminderSync
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.storage.StartupLoad
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

val notificationFeatureModule = module {
    single { RunningNotificationStore(androidContext(), io()) }

    single {
        val notifier = RunningNotifier(androidContext())
        RunningNotificationSync(
            running = runningNotificationContents(
                get<AccountTimerStates>().controls,
                get<AccountCache>().projects,
                get<AccountCache>().tasks,
            ),
            shown = get<RunningNotificationStore>(),
            currentId = get<ChangeQueue>()::currentId,
            notice = notifier,
            scope = get(),
            io = io(),
        )
    } bind AccountDataOwner::class
    single { RunningNotificationHook(inject()) } bind AppHook::class

    single { ReminderStore(androidContext(), io()) }

    // Shared reminder settings where both keys exist, account-local on older servers. They load at
    // process start.
    single { ReminderSetting(get<ReminderStore>(), get<Session>(), get(), get(), io()) } binds
        arrayOf(AccountDataOwner::class, StartupLoad::class)

    singleOf(::ReminderWork) bind ReminderDelays::class
    single {
        AccountReminder(
            session = get(),
            running = get<AccountCache>().running,
            settings = get(),
            changes = get(),
            controls = get(),
            surfaceActions = get(),
        )
    } bind ReminderAccount::class
    single { accountReminderSync(androidContext(), get(), get(), get(), get()) } bind AccountDataOwner::class
    single { ReminderHook(inject(), inject(), inject(), get(), io()) } bind AppHook::class

    worker { params -> ReminderWorker(androidContext(), params.get(), get(), io()) }
    single<TimerFailureNotice> { ToastTimerFailureNotice(androidContext(), get<CoroutineDispatcher>(MainDispatcher)) }
}
