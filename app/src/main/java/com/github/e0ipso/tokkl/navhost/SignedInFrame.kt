package com.github.e0ipso.tokkl.navhost

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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.sync.SyncStatus
import com.github.e0ipso.tokkl.sync.SyncStatusBand
import com.github.e0ipso.tokkl.trust.SessionTrust
import com.github.e0ipso.tokkl.trust.TrustPrompt
import com.github.e0ipso.tokkl.trust.TrustRefusedNotice
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.theme.LocalSectionHue
import com.github.e0ipso.tokkl.ui.windowWidth
import java.time.Clock

/**
 * The signed-in screen on top, [top], drawn by [screen]. With [entryBeside], which an expanded
 * window allows for the entry form above the Timer page's List view, the Timer page and the form
 * share the width, with a line between them, so the history stays in sight while an entry is
 * edited ([showsEntryBeside]). Otherwise the screen on top has the whole width, as on a phone.
 *
 * The Timer page and the form each keep one place in the composition, whether they show side by
 * side or alone, so a rotation or a fold that changes the layout keeps their saved state, and the
 * page keeps its scroll as the form opens and closes beside it. Each opening of the form is a
 * route of its own, so a form opened over one already beside the history starts afresh.
 */
@Composable
internal fun SignedInPanes(top: Route, entryBeside: Boolean, screen: @Composable (Route) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        val page = if (entryBeside) Route.Timer else top
        if (page !is Route.Entry) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (entryBeside) {
                            Modifier.consumeWindowInsets(
                                WindowInsets.systemBars.only(WindowInsetsSides.End),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                screen(page)
            }
        }
        if (entryBeside) VerticalDivider()
        if (top is Route.Entry) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (entryBeside) {
                            Modifier.consumeWindowInsets(
                                WindowInsets.systemBars.only(WindowInsetsSides.Start),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                key(top) { screen(top) }
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
 * and its sign-in button [onSignInAgain]. Above both shows [notice], when there is one, and the
 * screens end at its top, so it covers none of them. The bar's height, status and system navigation bar included, is padding
 * the screens consume, so their own navigation bar padding adds nothing above the bar. Beside the
 * rail, which keeps clear of the system bars at its side, the screens consume that side's insets.
 */
@Composable
internal fun SignedInFrame(
    current: TopLevelPage,
    onOpen: (TopLevelPage) -> Unit,
    timerRunning: Boolean = false,
    pomodoroRunning: Boolean = false,
    hidden: Set<TopLevelPage> = emptySet(),
    syncStatus: SyncStatus? = null,
    onOpenNeedsAttention: (() -> Unit)? = null,
    onSignInAgain: (() -> Unit)? = null,
    syncStatusClock: Clock = Clock.systemUTC(),
    width: WindowWidth = windowWidth(),
    showNavigation: Boolean = true,
    notice: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val rail = showNavigation && width != WindowWidth.COMPACT
    Row(Modifier.fillMaxSize()) {
        if (rail) {
            AppNavigationRail(
                current = current,
                onOpen = onOpen,
                timerRunning = timerRunning,
                pomodoroRunning = pomodoroRunning,
                hidden = hidden,
            )
        }
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (rail) {
                        Modifier.consumeWindowInsets(
                            WindowInsets.systemBars.only(WindowInsetsSides.Start),
                        )
                    } else {
                        Modifier
                    },
                ),
            bottomBar = {
                Column {
                    // The bar, or the band's spacer, keeps clear of the system navigation bar for
                    // the notice too.
                    val clearsSystemBar = (showNavigation && !rail) || syncStatus != null
                    Box(
                        if (clearsSystemBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier,
                    ) { notice() }
                    if (syncStatus != null) {
                        SyncStatusBand(
                            syncStatus,
                            onOpenNeedsAttention,
                            onSignInAgain = onSignInAgain,
                            clock = syncStatusClock,
                        )
                    }
                    if (showNavigation && !rail) {
                        AppNavigationBar(
                            current = current,
                            onOpen = onOpen,
                            timerRunning = timerRunning,
                            pomodoroRunning = pomodoroRunning,
                            hidden = hidden,
                        )
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

/** The prompt for a refused request, or after a cancel the notice that reopens it. */
@Composable
internal fun SessionTrustPrompts(sessionTrust: SessionTrust) {
    val state by sessionTrust.state.collectAsState()
    val refusal = state.refusal ?: return
    if (state.promptOpen && refusal is ApiError.TrustQuestion) {
        TrustPrompt(refusal, onAccept = sessionTrust::accept, onCancel = sessionTrust::cancel)
    } else {
        TrustRefusedNotice(refusal, onReview = sessionTrust::reopen)
    }
}
