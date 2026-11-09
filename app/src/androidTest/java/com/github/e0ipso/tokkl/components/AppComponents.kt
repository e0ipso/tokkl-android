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
import com.github.e0ipso.tokkl.TestClock
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.BackgroundSendRuns
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.DeleteUndoer
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.navhost.NavigationStores
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.platform.DefaultNetwork
import com.github.e0ipso.tokkl.pomodoro.PomodoroAlarmRearm
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationSync
import com.github.e0ipso.tokkl.pomodoro.ServicePomodoroRinging
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.settings.SignOutConfirmation
import com.github.e0ipso.tokkl.signin.SessionWriter
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.TimerActions
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerStateIntake
import com.github.e0ipso.tokkl.timersync.PomodoroAlarmScheduler
import com.github.e0ipso.tokkl.timersync.TimerSync
import com.github.e0ipso.tokkl.timersync.TimerSyncFollower
import com.github.e0ipso.tokkl.trust.SessionTrust
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsRepository
import com.github.e0ipso.tokkl.widget.PomodoroWidgetState
import com.github.e0ipso.tokkl.widget.PomodoroWidgetStateSource
import com.github.e0ipso.tokkl.widget.WidgetState
import com.github.e0ipso.tokkl.widget.WidgetStateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.Qualifier
import java.time.Clock

// The app's components as the tests read them, from the app's Koin graph. Each name matches the
// TokklApplication property it replaced, so the tests keep their call sites. They live in a package
// of their own because names such as `cache` and `reminder` are also packages of the app, and an
// import of com.github.e0ipso.tokkl.cache would name the package.

private inline fun <reified T : Any> component(qualifier: Qualifier? = null): T = GlobalContext.get().get(qualifier)

val TokklApplication.cache: AccountCache get() = component()
val TokklApplication.cacheDatabase: CacheDatabase get() = component()
val TokklApplication.changes: ChangeQueue get() = component()
val TokklApplication.sender: ChangeSender get() = component()
val TokklApplication.deleteUndo: DeleteUndoer get() = component()
val TokklApplication.accountSettings: AccountSettingsSource get() = component()
val TokklApplication.accountRefresh: AccountRefresh get() = component()
val TokklApplication.capabilities: CapabilitiesSource get() = component()
val TokklApplication.timerView: TimerViewSetting get() = component()
val TokklApplication.themeSettings: ThemeSettingsRepository get() = component()
val TokklApplication.pomodoroDefaults: PomodoroDefaultsSetting get() = component()
val TokklApplication.reminderSettings: ReminderSetting get() = component()
val TokklApplication.reminder: ReminderSync get() = component()
val TokklApplication.runningCheck: RunningCheck get() = component()
val TokklApplication.backgroundSendRuns: BackgroundSendRuns get() = component()
val TokklApplication.sessionTrust: SessionTrust get() = component()
val TokklApplication.lastAnswer: LastAnswer get() = component()
val TokklApplication.discarded: DiscardedChanges get() = component()
val TokklApplication.signOut: SignOut get() = component()
val TokklApplication.signOutConfirmation: SignOutConfirmation get() = component()
val TokklApplication.timerActions: TimerActions get() = component()
val TokklApplication.defaultNetwork: DefaultNetwork get() = component()
val TokklApplication.timerSync: TimerSync<TimerStateIntake> get() = component()
val TokklApplication.pomodoroAlarms: PomodoroAlarmScheduler get() = component()
internal val TokklApplication.pomodoroAlarm: PomodoroPhaseAlarm get() = component()
internal val TokklApplication.pomodoroAlarmRearm: PomodoroAlarmRearm get() = component()
internal val TokklApplication.pomodoroRinging: ServicePomodoroRinging get() = component()

val TokklApplication.timerStates: AccountTimerStates get() = component()

/** The signed-in account's held timer state now, read anew on each read as the app does. */
val TokklApplication.heldTimerState: HeldTimerState? get() = timerStates.heldNow()

/** Where the signed-in account's timer state takes answers, taken anew on each read as the app does. */
val TokklApplication.timerStateIntake: TimerStateIntake get() = timerStates.intake()

val TokklApplication.timerControls: Flow<TimerControls> get() = component<AccountTimerStates>().controls
val TokklApplication.widgetState: Flow<WidgetState> get() = component<WidgetStateSource>().state
internal val TokklApplication.pomodoroWidgetState: Flow<PomodoroWidgetState>
    get() = component<PomodoroWidgetStateSource>().state
internal val TokklApplication.pomodoroSessionNotification: PomodoroSessionNotificationSync get() = component()

/** Whether [com.github.e0ipso.tokkl.timersync.TimerSyncService] runs in the foreground. */
val TokklApplication.timerSyncServiceRuns: Boolean get() = component<TimerSyncFollower>().runningService.value != null

/** The app's clock, which [com.github.e0ipso.tokkl.TestTokklApplication] makes a [TestClock]. */
val TokklApplication.testClock: TestClock get() = component<Clock>() as TestClock

/** Clears the timer state and begins a new account, as a sign-in does, so the next read makes a new source. */
fun TokklApplication.renewTimerState() {
    component<AccountTimerStates>().clearForSignIn()
    component<CurrentAccount>().begin()
}

fun TokklApplication.followTimerSync() = runBlocking { component<TimerSyncFollower>().follow() }

fun TokklApplication.storeSignIn(server: ServerUrl, token: String, settings: AccountSettings) =
    runBlocking { component<SessionWriter>().save(server, token, settings) }

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
fun koinViewModels(owner: ViewModelStoreOwner): ViewModelProvider = ViewModelProvider(
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
 * actions, the timer templates, the reports' tab and filters and the Pomodoro page.
 */
internal fun sessionViewModels(activity: ComponentActivity): ViewModelProvider = koinViewModels(
    navigationStores(activity).session,
)

/** The view models of [activity]'s Timer page: its history and its calendar. */
internal fun timerPageViewModels(activity: ComponentActivity): ViewModelProvider =
    koinViewModels(navigationStores(activity).entries.getOrCreateOwner(Route.Timer.contentKey))

/** The view model of [activity]'s Reports page, which must be on the back stack. */
internal fun reportsViewModel(activity: ComponentActivity): ReportsViewModel =
    koinViewModels(navigationStores(activity).entries.getOrCreateOwner(ReportsRoute.contentKey))[
        ReportsViewModel::class.java,
    ]
