package com.github.e0ipso.tokkl.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerControlsSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.di.WidgetClock
import com.github.e0ipso.tokkl.navigation.NavigationStores
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationSync
import com.github.e0ipso.tokkl.pomodoro.ServicePomodoroRinging
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.settings.SignOutConfirmation
import com.github.e0ipso.tokkl.settings.ThemeSettingsRepository
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.timerstate.TimerSyncService
import com.github.e0ipso.tokkl.trust.SessionTrust
import com.github.e0ipso.tokkl.widget.PomodoroWidgetState
import com.github.e0ipso.tokkl.widget.PomodoroWidgetStateSource
import com.github.e0ipso.tokkl.widget.WidgetState
import com.github.e0ipso.tokkl.widget.WidgetStateSource
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.Qualifier

// The app's components as the tests read them, from the app's Koin graph. Each name matches the
// TokklApplication property it replaced, so the tests keep their call sites. They live in a package
// of their own because names such as `cache` and `reminder` are also packages of the app, and an
// import of com.github.e0ipso.tokkl.cache would name the package.

private inline fun <reified T : Any> component(qualifier: Qualifier? = null): T = GlobalContext.get().get(qualifier)

val TokklApplication.cache: AccountCache get() = component()
val TokklApplication.changes: ChangeQueue get() = component()
val TokklApplication.sender: ChangeSender get() = component()
val TokklApplication.accountSettings: AccountSettingsSource get() = component()
val TokklApplication.capabilities: CapabilitiesSource get() = component()
val TokklApplication.timerView: TimerViewSetting get() = component()
val TokklApplication.themeSettings: ThemeSettingsRepository get() = component()
val TokklApplication.pomodoroDefaults: PomodoroDefaultsSetting get() = component()
val TokklApplication.reminderSettings: ReminderSetting get() = component()
val TokklApplication.reminder: ReminderSync get() = component()
val TokklApplication.sessionTrust: SessionTrust get() = component()
val TokklApplication.lastAnswer: LastAnswer get() = component()
val TokklApplication.discarded: DiscardedChanges get() = component()
val TokklApplication.signOut: SignOut get() = component()
val TokklApplication.signOutConfirmation: SignOutConfirmation get() = component()
val TokklApplication.timerActions: TimerActions get() = component()
val TokklApplication.timerSync: TimerSync<TimerStateSource> get() = component()
val TokklApplication.pomodoroAlarms: PomodoroAlarmScheduler get() = component()
internal val TokklApplication.pomodoroAlarm: PomodoroPhaseAlarm get() = component()
internal val TokklApplication.pomodoroRinging: ServicePomodoroRinging get() = component()

/** The signed-in account's timer state source, taken anew on each read as the app does. */
val TokklApplication.timerState: TimerStateSource get() = component<AccountTimerStates>().source

val TokklApplication.timerControls: Flow<TimerControls> get() = component<TimerControlsSource>().controls
val TokklApplication.widgetState: Flow<WidgetState> get() = component<WidgetStateSource>().state
internal val TokklApplication.pomodoroWidgetState: Flow<PomodoroWidgetState>
    get() = component<PomodoroWidgetStateSource>().state
internal val TokklApplication.pomodoroSessionNotification: PomodoroSessionNotificationSync
    get() = component<AccountPomodoroNotification>().current

/** [TimerSyncService] while it runs in the foreground. */
val TokklApplication.timerSyncService: TimerSyncService? get() = component<TimerSyncFollower>().service

/**
 * The clock the widget reads on each event. Setting it replaces the app's [WidgetClock]
 * definition, which the widget state resolves again on its next event.
 */
var TokklApplication.widgetClock: Clock
    get() = component(WidgetClock)
    set(value) {
        GlobalContext.get().declare(value, WidgetClock, allowOverride = true)
    }

fun TokklApplication.renewTimerState() = component<AccountTimerStates>().renew()

fun TokklApplication.followTimerSync() = component<TimerSyncFollower>().follow()

fun TokklApplication.storeSignIn(server: ServerUrl, token: String, settings: AccountSettings) =
    component<SessionWriter>().save(server, token, settings)

/**
 * Puts an empty test WorkManager in this process, as [WorkManagerTestInitHelper.initializeTestWorkManager]
 * does, with the app's Koin worker factory so the workers get their dependencies.
 */
fun initializeTestWorkManager(context: Context) {
    val executor = SynchronousExecutor()
    val workers = DelegatingWorkerFactory().apply { addFactory(KoinWorkerFactory()) }
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder()
            .setExecutor(executor)
            .setTaskExecutor(executor)
            .setWorkerFactory(workers)
            .build(),
    )
}

/**
 * [owner]'s view models, made through Koin when the store lacks one, as the app makes them. A plain
 * [ViewModelProvider] cannot make them: they take their dependencies in their constructors.
 */
fun koinViewModels(owner: ViewModelStoreOwner): ViewModelProvider =
    ViewModelProvider(
        owner,
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                GlobalContext.get().get(modelClass.kotlin)
        },
    )

/** The navigation's view model stores in [activity], which TokklNavHost reads them from. */
internal fun navigationStores(activity: ComponentActivity): NavigationStores =
    ViewModelProvider(activity, viewModelFactory { initializer { NavigationStores() } })[NavigationStores::class.java]

/**
 * The view models of [activity]'s signed-in session, which the screens share: the timer, the entry
 * actions, the timer templates, the reports and the Pomodoro page.
 */
internal fun sessionViewModels(activity: ComponentActivity): ViewModelProvider = koinViewModels(navigationStores(activity).session)

/** The view models of [activity]'s Timer page: its history and its calendar. */
internal fun timerPageViewModels(activity: ComponentActivity): ViewModelProvider =
    koinViewModels(navigationStores(activity).entries.getOrCreateOwner(Route.Timer.contentKey))
