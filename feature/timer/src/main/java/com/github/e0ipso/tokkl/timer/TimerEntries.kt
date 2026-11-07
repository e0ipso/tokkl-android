package com.github.e0ipso.tokkl.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.calendar.WeekCalendar
import com.github.e0ipso.tokkl.entry.EntryFormScreen
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.favorites.TemplatesScreen
import com.github.e0ipso.tokkl.history.EntryActionsHost
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryList
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.navigation.routes
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Timer page, the fullscreen timer, the entry form and the timer templates. Each screen takes
 * its view models: its own, which live in its back stack entry and go when it closes, or the
 * signed-in session's ([sessionViewModel]) when other screens share them or their state outlives
 * the page.
 */
internal object TimerEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Timer) { TimerPage(host.navigator) }
        route(Route.FullscreenTimer) { FullscreenTimer(host.navigator) }
        routes<Route.Entry> { route -> EntryForm(route, host.navigator) }
        route(Route.Templates) {
            val account by koinInject<AccountSettingsSource>().state.collectAsState()
            TemplatesScreen(
                model = sessionViewModel<FavoritesViewModel>(),
                cache = koinInject(),
                workspaceId = account?.defaultWorkspaceId,
            )
        }
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
                        if (top == Route.Timer || top is Route.Entry) {
                            navigator.openEntry(
                                EntryOpening.Running.of(identity),
                            )
                        }
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
