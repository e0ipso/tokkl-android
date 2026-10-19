package com.github.e0ipso.tokkl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.navigation.AppNavigationBar
import com.github.e0ipso.tokkl.navigation.PlaceholderScreen
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.reports.ReportsModel
import com.github.e0ipso.tokkl.reports.ReportsScreen
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.settings.SettingsScreen
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.signin.SignInModel
import com.github.e0ipso.tokkl.signin.SignInScreen
import com.github.e0ipso.tokkl.timer.TimerScreen
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.trust.TrustPrompt
import com.github.e0ipso.tokkl.trust.TrustRefusedNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's screens. */
enum class Destination { SIGN_IN, TIMER, REPORTS, PROJECTS, CLIENTS, TAGS, TRASH, SETTINGS, LICENCES }

/**
 * The app's navigation host: a back stack of [Destination]s that survives configuration changes
 * and process death. It starts at sign-in until a server and its token are stored, and at the
 * Timer page after that.
 *
 * Every signed-in screen has the bottom bar ([AppNavigationBar]). Opening a page from it puts
 * that page right above Timer, so Back from any page in the bar or the More sheet goes to Timer,
 * and Back from Timer closes the app (docs/PRD.md B3). A screen opened from a page, such as Licences from
 * Settings, stacks above that page, and the bar keeps marking the page.
 *
 * Above any screen of a signed-in session it shows the prompt for a request that [sessionTrust]
 * saw refused, and after a cancel a notice that opens the prompt again. Sign-in shows its own
 * prompts.
 */
@Composable
fun TokklNavHost(
    signInModel: SignInModel,
    themeSettings: ThemeSettingsModel,
    sessionTrust: SessionTrustModel,
    reports: ReportsModel,
) {
    val appContext = LocalContext.current.applicationContext
    val backStack = rememberSaveable(saver = BackStackSaver) { SnapshotStateList<Destination>() }
    var timerView by rememberSaveable { mutableStateOf(TimerView.LIST) }
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
    // The bar's height, system navigation bar included, is padding the screens consume, so
    // their own navigation bar padding adds nothing above the bar.
    Scaffold(
        bottomBar = { AppNavigationBar(current = backStack.currentPage(), onOpen = backStack::open) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            Screen(destination, backStack, themeSettings, reports, timerView, onTimerViewChange = { timerView = it })
            SessionTrustPrompts(sessionTrust, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Screen(
    destination: Destination,
    backStack: SnapshotStateList<Destination>,
    themeSettings: ThemeSettingsModel,
    reports: ReportsModel,
    timerView: TimerView,
    onTimerViewChange: (TimerView) -> Unit,
) {
    when (destination) {
        // TokklNavHost draws sign-in on its own, without the bar.
        Destination.SIGN_IN -> Unit
        Destination.TIMER -> TimerScreen(view = timerView, onViewChange = onTimerViewChange)
        Destination.REPORTS -> ReportsScreen(model = reports)
        Destination.PROJECTS -> PlaceholderScreen(title = R.string.nav_projects, message = R.string.projects_later)
        Destination.CLIENTS -> PlaceholderScreen(title = R.string.nav_clients, message = R.string.clients_later)
        Destination.TAGS -> PlaceholderScreen(title = R.string.nav_tags, message = R.string.tags_later)
        Destination.TRASH -> PlaceholderScreen(title = R.string.nav_trash, message = R.string.trash_later)
        Destination.SETTINGS -> SettingsScreen(
            model = themeSettings,
            onOpenLicences = { backStack.add(Destination.LICENCES) },
        )
        Destination.LICENCES -> LicencesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
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
