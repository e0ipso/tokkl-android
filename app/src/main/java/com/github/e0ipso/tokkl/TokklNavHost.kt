package com.github.e0ipso.tokkl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.calendar.CalendarModel
import com.github.e0ipso.tokkl.calendar.WeekCalendar
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.clients.ClientsModel
import com.github.e0ipso.tokkl.clients.ClientsScreen
import com.github.e0ipso.tokkl.entry.EntryFormModel
import com.github.e0ipso.tokkl.entry.EntryFormScreen
import com.github.e0ipso.tokkl.favorites.FavoritesModel
import com.github.e0ipso.tokkl.favorites.TemplatesScreen
import com.github.e0ipso.tokkl.history.EntryActionsHost
import com.github.e0ipso.tokkl.history.EntryActionsModel
import com.github.e0ipso.tokkl.history.HistoryList
import com.github.e0ipso.tokkl.history.HistoryModel
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.navigation.AppNavigationBar
import com.github.e0ipso.tokkl.navigation.AppNavigationRail
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionPrompt
import com.github.e0ipso.tokkl.pomodoro.PomodoroScreen
import com.github.e0ipso.tokkl.pomodoro.PomodoroViewModel
import com.github.e0ipso.tokkl.projects.ProjectsModel
import com.github.e0ipso.tokkl.projects.ProjectsScreen
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.reports.ReportsModel
import com.github.e0ipso.tokkl.reports.ReportsScreen
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.settings.SettingsScreen
import com.github.e0ipso.tokkl.settings.SignOutModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.signin.SignInAgain
import com.github.e0ipso.tokkl.signin.SignInModel
import com.github.e0ipso.tokkl.signin.SignInScreen
import com.github.e0ipso.tokkl.sync.DiscardNotice
import com.github.e0ipso.tokkl.sync.NeedsAttentionModel
import com.github.e0ipso.tokkl.sync.NeedsAttentionScreen
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.sync.SyncStatusBand
import com.github.e0ipso.tokkl.tags.TagsModel
import com.github.e0ipso.tokkl.tags.TagsScreen
import com.github.e0ipso.tokkl.timer.TimerBar
import com.github.e0ipso.tokkl.timer.TimerModel
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.settleDescription
import com.github.e0ipso.tokkl.trash.TrashModel
import com.github.e0ipso.tokkl.trash.TrashScreen
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.trust.TrustPrompt
import com.github.e0ipso.tokkl.trust.TrustRefusedNotice
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.theme.LocalSectionHue
import com.github.e0ipso.tokkl.ui.windowWidth
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's screens. ENTRY is the entry form, for the entry or the manual entry that its model
 * holds. NEEDS_ATTENTION lists the changes the server refused.
 */
enum class Destination { SIGN_IN, TIMER, POMODORO, REPORTS, PROJECTS, CLIENTS, TAGS, TRASH, TEMPLATES, SETTINGS, LICENCES, ENTRY, NEEDS_ATTENTION }

/**
 * The app's navigation host: a back stack of [Destination]s that survives configuration changes
 * and process death. It starts at sign-in until a server and its token are stored, and at the
 * Timer page after that. A restored signed-in back stack goes to sign-in when nobody is signed in
 * any more.
 *
 * Every signed-in screen has the bottom bar ([AppNavigationBar]), or on a medium or expanded
 * window the navigation rail ([AppNavigationRail]) with the same items. Opening a page from it puts
 * that page right above Timer, so Back from any page in the bar or the More sheet goes to Timer,
 * and Back from Timer closes the app (docs/PRD.md B3). A screen opened from a page, such as Licences from
 * Settings, stacks above that page, and the bar keeps marking the page. While [timerRunning] says
 * a time entry runs, the bar's Timer item carries a dot. The Timer page's [timer] picks projects,
 * tasks and tags from [cache]. A tap on an entry in the history, or on the Timer page's button for
 * a past entry, opens [entryForm] above the Timer page, beside its List view on an expanded window
 * ([SignedInPanes]), an entry's menu deletes it through
 * [entryActions], and its Continue button starts [timer] with its fields. Trash, on [trash], shows
 * only while the server's [capabilities] have it: when they lose it, the page goes back to Timer.
 * Timer templates, on [favorites], show in More the same way, while the server keeps that key.
 *
 * Above any screen of a signed-in session it shows the prompt for a request that [sessionTrust]
 * saw refused, and after a cancel a notice that opens the prompt again. Sign-in shows its own
 * prompts. Above the bottom bar, or at the bottom beside the rail, it shows [syncStatus] while
 * changes wait to reach the server (docs/PRD.md O5, O6). Its button opens the [needsAttention]
 * list above the page, and after a refused token its other button opens sign-in above the page,
 * filled in for [signedInAccount], so the user signs in again to the same account. At the bottom
 * of a signed-in screen, a notice says how many changes [discardedChanges] counts, until
 * [onDismissDiscarded].
 */
@Composable
fun TokklNavHost(
    signInModel: SignInModel,
    themeSettings: ThemeSettingsModel,
    signOut: SignOutModel,
    sessionTrust: SessionTrustModel,
    reports: ReportsModel,
    history: HistoryModel,
    entryActions: EntryActionsModel,
    calendar: CalendarModel,
    projects: ProjectsModel,
    clients: ClientsModel,
    tags: TagsModel,
    timerView: TimerViewSetting,
    timer: TimerModel,
    entryForm: EntryFormModel,
    cache: AccountCache,
    trash: TrashModel,
    timerRunning: Flow<Boolean> = flowOf(false),
    capabilities: StateFlow<Capabilities> = MutableStateFlow(Capabilities.NONE),
    favorites: FavoritesModel? = null,
    syncStatus: Flow<SyncStatus?> = flowOf(null),
    needsAttention: NeedsAttentionModel? = null,
    settings: StateFlow<AccountSettings?> = MutableStateFlow(null),
    discardedChanges: StateFlow<Int> = MutableStateFlow(0),
    onDismissDiscarded: () -> Unit = {},
    signedInAccount: () -> SignInAgain? = { null },
    notificationPermission: NotificationPermissionAsk? = null,
    onNotificationsAllowed: () -> Unit = {},
    reminderSettings: ReminderSetting? = null,
    openReminderTimer: Int = 0,
) {
    LaunchedEffect(entryActions, calendar, history) {
        entryActions.reconciliation.collect { overrides ->
            calendar.reconcile(overrides)
            history.reconcile(overrides)
        }
    }
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val backStack = rememberSaveable(saver = BackStackSaver) { SnapshotStateList<Destination>() }
    // A signed-in page restored from saved state can outlive its session: a sign-out that finished
    // in the background before the process died, or a token that no longer decrypts. The page
    // shows while the stores are read, once per composition of the host, so sign-in pays nothing.
    val restoredSignedIn = remember { backStack.lastOrNull().let { it != null && it != Destination.SIGN_IN } }
    if (restoredSignedIn) {
        LaunchedEffect(Unit) {
            val signedIn = withContext(Dispatchers.IO) { Session(appContext).isSignedIn() }
            if (!signedIn) {
                backStack.clear()
                backStack.add(Destination.SIGN_IN)
            }
        }
    }
    if (backStack.isEmpty()) {
        // Shows the window background, which matches the theme, while the
        // stores are read off the main thread.
        LaunchedEffect(Unit) {
            val signedIn = withContext(Dispatchers.IO) { Session(appContext).isSignedIn() }
            backStack.add(if (signedIn) Destination.TIMER else Destination.SIGN_IN)
        }
        return
    }
    BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
    val destination = backStack.last()
    if (destination == Destination.SIGN_IN) {
        SignInScreen(
            model = signInModel,
            onSignedIn = {
                signInModel.reset()
                backStack.open(TopLevelPage.TIMER)
            },
        )
        return
    }
    LaunchedEffect(openReminderTimer) {
        if (openReminderTimer > 0) backStack.open(TopLevelPage.TIMER)
    }
    val capabilityFlags = capabilities.collectAsState().value
    val offersTrash = capabilityFlags.trash
    val offersTemplates = capabilityFlags.favorites && favorites != null
    LaunchedEffect(offersTrash, offersTemplates) {
        if ((!offersTrash && Destination.TRASH in backStack) || (!offersTemplates && Destination.TEMPLATES in backStack)) {
            backStack.open(TopLevelPage.TIMER)
        }
    }
    val view by timerView.view.collectAsState()
    val running by timerRunning.collectAsState(initial = false)
    val status by syncStatus.collectAsState(initial = null)
    val width = windowWidth()
    // Counts the openings of the entry form, so a form opened beside the history over another one
    // starts afresh, as a form opened on its own screen does.
    var entryOpenings by rememberSaveable { mutableIntStateOf(0) }
    val openEntry: () -> Unit = {
        entryOpenings++
        if (backStack.lastOrNull() != Destination.ENTRY) backStack.add(Destination.ENTRY)
    }
    val discarded by discardedChanges.collectAsState()
    val changes = (appContext as TokklApplication).changes
    val shortUndo by changes.shortStopUndo.collectAsState(initial = null)
    val shortHost = remember { SnackbarHostState() }
    val shortMessage = stringResource(R.string.timer_short_stop_trashed)
    val undoLabel = stringResource(R.string.history_undo)
    LaunchedEffect(destination, shortUndo?.entryId, shortUndo?.capturedAt) {
        val pending = shortUndo ?: return@LaunchedEffect
        if (destination != Destination.TIMER) return@LaunchedEffect
        val result = shortHost.showSnackbar(shortMessage, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) changes.undoShortStop(pending.entryId)
        else changes.dismissShortStop(pending.entryId)
    }
    SignedInFrame(
        current = backStack.currentPage(),
        onOpen = backStack::open,
        timerRunning = running,
        hidden = buildSet {
            if (!offersTrash) add(TopLevelPage.TRASH)
            if (!offersTemplates) add(TopLevelPage.TEMPLATES)
        },
        syncStatus = status,
        onOpenNeedsAttention = needsAttention?.let { { if (backStack.lastOrNull() != Destination.NEEDS_ATTENTION) backStack.add(Destination.NEEDS_ATTENTION) } },
        onSignInAgain = {
            scope.launch {
                // Reads the stored server, off the main thread.
                val account = withContext(Dispatchers.IO) { signedInAccount() } ?: return@launch
                signInModel.signInAgain(account)
                backStack.add(Destination.SIGN_IN)
            }
        },
        width = width,
    ) {
        SignedInPanes(
            destination = destination,
            entryBeside = showsEntryBeside(width, view, backStack),
            entryKey = entryOpenings,
        ) { shown ->
            Screen(
                destination = shown,
                backStack = backStack,
                themeSettings = themeSettings,
                reminderSettings = reminderSettings,
                signOut = signOut,
                reports = reports,
                history = history,
                entryActions = entryActions,
                calendar = calendar,
                projects = projects,
                clients = clients,
                tags = tags,
                trash = trash,
                entryForm = entryForm,
                cache = cache,
                timerView = view,
                onTimerViewChange = timerView::choose,
                onContinue = { entryId ->
                    history.entry(entryId)?.let { source ->
                        timer.continueEntry(source)
                        settleDescription(focusManager)
                    }
                },
                onOpenEntry = openEntry,
                favorites = favorites,
                needsAttention = needsAttention,
                settings = settings,
            ) {
                TimerBar(
                    timer,
                    cache,
                    favorites = favorites,
                    onManageTemplates = { backStack.open(TopLevelPage.TEMPLATES) },
                    onAddEntry = {
                        entryForm.new()
                        openEntry()
                    },
                    onEditRunning = {
                        scope.launch {
                            val identity = timer.runningForEdit() ?: return@launch
                            if (backStack.lastOrNull() == Destination.TIMER || backStack.lastOrNull() == Destination.ENTRY) {
                                entryForm.editRunning(identity)
                                openEntry()
                            }
                        }
                    },
                )
            }
        }
        if (discarded > 0) DiscardNotice(discarded, onDismiss = onDismissDiscarded, modifier = Modifier.align(Alignment.BottomCenter))
        if (destination == Destination.TIMER) SnackbarHost(shortHost, Modifier.align(Alignment.BottomCenter))
        SessionTrustPrompts(sessionTrust, modifier = Modifier.align(Alignment.BottomCenter))
        if (notificationPermission != null) {
            NotificationPermissionPrompt(
                timer.starts,
                notificationPermission,
                onAllowed = onNotificationsAllowed,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Whether the entry form on top of [backStack] shows beside the Timer page below it: on an expanded
 * window ([width]) in the List [view], so the history and the form share the width. The Calendar
 * view keeps the whole width for the week, so there the form takes the screen, as on a phone.
 */
internal fun showsEntryBeside(width: WindowWidth, view: TimerView, backStack: List<Destination>): Boolean =
    width == WindowWidth.EXPANDED && view == TimerView.LIST &&
        backStack.lastOrNull() == Destination.ENTRY && backStack.getOrNull(backStack.lastIndex - 1) == Destination.TIMER

/**
 * The signed-in screen on top, [destination], drawn by [screen]. With [entryBeside], which an
 * expanded window allows for the entry form above the Timer page's List view, the Timer page and
 * the form share the width, with a line between them, so the history stays in sight while an entry
 * is edited ([showsEntryBeside]). Otherwise the screen on top has the whole width, as on a phone.
 *
 * The Timer page and the form each keep one place in the composition, whether they show side by
 * side or alone, so a rotation or a fold that changes the layout keeps their saved state, and the
 * page keeps its scroll as the form opens and closes beside it. [entryKey] changes with each
 * opening of the form, so a form opened over one already beside the history starts afresh.
 */
@Composable
internal fun SignedInPanes(
    destination: Destination,
    entryBeside: Boolean,
    entryKey: Int,
    screen: @Composable (Destination) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        val page = if (entryBeside) Destination.TIMER else destination
        if (page != Destination.ENTRY) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (entryBeside) Modifier.consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.End)) else Modifier),
            ) {
                screen(page)
            }
        }
        if (entryBeside) VerticalDivider()
        if (destination == Destination.ENTRY) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (entryBeside) Modifier.consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)) else Modifier),
            ) {
                key(entryKey) { screen(Destination.ENTRY) }
            }
        }
    }
}

/**
 * A signed-in screen, [content], with the navigation that fits [width]: above the bottom bar on a
 * compact window, beside the navigation rail on wider ones. The bar or the rail marks [current]
 * and opens pages with [onOpen], leaves out the pages in [hidden], and puts a dot on Timer while
 * [timerRunning]. Right above the bar, or at the bottom of the screen beside the rail, shows
 * [syncStatus] when it is not null, its "needs attention" button calls [onOpenNeedsAttention]
 * and its sign-in button [onSignInAgain]. The bar's height, status and system navigation bar included, is padding
 * the screens consume, so their own navigation bar padding adds nothing above the bar. Beside the
 * rail, which keeps clear of the system bars at its side, the screens consume that side's insets.
 */
@Composable
internal fun SignedInFrame(
    current: TopLevelPage,
    onOpen: (TopLevelPage) -> Unit,
    timerRunning: Boolean = false,
    hidden: Set<TopLevelPage> = emptySet(),
    syncStatus: SyncStatus? = null,
    onOpenNeedsAttention: (() -> Unit)? = null,
    onSignInAgain: (() -> Unit)? = null,
    syncStatusClock: Clock = Clock.systemUTC(),
    width: WindowWidth = windowWidth(),
    content: @Composable BoxScope.() -> Unit,
) {
    val rail = width != WindowWidth.COMPACT
    Row(Modifier.fillMaxSize()) {
        if (rail) {
            AppNavigationRail(current = current, onOpen = onOpen, timerRunning = timerRunning, hidden = hidden)
        }
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .then(if (rail) Modifier.consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)) else Modifier),
            bottomBar = {
                Column {
                    if (syncStatus != null) SyncStatusBand(syncStatus, onOpenNeedsAttention, onSignInAgain = onSignInAgain, clock = syncStatusClock)
                    if (!rail) {
                        AppNavigationBar(current = current, onOpen = onOpen, timerRunning = timerRunning, hidden = hidden)
                    } else if (syncStatus != null) {
                        // Without the bar, the band keeps above the system navigation bar on its own.
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .windowInsetsBottomHeight(WindowInsets.navigationBars),
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            // The page's stickers take the current tab's hue, LocalSectionHue.
            CompositionLocalProvider(LocalSectionHue provides current.hue) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun Screen(
    destination: Destination,
    backStack: SnapshotStateList<Destination>,
    themeSettings: ThemeSettingsModel,
    reminderSettings: ReminderSetting?,
    signOut: SignOutModel,
    reports: ReportsModel,
    history: HistoryModel,
    entryActions: EntryActionsModel,
    calendar: CalendarModel,
    projects: ProjectsModel,
    clients: ClientsModel,
    tags: TagsModel,
    trash: TrashModel,
    entryForm: EntryFormModel,
    cache: AccountCache,
    timerView: TimerView,
    onTimerViewChange: (TimerView) -> Unit,
    onContinue: (Long) -> Unit,
    onOpenEntry: () -> Unit,
    favorites: FavoritesModel?,
    needsAttention: NeedsAttentionModel?,
    settings: StateFlow<AccountSettings?>,
    timer: @Composable () -> Unit,
) {
    when (destination) {
        // TokklNavHost draws sign-in on its own, without the bar.
        Destination.SIGN_IN -> Unit
        Destination.TIMER -> TimerScreen(
            view = timerView,
            onViewChange = onTimerViewChange,
            timer = timer,
            listContent = {
                HistoryList(
                    history,
                    entryActions,
                    onEdit = { entryId ->
                        entryForm.edit(entryId, older = history.entry(entryId))
                        onOpenEntry()
                    },
                    onContinue = onContinue,
                    favorites = favorites,
                    showActions = false,
                )
            },
            feedback = {
                val actionsState by entryActions.state.collectAsState()
                val account by settings.collectAsState()
                EntryActionsHost(
                    state = actionsState,
                    onUndo = entryActions::undo,
                    onNoticeDone = entryActions::noticeDone,
                    onConfirmDelete = entryActions::confirmDelete,
                    onCancelDelete = entryActions::cancelDelete,
                    settings = account,
                    onSplitTextChange = entryActions::setSplitText,
                    onConfirmSplit = entryActions::confirmSplit,
                    onCancelSplit = entryActions::cancelSplit,
                )
            },
            calendarContent = {
                WeekCalendar(
                    model = calendar,
                    onEdit = { entryId, older ->
                        entryForm.edit(entryId, older)
                        onOpenEntry()
                    },
                    onAdd = { day ->
                        entryForm.new(day)
                        onOpenEntry()
                    },
                )
            },
        )
        Destination.POMODORO -> {
            val pomodoro: PomodoroViewModel = viewModel()
            PomodoroScreen(model = pomodoro.model, cache = cache)
        }
        Destination.REPORTS -> ReportsScreen(model = reports)
        Destination.PROJECTS -> ProjectsScreen(model = projects)
        Destination.CLIENTS -> ClientsScreen(model = clients)
        Destination.TAGS -> TagsScreen(model = tags)
        Destination.TRASH -> TrashScreen(model = trash)
        Destination.TEMPLATES -> if (favorites != null) {
            val account by settings.collectAsState()
            TemplatesScreen(model = favorites, cache = cache, workspaceId = account?.defaultWorkspaceId)
        }
        Destination.SETTINGS -> SettingsScreen(
            model = themeSettings,
            reminder = reminderSettings,
            signOut = signOut,
            onOpenLicences = { backStack.add(Destination.LICENCES) },
        )
        Destination.LICENCES -> LicencesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
        Destination.ENTRY -> EntryFormScreen(
            model = entryForm,
            cache = cache,
            canRestore = entryActions::canRestoreDelete,
            onDeleted = entryActions::deleted,
            onClose = {
                if (backStack.lastOrNull() == Destination.ENTRY) backStack.removeAt(backStack.lastIndex)
                entryForm.close()
            },
        )
        Destination.NEEDS_ATTENTION -> if (needsAttention != null) {
            NeedsAttentionScreen(
                model = needsAttention,
                settings = settings,
                onEdit = { entryId ->
                    entryForm.edit(entryId, older = null)
                    onOpenEntry()
                },
                onBack = { backStack.removeAt(backStack.lastIndex) },
            )
        }
    }
}

/** Shows [page] right above Timer, or Timer alone, dropping whatever stood above Timer before. */
private fun SnapshotStateList<Destination>.open(page: TopLevelPage) {
    clear()
    add(Destination.TIMER)
    if (page != TopLevelPage.TIMER) add(page.destination)
}

/** The top-level page the screen on top belongs to: itself, or the nearest one below it. */
private fun List<Destination>.currentPage(): TopLevelPage =
    asReversed().firstNotNullOfOrNull(TopLevelPage::of) ?: TopLevelPage.TIMER

/** The prompt for a refused request, or after a cancel the notice that reopens it. [modifier] places the notice. */
@Composable
private fun SessionTrustPrompts(sessionTrust: SessionTrustModel, modifier: Modifier = Modifier) {
    val state by sessionTrust.state.collectAsState()
    val refusal = state.refusal ?: return
    if (state.promptOpen && refusal is ApiError.TrustQuestion) {
        TrustPrompt(refusal, onAccept = sessionTrust::accept, onCancel = sessionTrust::cancel)
    } else {
        TrustRefusedNotice(refusal, onReview = sessionTrust::reopen, modifier = modifier)
    }
}

/** Saves the stack by name. A name this version no longer has, such as an older version's HOME, is dropped. */
private val BackStackSaver = listSaver<SnapshotStateList<Destination>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.mapNotNull { name -> Destination.entries.firstOrNull { it.name == name } }.toMutableStateList() },
)
