package com.github.e0ipso.tokkl.navhost

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.NotificationPrompts
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.SignInAgain
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionPrompt
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.sync.DiscardNotice
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.SyncStatusSource
import com.github.e0ipso.tokkl.timer.TimerNotices
import com.github.e0ipso.tokkl.timer.TimerState
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timerstate.HeldTimerFlows
import com.github.e0ipso.tokkl.ui.windowWidth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.github.e0ipso.tokkl.feature.timer.R as TimerR

/**
 * A signed-in back stack, [stack], with [top] on screen, in the [SignedInFrame]. The bar or the
 * rail marks the page [top] belongs to and opens pages ([Navigator.open]). While a time entry
 * runs, the bar's Timer item carries a dot, and while a Pomodoro session runs, Pomodoro's. The
 * pages in [unavailable] show nowhere, and a stack that holds one goes to Timer.
 *
 * The fullscreen timer hides the bar, and so does the entry form opened from it. When the timer
 * goes idle, by the server's word, both close down to Timer. The entry form shows beside the
 * Timer page's List view on an expanded window ([SignedInPanes]).
 *
 * Above the bar, or at the bottom beside the rail, shows the sync status while changes wait to
 * reach the server (docs/PRD.md O5, O6). Its button opens the changes the server refused above
 * the page, and after a refused token its other button opens sign-in above the page, filled in
 * for the account, so the user signs in again to the same account. Above the bar shows the
 * session's trust prompt, or after a cancel the notice that reopens it. At the bottom of the
 * screen, a notice says how many changes were discarded, until dismissed. On the pages that show
 * the timer, the short Stop's Undo and the timer's notices share one snackbar host, so they
 * queue, never overlap. With [notificationPermission], a timer start or a Pomodoro start may ask
 * for the notification permission, and [onNotificationsAllow] hears the grant.
 */
@Composable
internal fun SignedInHost(
    stack: List<Route>,
    top: Route,
    entries: List<NavEntry<Route>>,
    navigator: Navigator,
    unavailable: Set<TopLevelPage>,
    prompts: NotificationPrompts,
    notificationPermission: NotificationPermissionAsk?,
    onNotificationsAllow: () -> Unit,
) {
    val io: CoroutineDispatcher = koinInject(IoDispatcher)
    val timer = sessionViewModel<TimerViewModel>()
    val cache: AccountCache = koinInject()
    val held: HeldTimerFlows = koinInject()
    val changes: ChangeQueue = koinInject()
    val discarded: DiscardedChanges = koinInject()
    val servers: ServerConfigStore = koinInject()
    val accountSettings: AccountSettingsSource = koinInject()
    val scope = rememberCoroutineScope()
    LaunchedEffect(unavailable) {
        if (stack.holdsAnyOf(unavailable)) navigator.open(TopLevelPage.TIMER)
    }
    val view by koinInject<TimerViewSetting>().view.collectAsState()
    val runningFlow = remember(cache) { cache.running.map { it != null }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = false)
    // The server's session, from the held timer state.
    val pomodoroRunningFlow = remember(held) { held.held.map { it?.state?.pomodoro != null }.distinctUntilChanged() }
    val pomodoroRunning by pomodoroRunningFlow.collectAsState(initial = false)
    val status by koinInject<SyncStatusSource>().status.collectAsState(initial = null)
    val width = windowWidth()
    val focused = navigator.focused
    val timerState by timer.state.collectAsState()
    LaunchedEffect(focused, timerState) {
        // A notification may have opened Pomodoro before this old focused effect runs.
        if (focused && (timerState as? TimerState.Idle)?.authoritative == true &&
            navigator.focused
        ) {
            navigator.downToTimer()
        }
    }
    val discardedCount by discarded.state.collectAsState()
    val shortUndo by changes.shortStopUndo.collectAsState(initial = null)
    // The short Stop's Undo and the timer's notices share one host, so they queue, never overlap.
    val shortHost = remember { SnackbarHostState() }
    val shortMessage = stringResource(R.string.timer_short_stop_trashed)
    val undoLabel = stringResource(TimerR.string.history_undo)
    LaunchedEffect(top, shortUndo?.entryId, shortUndo?.capturedAt) {
        val pending = shortUndo ?: return@LaunchedEffect
        if (top != Route.Timer) return@LaunchedEffect
        val result = shortHost.showSnackbar(
            shortMessage,
            undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            changes.undoShortStop(pending.entryId)
        } else {
            changes.dismissShortStop(pending.entryId)
        }
    }
    val permissionStarts = remember(timer, prompts) { merge(timer.starts, prompts.pomodoroStarts.receiveAsFlow()) }
    val permissionRetries = remember(prompts) { prompts.retries.receiveAsFlow() }
    SignedInFrame(
        current = stack.currentPage(),
        showNavigation = !focused,
        onOpen = { page -> navigator.open(page, unavailable) },
        timerRunning = running,
        pomodoroRunning = pomodoroRunning,
        hidden = unavailable,
        syncStatus = status,
        onOpenNeedsAttention = navigator::openNeedsAttention,
        onSignInAgain = {
            scope.launch {
                // Reads the stored server, off the main thread.
                val account = withContext(io) {
                    servers.load()?.let { server -> SignInAgain(server, accountSettings.state.value?.userId) }
                } ?: return@launch
                navigator.push(Route.SignIn.again(account))
            }
        },
        width = width,
        notice = { SessionTrustPrompts(koinInject()) },
    ) {
        if (top == Route.FullscreenTimer) {
            entries.Show(top)
        } else {
            SignedInPanes(
                top = top,
                entryBeside = showsEntryBeside(width, view, stack),
            ) { route -> entries.Show(route) }
        }
        if (discardedCount > 0) {
            DiscardNotice(
                discardedCount,
                onDismiss = discarded::dismiss,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (top in TIMER_PAGES) {
            TimerNotices(timer, shortHost)
            SnackbarHost(shortHost, Modifier.align(Alignment.BottomCenter))
        }
        if (notificationPermission != null) {
            NotificationPermissionPrompt(
                permissionStarts,
                notificationPermission,
                onAllow = onNotificationsAllow,
                modifier = Modifier.align(Alignment.BottomCenter),
                retries = permissionRetries,
                onResult = { prompts.answers++ },
            )
        }
    }
}

/** Draws the back stack entry of [route]. */
@Composable
internal fun List<NavEntry<Route>>.Show(route: Route) {
    firstOrNull { it.contentKey == route.contentKey }?.Content()
}

/**
 * The pages that show the timer, where its notices show: a failed Pause, Resume or Stop while
 * paused, and a pomodoro too short to save.
 */
private val TIMER_PAGES = setOf(Route.Timer, Route.FullscreenTimer, Route.Pomodoro)
