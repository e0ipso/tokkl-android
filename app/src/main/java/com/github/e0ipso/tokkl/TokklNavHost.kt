package com.github.e0ipso.tokkl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.calendar.CalendarModel
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.calendar.WeekCalendar
import com.github.e0ipso.tokkl.clients.ClientsModel
import com.github.e0ipso.tokkl.clients.ClientsScreen
import com.github.e0ipso.tokkl.entry.EntryFormModel
import com.github.e0ipso.tokkl.entry.EntryFormScreen
import com.github.e0ipso.tokkl.favorites.FavoritesModel
import com.github.e0ipso.tokkl.history.EntryActionsModel
import com.github.e0ipso.tokkl.history.HistoryList
import com.github.e0ipso.tokkl.history.HistoryModel
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.navigation.AppNavigationBar
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.projects.ProjectsModel
import com.github.e0ipso.tokkl.projects.ProjectsScreen
import com.github.e0ipso.tokkl.reports.ReportsModel
import com.github.e0ipso.tokkl.reports.ReportsScreen
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.settings.SettingsScreen
import com.github.e0ipso.tokkl.settings.SignOutModel
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.signin.SignInModel
import com.github.e0ipso.tokkl.signin.SignInScreen
import com.github.e0ipso.tokkl.tags.TagsModel
import com.github.e0ipso.tokkl.tags.TagsScreen
import com.github.e0ipso.tokkl.timer.TimerBar
import com.github.e0ipso.tokkl.timer.TimerModel
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.trash.TrashModel
import com.github.e0ipso.tokkl.trash.TrashScreen
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.trust.TrustPrompt
import com.github.e0ipso.tokkl.trust.TrustRefusedNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** The app's screens. ENTRY is the entry form, for the entry or the manual entry that its model holds. */
enum class Destination { SIGN_IN, TIMER, REPORTS, PROJECTS, CLIENTS, TAGS, TRASH, SETTINGS, LICENCES, ENTRY }

/**
 * The app's navigation host: a back stack of [Destination]s that survives configuration changes
 * and process death. It starts at sign-in until a server and its token are stored, and at the
 * Timer page after that. A restored signed-in back stack goes to sign-in when nobody is signed in
 * any more.
 *
 * Every signed-in screen has the bottom bar ([AppNavigationBar]). Opening a page from it puts
 * that page right above Timer, so Back from any page in the bar or the More sheet goes to Timer,
 * and Back from Timer closes the app (docs/PRD.md B3). A screen opened from a page, such as Licences from
 * Settings, stacks above that page, and the bar keeps marking the page. While [timerRunning] says
 * a time entry runs, the bar's Timer item carries a dot. The Timer page's [timer] picks projects,
 * tasks and tags from [cache]. A tap on an entry in the history, or on the Timer page's button for
 * a past entry, opens [entryForm] above the Timer page, an entry's menu deletes it through
 * [entryActions], and its Continue button starts [timer] with its fields. Trash, on [trash], shows
 * only while the server's [capabilities] have it: when they lose it, the page goes back to Timer.
 *
 * Above any screen of a signed-in session it shows the prompt for a request that [sessionTrust]
 * saw refused, and after a cancel a notice that opens the prompt again. Sign-in shows its own
 * prompts.
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
) {
    val appContext = LocalContext.current.applicationContext
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
    val offersTrash = capabilities.collectAsState().value.trash
    LaunchedEffect(offersTrash) {
        if (!offersTrash && Destination.TRASH in backStack) backStack.open(TopLevelPage.TIMER)
    }
    val view by timerView.view.collectAsState()
    val running by timerRunning.collectAsState(initial = false)
    SignedInFrame(
        current = backStack.currentPage(),
        onOpen = backStack::open,
        timerRunning = running,
        hidden = if (offersTrash) emptySet() else setOf(TopLevelPage.TRASH),
    ) {
        Screen(
            destination = destination,
            backStack = backStack,
            themeSettings = themeSettings,
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
            onContinue = { entryId -> history.entry(entryId)?.let(timer::continueEntry) },
            favorites = favorites,
        ) {
            TimerBar(timer, cache, favorites = favorites, onAddEntry = {
                entryForm.new()
                backStack.add(Destination.ENTRY)
            })
        }
        SessionTrustPrompts(sessionTrust, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A signed-in screen, [content], above the bottom bar, which marks [current] and opens pages with
 * [onOpen], leaves out the pages in [hidden], and puts a dot on Timer while [timerRunning]. The
 * bar's height, system navigation bar included, is padding the screens consume, so their own
 * navigation bar padding adds nothing above the bar.
 */
@Composable
internal fun SignedInFrame(
    current: TopLevelPage,
    onOpen: (TopLevelPage) -> Unit,
    timerRunning: Boolean = false,
    hidden: Set<TopLevelPage> = emptySet(),
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        bottomBar = { AppNavigationBar(current = current, onOpen = onOpen, timerRunning = timerRunning, hidden = hidden) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            content = content,
        )
    }
}

@Composable
private fun Screen(
    destination: Destination,
    backStack: SnapshotStateList<Destination>,
    themeSettings: ThemeSettingsModel,
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
    favorites: FavoritesModel?,
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
                        backStack.add(Destination.ENTRY)
                    },
                    onContinue = onContinue,
                    favorites = favorites,
                )
            },
            calendarContent = {
                WeekCalendar(
                    model = calendar,
                    onEdit = { entryId, older ->
                        entryForm.edit(entryId, older)
                        backStack.add(Destination.ENTRY)
                    },
                    onAdd = { day ->
                        entryForm.new(day)
                        backStack.add(Destination.ENTRY)
                    },
                )
            },
        )
        Destination.REPORTS -> ReportsScreen(model = reports)
        Destination.PROJECTS -> ProjectsScreen(model = projects)
        Destination.CLIENTS -> ClientsScreen(model = clients)
        Destination.TAGS -> TagsScreen(model = tags)
        Destination.TRASH -> TrashScreen(model = trash)
        Destination.SETTINGS -> SettingsScreen(
            model = themeSettings,
            signOut = signOut,
            onOpenLicences = { backStack.add(Destination.LICENCES) },
        )
        Destination.LICENCES -> LicencesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
        Destination.ENTRY -> EntryFormScreen(
            model = entryForm,
            cache = cache,
            onClose = {
                if (backStack.lastOrNull() == Destination.ENTRY) backStack.removeAt(backStack.lastIndex)
                entryForm.close()
            },
        )
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
