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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.NotificationPrompts
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionPrompt
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.signin.SignInRoute
import com.github.e0ipso.tokkl.sync.DiscardNotice
import com.github.e0ipso.tokkl.sync.openNeedsAttention
import com.github.e0ipso.tokkl.timer.FullscreenTimerRoute
import com.github.e0ipso.tokkl.timernotice.TimerFrame
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import com.github.e0ipso.tokkl.ui.windowWidth
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * A signed-in back stack, [stack], with [top] on screen, in the [SignedInFrame]. The bar or the
 * rail shows the [pages], marks the page [top] belongs to and opens pages ([Navigator.open]). While a time entry
 * runs, the bar's Timer item carries a dot, and while a Pomodoro session runs, Pomodoro's. The
 * pages whose routes are in [unavailable] show nowhere, and a stack that holds one goes to Timer.
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
 * for the notification permission, and [onNotificationsAllow] hears the grant. [timerFrame] gives
 * what the frame reads of the timer, from the signed-in session's store.
 */
@Composable
internal fun SignedInHost(
    stack: List<Route>,
    top: Route,
    entries: List<NavEntry<Route>>,
    navigator: Navigator,
    pages: TopLevelPages,
    unavailable: Set<Route>,
    prompts: NotificationPrompts,
    notificationPermission: NotificationPermissionAsk?,
    onNotificationsAllow: () -> Unit,
    timerFrame: @Composable () -> TimerFrame,
) {
    val model = koinViewModel<SignedInViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
    val timer = timerFrame()
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(unavailable) {
        if (stack.holdsAnyOf(unavailable)) navigator.open(Route.Timer)
    }
    val width = windowWidth()
    val focused = navigator.focused
    // Collected while stopped too, so a timer stopped outside the app has closed these screens by
    // the first frame back.
    val settledIdle by remember(timer) { timer.settledIdle }.collectAsState(initial = false)
    LaunchedEffect(focused, settledIdle) {
        // A notification may have opened Pomodoro before this old focused effect runs.
        if (focused && settledIdle && navigator.focused) {
            navigator.downToTimer()
        }
    }
    val shortUndo = state.shortStopUndo
    // The short Stop's Undo and the timer's notices share one host, so they queue, never overlap.
    val shortHost = remember { SnackbarHostState() }
    val shortMessage = stringResource(R.string.timer_short_stop_trashed)
    val undoLabel = stringResource(UiR.string.history_undo)
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
            model.undoShortStop(pending.entryId)
        } else {
            model.dismissShortStop(pending.entryId)
        }
    }
    val permissionStarts = remember(timer, prompts) { merge(timer.starts, prompts.starts.receiveAsFlow()) }
    val permissionRetries = remember(prompts) { prompts.retries.receiveAsFlow() }
    SignedInFrame(
        pages = pages,
        current = pages.currentPage(stack),
        showNavigation = !focused,
        onOpen = { route -> navigator.open(route, unavailable) },
        timerRunning = state.timerRunning,
        pomodoroRunning = state.pomodoroRunning,
        hidden = unavailable,
        syncStatus = state.syncStatus,
        onOpenNeedsAttention = navigator::openNeedsAttention,
        onSignInAgain = {
            scope.launch {
                val account = model.signInAgain() ?: return@launch
                navigator.push(SignInRoute.again(account))
            }
        },
        width = width,
        notice = {
            SessionTrustPrompts(
                state = state.trust,
                onAccept = model::acceptTrust,
                onCancel = model::cancelTrust,
                onReopen = model::reopenTrust,
            )
        },
    ) {
        if (top == FullscreenTimerRoute) {
            entries.Show(top)
        } else {
            SignedInPanes(
                top = top,
                entryBeside = showsEntryBeside(width, state.timerView, stack),
            ) { route -> entries.Show(route) }
        }
        if (state.discardedCount > 0) {
            DiscardNotice(
                state.discardedCount,
                onDismiss = model::dismissDiscarded,
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

/**
 * Shows the timer's notices in [host], one after another, while it is in the composition: a Pause,
 * Resume or Stop while paused that did not happen, and a pomodoro the server discarded as too
 * short. The frame puts it on each of the timer's pages, so every notice has one path to the
 * screen and a too-short pomodoro shows once, whichever page shows it.
 */
@Composable
private fun TimerNotices(notices: TimerFrame, host: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(notices, host, resources) {
        notices.notice.filterNotNull().collect { notice ->
            val claimed = notices.claim(notice) ?: return@collect
            try {
                host.showSnackbar(timerNoticeText(resources, claimed))
            } finally {
                notices.noticeShown(claimed)
            }
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
private val TIMER_PAGES = setOf(Route.Timer, FullscreenTimerRoute, PomodoroRoute)
