package com.github.e0ipso.tokkl.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.calendar.WeekCalendar
import com.github.e0ipso.tokkl.entries.SessionEntries
import com.github.e0ipso.tokkl.entry.EntryFormScreen
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.favorites.TemplatesScreen
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.history.EntryActionsHost
import com.github.e0ipso.tokkl.history.EntryActionsViewModel
import com.github.e0ipso.tokkl.history.HistoryList
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.history.pinAction
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.LinkIntent
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.NavigationRequest
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.navigation.RequestOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.navigation.routes
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The Timer page, the fullscreen timer, the entry form and the timer templates. Each screen takes
 * its view models: its own, which live in its back stack entry and go when it closes, or the
 * signed-in session's ([LocalSignedInSession]) when other screens share them or their state outlives
 * the page. The screens get plain state from them and send their events back.
 */
internal object TimerEntries : FeatureRoutes {
    override val pages get() = listOf(timerPage, templatesPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(FullscreenTimerRoute::class)
        subclass(TemplatesRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        route(Route.Timer) { TimerPage(host.navigator) }
        route(FullscreenTimerRoute) { FullscreenTimer(host.navigator) }
        routes<Route.Entry> { route -> EntryForm(route, host.navigator) }
        route(TemplatesRoute) {
            val favorites = koinViewModel<FavoritesViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
            TemplatesScreen(model = favorites)
        }
    }
}

/** The taps outside the app that open Timer: a Timer widget's and a reminder's. */
internal object TimerRequests : FeatureRequests {
    override val linkExtras get() = listOf(TIMER_WIDGET_TAP, REMINDER_TAP)

    override fun readLinks(intent: LinkIntent): List<NavigationRequest> = listOfNotNull(
        ReminderTap.takeIf { intent.flag(REMINDER_TAP) },
        TimerWidgetTap.takeIf { intent.flag(TIMER_WIDGET_TAP) },
    )

    override fun PolymorphicModuleBuilder<NavigationRequest>.registerRequests() {
        subclass(TimerWidgetTap::class)
        subclass(ReminderTap::class)
    }

    /**
     * A Timer widget's tap opens Timer when somebody is signed in, and sign-in otherwise. A
     * reminder's tap opens Timer, but waits while sign-in shows.
     */
    @Composable
    override fun OpenRequest(request: NavigationRequest, opening: RequestOpening) {
        when (request) {
            TimerWidgetTap -> LaunchedEffect(request) {
                opening.openSignedIn(Route.Timer)
                opening.handled(request)
            }

            ReminderTap -> LaunchedEffect(request, opening.onSignIn) {
                if (opening.onSignIn) return@LaunchedEffect
                opening.navigator.open(Route.Timer)
                opening.handled(request)
            }
        }
    }
}

/**
 * The Timer page. Its history and calendar live in its entry, which stays at the bottom of every
 * signed-in back stack, and show what the session's [SessionEntries] merges. A tap on an entry in
 * the history, or on the button for a past entry, opens the entry form above the page, and an
 * entry's Continue starts the timer with its fields. Each part reads its own state, so a state
 * that changes often, such as the ticking totals, redraws only its part.
 */
@Composable
private fun TimerPage(navigator: Navigator) {
    val timer = koinViewModel<TimerViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
    val favorites = koinViewModel<FavoritesViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
    val entries = koinViewModel<SessionEntries>(viewModelStoreOwner = LocalSignedInSession.current)
    val actions = koinViewModel<EntryActionsViewModel>(
        viewModelStoreOwner = LocalSignedInSession.current,
        parameters = { parametersOf(entries) },
    )
    val page = koinViewModel<TimerPageViewModel>()
    val history = koinViewModel<HistoryViewModel>(parameters = { parametersOf(entries) })
    val calendar = koinViewModel<CalendarViewModel>(parameters = { parametersOf(entries) })
    val pageState by page.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    TimerScreen(
        view = pageState.view,
        onViewChange = page::chooseView,
        timer = {
            val timerState by timer.state.collectAsStateWithLifecycle()
            val totals by page.totals.collectAsStateWithLifecycle()
            val templates by favorites.state.collectAsStateWithLifecycle()
            TimerBar(
                state = timerState,
                page = pageState,
                totals = totals,
                templates = templates,
                actions = timerBarActions(timer, favorites, navigator, scope),
            )
        },
        listContent = {
            val historyState by history.state.collectAsStateWithLifecycle()
            val templates by favorites.state.collectAsStateWithLifecycle()
            HistoryList(
                state = historyState,
                onLoadOlder = history::loadOlder,
                onEdit = { entryId -> navigator.openEntry(EntryOpening.Edit(entryId), older = history.entry(entryId)) },
                onContinue = { entryId ->
                    history.entry(entryId)?.let { source ->
                        timer.continueEntry(source)
                        settleDescription(focusManager)
                    }
                },
                onDuplicate = actions::duplicate,
                onSplit = actions::split,
                onDelete = actions::delete,
                pin = pinAction(templates) { entryId -> actions.pin(entryId, favorites, pageState.offline) },
            )
        },
        feedback = {
            val actionsState by actions.state.collectAsStateWithLifecycle()
            EntryActionsHost(
                state = actionsState,
                onUndo = actions::undo,
                onNoticeEnd = actions::noticeDone,
                onConfirmDelete = actions::confirmDelete,
                onCancelDelete = actions::cancelDelete,
                settings = pageState.settings,
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

/** What the Timer page's timer does: [timer]'s taps, [favorites]' templates, and the screens it opens. */
private fun timerBarActions(
    timer: TimerViewModel,
    favorites: FavoritesViewModel,
    navigator: Navigator,
    scope: CoroutineScope,
) = TimerBarActions(
    onShown = timer::shown,
    onDescriptionChange = timer::editDescription,
    onDescriptionFinish = timer::descriptionDone,
    onPick = timer::pick,
    onBillableChange = timer::setBillable,
    onFill = timer::fill,
    onStart = timer::start,
    onStop = timer::stop,
    onPause = timer::pause,
    onResume = timer::resume,
    onStartTemplate = timer::startFrom,
    onSaveTemplate = { fields, onDone -> favorites.add(fields, onDone) },
    onManageTemplates = { navigator.open(TemplatesRoute) },
    onAddEntry = { navigator.openEntry(EntryOpening.New()) },
    onEditRunning = {
        scope.launch {
            val identity = timer.runningForEdit() ?: return@launch
            val top = navigator.top
            if (top == Route.Timer || top is Route.Entry) navigator.openEntry(EntryOpening.Running.of(identity))
        }
    },
    onOpenFullscreen = {
        if (navigator.top == Route.Timer && timer.state.value.target != null) navigator.push(FullscreenTimerRoute)
    },
)

/** The running timer on its own. Its edit button opens the entry form on the segment it shows. */
@Composable
private fun FullscreenTimer(navigator: Navigator) {
    val timer = koinViewModel<TimerViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
    val scope = rememberCoroutineScope()
    FullscreenTimerScreen(
        model = timer,
        onExit = { navigator.close(FullscreenTimerRoute) },
        onEditRunning = { target ->
            scope.launch {
                if (navigator.top != FullscreenTimerRoute) return@launch
                val identity = timer.runningForEditShown(target) ?: return@launch
                if (navigator.top == FullscreenTimerRoute) navigator.openEntry(EntryOpening.Running.of(identity))
            }
        },
    )
}

/**
 * The entry form [route] opens. Its view model is its own, opened on what the route says when
 * this process opened it ([EntryFormOpenings]). An edit saves through the session's
 * [SessionEntries], and a delete goes through the session's entry actions, so the Timer page offers
 * its Undo.
 */
@Composable
private fun EntryForm(route: Route.Entry, navigator: Navigator) {
    val openings = navigator.openings
    val entries = koinViewModel<SessionEntries>(viewModelStoreOwner = LocalSignedInSession.current)
    val model = koinViewModel<EntryFormViewModel>(parameters = { parametersOf(openings.take(route), entries) })
    val actions = koinViewModel<EntryActionsViewModel>(
        viewModelStoreOwner = LocalSignedInSession.current,
        parameters = { parametersOf(entries) },
    )
    EntryFormScreen(
        model = model,
        canRestore = actions::canRestoreDelete,
        onEntryDelete = actions::deleted,
        onClose = { navigator.close(route) },
    )
}

/** The Timer page, first in the bar and the rail. */
val timerPage = TopLevelDestination(
    route = Route.Timer,
    label = R.string.nav_timer,
    icon = UiR.drawable.ic_nav_timer,
    hue = SectionHues.timer,
    position = 0,
)

/** The Timer templates page, on the More sheet after Reports, while the server keeps the favorites key. */
val templatesPage = TopLevelDestination(
    route = TemplatesRoute,
    label = R.string.nav_templates,
    icon = R.drawable.ic_nav_templates,
    hue = SectionHues.more,
    position = 40,
    inMore = true,
    isAvailable = { it.favorites },
)

/**
 * The extra of a Timer widget's tap, which the widget puts in its intent (`WidgetNavigation.OPEN_TIMER`
 * in the widget feature). The app's DeepLinksTest checks that the two agree.
 */
internal const val TIMER_WIDGET_TAP = "open_timer_widget"

/**
 * The extra of a reminder notification's tap, which the reminder puts in its intent
 * (`ReminderNotifier.OPEN_TIMER` in the notification feature).
 */
internal const val REMINDER_TAP = "com.github.e0ipso.tokkl.OPEN_TIMER_REMINDER"

/** A Timer widget's tap. */
@Serializable
data object TimerWidgetTap : NavigationRequest

/** A reminder notification's tap. */
@Serializable
data object ReminderTap : NavigationRequest

/** The running timer on its own, without the bar. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.FullscreenTimer")
data object FullscreenTimerRoute : Route {
    override val focused: Boolean get() = true

    override val showsTimerNotices: Boolean get() = true
}

/** The Timer templates page. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Templates")
data object TemplatesRoute : Route

/** The Timer page's running dot, while an entry runs. */
class TimerBadge(cache: AccountCache) :
    PageBadge(Route.Timer, R.string.nav_timer_running, cache.running.map { it != null })
