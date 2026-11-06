package com.github.e0ipso.tokkl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.di.IoDispatcher
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationTap
import com.github.e0ipso.tokkl.pomodoro.pomodoroSessionOpenIntent
import com.github.e0ipso.tokkl.pomodoro.takePomodoroNotificationTap
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reminder.ReminderNotifier
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ForegroundRefresh
import com.github.e0ipso.tokkl.session.NetworkWatch
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.SignOut
import com.github.e0ipso.tokkl.settings.SignOutModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.NeedsAttentionModel
import com.github.e0ipso.tokkl.sync.SyncStatusSource
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.trash.TrashViewModel
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.widget.WidgetPage
import com.github.e0ipso.tokkl.widget.takeWidgetPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel

class MainActivity : ComponentActivity() {

    // Read from the store on every use, never kept: a sign-out replaces them all.
    private val signInViewModel: SignInViewModel get() = viewModel()
    private val reportsViewModel: ReportsViewModel get() = viewModel()
    private val historyViewModel: HistoryViewModel get() = viewModel()
    private val calendarViewModel: CalendarViewModel get() = viewModel()
    private val projectsViewModel: ProjectsViewModel get() = viewModel()
    private val clientsViewModel: ClientsViewModel get() = viewModel()
    private val tagsViewModel: TagsViewModel get() = viewModel()
    private val trashViewModel: TrashViewModel get() = viewModel()
    private val timerViewModel: TimerViewModel get() = viewModel()
    private val entryFormViewModel: EntryFormViewModel get() = viewModel()

    private val io: CoroutineDispatcher by inject(IoDispatcher)
    private val cache: AccountCache by inject()
    private val themeSettings: ThemeSettingsModel by inject()
    private val reminderSettings: ReminderSetting by inject()
    private val signOut: SignOut by inject()
    private val signOutModel: SignOutModel by inject()
    private val sessionTrust: SessionTrustModel by inject()
    private val timerView: TimerViewSetting by inject()
    private val capabilities: CapabilitiesSource by inject()
    private val syncStatus: SyncStatusSource by inject()
    private val needsAttention: NeedsAttentionModel by inject()
    private val accountSettings: AccountSettingsSource by inject()
    private val discarded: DiscardedChanges by inject()
    private val foregroundRefresh: ForegroundRefresh by inject()
    private val timerSync: TimerSyncFollower by inject()
    private val runningNotification: RunningNotificationSync by inject()
    private val pomodoroNotification: AccountPomodoroNotification by inject()
    private val servers: ServerConfigStore by inject()

    /** Whether a time entry runs, from the phone's cache, which the bottom bar marks on Timer. */
    private val timerRunning: Flow<Boolean> by lazy {
        cache.running.map { it != null }.distinctUntilChanged()
    }

    private val notificationPermission by lazy { NotificationPermissionAsk.forApp(this) }
    private var notificationsAllowed = false
    private var openWidgetPage by mutableStateOf<WidgetPage?>(null)
    private var openReminderTimer by mutableIntStateOf(0)
    private var openPomodoro by mutableStateOf<PomodoroNotificationTap?>(null)

    /** The default network, watched while the activity is started. */
    private val network by lazy { NetworkWatch(this, ::networkChanged) }

    /** The sign-outs the view models and the screens on show came after. */
    private lateinit var shownSignOuts: MutableStateFlow<Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawEdgeToEdge()
        @Suppress("DEPRECATION")
        val pendingPomodoro = savedInstanceState?.getParcelable<Intent>("pending-pomodoro-notification")
        openPomodoro = pendingPomodoro?.takePomodoroNotificationTap() ?: intent?.takePomodoroNotificationTap()
        if (intent?.getBooleanExtra(ReminderNotifier.OPEN_TIMER, false) == true) {
            openReminderTimer++
            intent.removeExtra(ReminderNotifier.OPEN_TIMER)
        }
        openWidgetPage = savedInstanceState?.getString("pending-widget-page")?.let { name ->
            WidgetPage.entries.firstOrNull { it.name == name }
        } ?: intent?.takeWidgetPage()
        notificationsAllowed = allowsNotifications()
        // Reading the theme setting loads it, so the first frame already has the chosen theme's icons.
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        showSystemBarIconsFor(darkTheme = themeSettings.state.value.mode.isDark(systemIsDark))
        followSignOuts()
        refreshOnSignIn()
        setContent {
            val settings by themeSettings.state.collectAsState()
            val darkTheme = settings.mode.isDark(systemIsDark = isSystemInDarkTheme())
            val signOuts by shownSignOuts.collectAsState()
            TokklTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                WindowFollowsTheme(darkTheme)
                // A sign-out starts the navigation over, at sign-in, with new view models.
                key(signOuts) {
                    TokklNavHost(
                        signInModel = signInViewModel.model,
                        themeSettings = themeSettings,
                        reminderSettings = reminderSettings,
                        openReminderTimer = openReminderTimer,
                        openWidgetPage = openWidgetPage,
                        onWidgetPageOpened = { handled -> if (openWidgetPage == handled) openWidgetPage = null },
                        openPomodoro = openPomodoro,
                        onPomodoroOpened = { handled -> if (openPomodoro == handled) openPomodoro = null },
                        signOut = signOutModel,
                        sessionTrust = sessionTrust,
                        reports = reportsViewModel.model,
                        history = historyViewModel.model,
                        entryActions = historyViewModel.actions,
                        calendar = calendarViewModel.model,
                        projects = projectsViewModel.model,
                        clients = clientsViewModel.model,
                        tags = tagsViewModel.model,
                        timerView = timerView,
                        timer = timerViewModel.model,
                        entryForm = entryFormViewModel.model,
                        cache = cache,
                        trash = trashViewModel.model,
                        timerRunning = timerRunning,
                        capabilities = capabilities.state,
                        favorites = timerViewModel.favorites,
                        syncStatus = syncStatus.status,
                        needsAttention = needsAttention,
                        settings = accountSettings.state,
                        discardedChanges = discarded.state,
                        onDismissDiscarded = discarded::dismiss,
                        signedInAccount = ::signedInAccount,
                        notificationPermission = notificationPermission,
                        onNotificationsAllowed = ::notificationsGranted,
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        openPomodoro?.let { pending ->
            outState.putParcelable("pending-pomodoro-notification", pomodoroSessionOpenIntent(this, pending.account, pending.phase))
        }
        openWidgetPage?.let { outState.putString("pending-widget-page", it.name) }
        super.onSaveInstanceState(outState)
    }

    /**
     * Takes a tap's route from [intent] and leaves the activity's own intent as it was started.
     * Nothing reads the intent after onCreate, and a pending route outlives rotation in the saved
     * state.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.takeWidgetPage()?.let { openWidgetPage = it }
        intent.takePomodoroNotificationTap()?.let { openPomodoro = it }
        if (intent.getBooleanExtra(ReminderNotifier.OPEN_TIMER, false)) {
            openReminderTimer++
            intent.removeExtra(ReminderNotifier.OPEN_TIMER)
        }
    }

    /**
     * Drops every view model after a sign-out, and with them what each screen loaded, such as the
     * history's older days and the reports. The view models outlive a configuration change, so
     * [ViewModelsAfterSignOuts] records which sign-out they came after, and a sign-out that
     * finished while the activity was being made again counts too.
     */
    private fun followSignOuts() {
        val signOuts = signOut.finished
        dropViewModelsBefore(signOuts.value)
        shownSignOuts = MutableStateFlow(signOuts.value)
        lifecycleScope.launch {
            signOuts.collect { finished ->
                dropViewModelsBefore(finished)
                if (shownSignOuts.value != finished) {
                    openPomodoro = null
                    openWidgetPage = null
                }
                shownSignOuts.value = finished
            }
        }
    }

    private fun dropViewModelsBefore(signOuts: Int) {
        if (viewModel<ViewModelsAfterSignOuts>().signOuts != signOuts) viewModelStore.clear()
    }

    private inline fun <reified T : ViewModel> viewModel(): T = getViewModel()

    /**
     * Coming to the foreground reads the account settings again (docs/PRD.md S7) and refreshes the
     * offline cache with the same `GET /api/v9/me` (O2), but a rotation's start does not. After a
     * trip to the background, the calendar loads its weeks from the server again. From here to
     * [onStop] the network is watched, so its return refreshes too. Nothing refreshes on a timer.
     * Every start, a rotation's too, starts the timer sync service when it should run.
     */
    override fun onStart() {
        super.onStart()
        // A grant in notification settings makes the already-running timer visible too.
        val allowed = allowsNotifications()
        if (allowed && !notificationsAllowed) notificationsGranted()
        notificationsAllowed = allowed
        foregroundRefresh.started(online = network.start())
        calendarViewModel.model.cameToForeground()
        // The app is on screen, so the platform lets the timer sync service start, also offline.
        lifecycleScope.launch(io) { timerSync.follow() }
    }

    /**
     * Going to the background, which a configuration change is not. The calendar's weeks live in
     * its view model whichever page shows, so the activity tells it rather than the calendar.
     */
    override fun onStop() {
        super.onStop()
        network.stop()
        foregroundRefresh.stopped(isChangingConfigurations)
        if (!isChangingConfigurations) calendarViewModel.model.wentToBackground()
    }

    /**
     * The network came back while the app is on screen: the account refreshes once, and the
     * history's older days, the calendar and the reports load again what they could not load
     * offline. The projects, clients and tags pages watch the network themselves.
     */
    private fun networkChanged(online: Boolean) {
        // A kept connection went with the network. A request on it would fail only after it was
        // written, so the queue could not tell whether a delete reached the server, and Undo
        // would need the server. Without one, the request fails before it leaves the phone.
        if (!online) SharedHttpClient.instance.connectionPool.evictAll()
        val returned = foregroundRefresh.networkChanged(online) {
            // A cellular route can appear before it works, followed by Wi-Fi without an
            // offline interval. Retry failed history days and calendar weeks on each online
            // update, while keeping the account refresh and other foreground work debounced.
            historyViewModel.model.reconnected()
            calendarViewModel.model.reconnected()
        }
        if (!returned) return
        reportsViewModel.model.reconnected()
    }

    private fun allowsNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationsGranted() {
        notificationsAllowed = true
        lifecycleScope.launch(io) {
            runningNotification.repost()
            pomodoroNotification.current.refresh(force = true)
        }
    }

    /** The account signed in now, for signing in again after the server refused its token. Reads the disk. */
    private fun signedInAccount(): SignInAgain? =
        servers.load()?.let { server -> SignInAgain(server, accountSettings.state.value?.userId) }

    /** A sign-in loads the account settings and the cache, as coming to the foreground does. */
    private fun refreshOnSignIn() {
        lifecycleScope.launch {
            // Each sign-out brings a new sign-in view model.
            shownSignOuts.collectLatest {
                signInViewModel.model.state
                    .map { it.signedIn }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { accountSettings.refresh() }
            }
        }
    }

    /**
     * Paints the window in the scheme's background and picks system bar icons that show on it.
     * The window theme follows the light or dark setting through the app's night mode, but never
     * knows the wallpaper's colours, so this sets the background and the icons from the chosen
     * theme. Effects run before the first frame draws.
     */
    @Composable
    private fun WindowFollowsTheme(darkTheme: Boolean) {
        val background = MaterialTheme.colorScheme.background.toArgb()
        DisposableEffect(darkTheme, background) {
            window.setBackgroundDrawable(background.toDrawable())
            showSystemBarIconsFor(darkTheme)
            onDispose {}
        }
    }

    /**
     * Draws behind transparent system bars, with the system's navigation bar contrast enforcement
     * off, so the screen's own background shows under both bars.
     *
     * With enforcement on, which androidx.activity's `enableEdgeToEdge` turns on for
     * `SystemBarStyle.auto`, Android paints a translucent scrim over a three-button
     * navigation bar: a near-white band on the light background, a lighter one on the dark.
     * `enableEdgeToEdge` also reapplies its first call's styles after every configuration change,
     * whatever later calls set. `WindowCompat.enableEdgeToEdge` sets the window flags once, with
     * enforcement off, and leaves the icons to [showSystemBarIconsFor].
     */
    private fun drawEdgeToEdge() {
        WindowCompat.enableEdgeToEdge(window)
    }

    /** Dark status and navigation bar icons on a light theme, light icons on a dark one. */
    private fun showSystemBarIconsFor(darkTheme: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/**
 * Made along with MainActivity's other view models, so it tells which sign-out they came after:
 * [SignOut.finished][com.github.e0ipso.tokkl.session.SignOut.finished] as it stood then.
 */
class ViewModelsAfterSignOuts(signOut: SignOut) : ViewModel() {
    val signOuts: Int = signOut.finished.value
}
