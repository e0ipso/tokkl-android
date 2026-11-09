package com.github.e0ipso.tokkl.navhost

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.navigation.FrameContributions
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.timernotice.TimerFrame
import com.github.e0ipso.tokkl.timernotice.timerNoticeText
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.windowWidth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.compose.viewmodel.koinViewModel
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * A signed-in back stack, [stack], with [top] on screen, in the [SignedInFrame], with what the
 * [host] lends every screen. The bar or the rail shows the [pages], marks the page [top] belongs
 * to and opens pages ([Navigator.open]). The items of the pages whose [PageBadge]s say so carry a
 * running dot. The pages whose routes are in [unavailable] show nowhere, and a stack that holds
 * one goes to Timer.
 *
 * A focused screen, the fullscreen timer, hides the bar, and so does the entry form opened from
 * it. When the timer goes idle, by the server's word, both close down to Timer. The entry form
 * shows beside the Timer page's List view on an expanded window ([SignedInPanes]).
 *
 * Each of the [features] may show a band above the bar, or at the bottom beside the rail, such as
 * the sync status, and a notice over the page, such as the changes discarded. Above the bar shows
 * the session's trust prompt, or after a cancel the notice that reopens it. On the pages that show
 * the timer ([Route.showsTimerNotices]), the short Stop's Undo and the timer's notices share one
 * snackbar host, so they queue, never overlap. With [notificationPrompt], a timer start or a
 * Pomodoro start may ask for the notification permission. [timerFrame] gives what the frame reads
 * of the timer, from the signed-in session's store.
 */
@Composable
internal fun SignedInHost(
    stack: List<Route>,
    top: Route,
    entries: List<NavEntry<Route>>,
    host: NavigationHost,
    features: List<FrameContributions>,
    pages: TopLevelPages,
    unavailable: Set<Route>,
    notificationPrompt: NotificationPrompt?,
    timerFrame: @Composable () -> TimerFrame,
) {
    val navigator = host.navigator
    val prompts = host.prompts
    val model = koinViewModel<SignedInViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
    val timer = timerFrame()
    val state by model.state.collectAsStateWithLifecycle()
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
    val listView by timer.listView.collectAsStateWithLifecycle()
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
        badges = state.badges,
        hidden = unavailable,
        band = features.firstNotNullOfOrNull { it.frameBand(host) },
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
        if (top.focused) {
            entries.Show(top)
        } else {
            SignedInPanes(
                top = top,
                entryBeside = showsEntryBeside(width, listView, stack),
            ) { route -> entries.Show(route) }
        }
        for (feature in features) {
            feature.frameNotice(host)?.invoke(Modifier.align(Alignment.BottomCenter))
        }
        if (top.showsTimerNotices) {
            TimerNotices(timer, shortHost)
            SnackbarHost(shortHost, Modifier.align(Alignment.BottomCenter))
        }
        notificationPrompt?.invoke(this, permissionStarts, permissionRetries) { prompts.answers++ }
    }
}

/**
 * The notification permission's prompt, which MainActivity hands the host: it asks for the
 * permission after the timer's or a Pomodoro start in `starts`, again on each of `retries`, and
 * calls `onAnswer` once the user answered, so a page reads the permission again.
 */
internal typealias NotificationPrompt =
    @Composable BoxScope.(starts: Flow<Unit>, retries: Flow<Unit>, onAnswer: () -> Unit) -> Unit

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
 * This route, or Timer when it is a page in [unavailable], the pages the server lacks
 * (`TopLevelPages.unavailable`). A back stack restored from before a capability was lost, or a
 * direct open of a page, then shows Timer.
 */
fun Route.orTimerIfUnavailable(unavailable: Set<Route>): Route = if (this in unavailable) Route.Timer else this

/** Whether the stack holds a page in [unavailable]. */
internal fun List<Route>.holdsAnyOf(unavailable: Set<Route>): Boolean = any { it in unavailable }

/**
 * Whether the entry form on top of [stack] shows beside the Timer page below it: on an expanded
 * window ([width]) while the Timer page shows its List view ([listView]), so the history and the
 * form share the width. The Calendar view keeps the whole width for the week, so there the form
 * takes the screen, as on a phone.
 */
internal fun showsEntryBeside(width: WindowWidth, listView: Boolean, stack: List<Route>): Boolean =
    width == WindowWidth.EXPANDED && listView &&
        stack.lastOrNull() is Route.Entry && stack.getOrNull(stack.lastIndex - 1) == Route.Timer
