package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation3.runtime.NavEntry
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.calendar.WeekCalendar
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.clients.ClientsScreen
import com.github.e0ipso.tokkl.entry.EntryFormScreen
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.favorites.TemplatesScreen
import com.github.e0ipso.tokkl.history.EntryActionsHost
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryList
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.pomodoro.PomodoroScreen
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.projects.ProjectsScreen
import com.github.e0ipso.tokkl.reports.ReportsScreen
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.settings.SettingsScreen
import com.github.e0ipso.tokkl.signin.SignInScreen
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.sync.NeedsAttentionScreen
import com.github.e0ipso.tokkl.tags.TagsScreen
import com.github.e0ipso.tokkl.timer.FullscreenTimerScreen
import com.github.e0ipso.tokkl.timer.TimerBar
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.settleDescription
import com.github.e0ipso.tokkl.timer.target
import com.github.e0ipso.tokkl.trash.TrashScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * What the Pomodoro page and the frame's notification permission prompt share: the page's
 * starts and retries, which may ask for the permission, and a count of the prompt's answers,
 * which tells the page to read the permission again.
 */
internal class NotificationPrompts {
    val pomodoroStarts = Channel<Unit>(Channel.UNLIMITED)
    val retries = Channel<Unit>(Channel.UNLIMITED)
    var answers by mutableIntStateOf(0)
}

/**
 * The back stack entry for [route], whose content is the route's screen. The entries live as
 * long as the stack does not change, so the content takes only objects that stay the same for
 * the host's life, and reads every state itself.
 */
internal fun routeEntry(
    route: Route,
    navigator: Navigator,
    prompts: NotificationPrompts,
    capabilities: StateFlow<Capabilities>,
): NavEntry<Route> = NavEntry(route, contentKey = route.contentKey) { RouteScreen(it, navigator, prompts, capabilities) }

/**
 * The screen [route] shows. Each screen takes its view models: its own, which live in its back
 * stack entry and go when it closes, or the signed-in session's ([sessionViewModel]) when other
 * screens share them or their state outlives the page.
 */
@Composable
private fun RouteScreen(
    route: Route,
    navigator: Navigator,
    prompts: NotificationPrompts,
    capabilities: StateFlow<Capabilities>,
) {
    when (route) {
        is Route.SignIn -> {
            val model = koinViewModel<SignInViewModel>(parameters = { parametersOf(route.again) })
            SignInScreen(
                model = model,
                onSignedIn = {
                    model.reset()
                    navigator.open(TopLevelPage.TIMER)
                },
            )
        }
        Route.Timer -> TimerPage(navigator)
        Route.FullscreenTimer -> FullscreenTimer(navigator)
        Route.Pomodoro -> PomodoroScreen(
            model = sessionViewModel<PomodoroViewModel>(),
            cache = koinInject(),
            onNotificationStart = { prompts.pomodoroStarts.trySend(Unit) },
            onNotificationRetry = { prompts.retries.trySend(Unit) },
            notificationPermissionRevision = prompts.answers,
        )
        Route.Reports -> ReportsScreen(model = sessionViewModel())
        Route.Projects -> ProjectsScreen(model = koinViewModel())
        Route.Clients -> ClientsScreen(model = koinViewModel())
        Route.Tags -> TagsScreen(model = koinViewModel())
        Route.Trash -> TrashScreen(model = koinViewModel())
        Route.Templates -> {
            val account by koinInject<AccountSettingsSource>().state.collectAsState()
            TemplatesScreen(model = sessionViewModel<FavoritesViewModel>(), cache = koinInject(), workspaceId = account?.defaultWorkspaceId)
        }
        Route.Settings -> {
            val flags by capabilities.collectAsState()
            SettingsScreen(
                model = koinInject(),
                reminder = koinInject(),
                pomodoro = koinInject(),
                pomodoroSharedWithWeb = flags.favorites,
                signOut = koinInject(),
                onOpenLicences = { navigator.push(Route.Licences) },
            )
        }
        Route.Licences -> LicencesScreen(onBack = { navigator.close(Route.Licences) })
        is Route.Entry -> EntryForm(route, navigator)
        Route.NeedsAttention -> NeedsAttentionScreen(
            model = koinInject(),
            settings = koinInject<AccountSettingsSource>().state,
            onEdit = { entryId -> navigator.openEntry(EntryOpening.Edit(entryId)) },
            onBack = { navigator.close(Route.NeedsAttention) },
        )
    }
}

/**
 * The Timer page. Its history and calendar live in its entry, which stays at the bottom of every
 * signed-in back stack, and apply the entry actions' deletes and restores. A tap on an entry in
 * the history, or on the button for a past entry, opens the entry form above the page, and an
 * entry's Continue starts the timer with its fields.
 */
@Composable
private fun TimerPage(navigator: Navigator) {
    val timer = sessionViewModel<TimerViewModel>()
    val favorites = sessionViewModel<FavoritesViewModel>()
    val actions = sessionViewModel<EntryActionsViewModel>()
    val history = koinViewModel<HistoryViewModel>(parameters = { parametersOf(actions.reconciliation) })
    val calendar = koinViewModel<CalendarViewModel>(parameters = { parametersOf(actions.reconciliation) })
    val timerView = koinInject<TimerViewSetting>()
    val view by timerView.view.collectAsState()
    val cache = koinInject<AccountCache>()
    val settings = koinInject<AccountSettingsSource>().state
    val timerState by timer.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    TimerScreen(
        view = view,
        onViewChange = timerView::choose,
        timer = {
            TimerBar(
                timer,
                cache,
                favorites = favorites,
                onManageTemplates = { navigator.open(TopLevelPage.TEMPLATES) },
                onOpenFullscreen = {
                    if (navigator.top == Route.Timer && timerState.target != null) navigator.push(Route.FullscreenTimer)
                },
                onAddEntry = { navigator.openEntry(EntryOpening.New()) },
                onEditRunning = {
                    scope.launch {
                        val identity = timer.runningForEdit() ?: return@launch
                        val top = navigator.top
                        if (top == Route.Timer || top is Route.Entry) navigator.openEntry(EntryOpening.Running.of(identity))
                    }
                },
            )
        },
        listContent = {
            HistoryList(
                history,
                actions,
                onEdit = { entryId -> navigator.openEntry(EntryOpening.Edit(entryId), older = history.entry(entryId)) },
                onContinue = { entryId ->
                    history.entry(entryId)?.let { source ->
                        timer.continueEntry(source)
                        settleDescription(focusManager)
                    }
                },
                favorites = favorites,
                showActions = false,
            )
        },
        feedback = {
            val actionsState by actions.state.collectAsState()
            val account by settings.collectAsState()
            EntryActionsHost(
                state = actionsState,
                onUndo = actions::undo,
                onNoticeDone = actions::noticeDone,
                onConfirmDelete = actions::confirmDelete,
                onCancelDelete = actions::cancelDelete,
                settings = account,
                onSplitTextChange = actions::setSplitText,
                onConfirmSplit = actions::confirmSplit,
                onCancelSplit = actions::cancelSplit,
            )
        },
        calendarContent = {
            WeekCalendar(
                model = calendar,
                onEdit = { entryId, older -> navigator.openEntry(EntryOpening.Edit(entryId), older) },
                onAdd = { day -> navigator.openEntry(EntryOpening.New.on(day)) },
                onAddAllDay = { day -> navigator.openEntry(EntryOpening.New.on(day, noTimes = true)) },
            )
        },
    )
}

/** The running timer on its own. Its edit button opens the entry form on the segment it shows. */
@Composable
private fun FullscreenTimer(navigator: Navigator) {
    val timer = sessionViewModel<TimerViewModel>()
    val scope = rememberCoroutineScope()
    FullscreenTimerScreen(
        model = timer,
        onExit = { navigator.close(Route.FullscreenTimer) },
        onEditRunning = { target ->
            scope.launch {
                if (navigator.top != Route.FullscreenTimer) return@launch
                val identity = timer.runningForEditShown(target) ?: return@launch
                if (navigator.top == Route.FullscreenTimer) navigator.openEntry(EntryOpening.Running.of(identity))
            }
        },
    )
}

/**
 * The entry form [route] opens. Its view model is its own, opened on what the route says when
 * this process opened it ([EntryFormOpenings]). A delete goes through the session's entry
 * actions, so the Timer page offers its Undo.
 */
@Composable
private fun EntryForm(route: Route.Entry, navigator: Navigator) {
    val openings = navigator.openings
    val model = koinViewModel<EntryFormViewModel>(parameters = { parametersOf(openings.take(route)) })
    val actions = sessionViewModel<EntryActionsViewModel>()
    EntryFormScreen(
        model = model,
        cache = koinInject(),
        canRestore = actions::canRestoreDelete,
        onDeleted = actions::deleted,
        onClose = { navigator.close(route) },
    )
}
