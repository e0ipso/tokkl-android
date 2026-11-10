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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.trust.SessionTrust
import com.github.e0ipso.tokkl.trust.TrustPrompt
import com.github.e0ipso.tokkl.trust.TrustRefusedNotice
import com.github.e0ipso.tokkl.ui.WindowWidth
import com.github.e0ipso.tokkl.ui.theme.LocalSectionHue
import com.github.e0ipso.tokkl.ui.windowWidth

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
 * A signed-in screen, [content], above the bottom bar on a compact window and beside the
 * navigation rail on wider ones. Right above the bar, or at the bottom of the screen beside the
 * rail, shows [band], such as the sync status. Above both shows [notice], and the screens end at
 * its top, so it covers none of them. The bar's height, status and system navigation bar included,
 * is padding the screens consume, so their own navigation bar padding adds nothing above the bar.
 * Beside the rail, which keeps clear of the system bars at its side, the screens consume that
 * side's insets.
 */
@Composable
internal fun SignedInFrame(
    pages: TopLevelPages,
    current: Route,
    onOpen: (Route) -> Unit,
    badges: Map<Route, Int> = emptyMap(),
    hidden: Set<Route> = emptySet(),
    band: (@Composable () -> Unit)? = null,
    width: WindowWidth = windowWidth(),
    showNavigation: Boolean = true,
    notice: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val rail = showNavigation && width != WindowWidth.COMPACT
    Row(Modifier.fillMaxSize()) {
        if (rail) {
            AppNavigationRail(
                pages = pages,
                current = current,
                onOpen = onOpen,
                badges = badges,
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
                    val clearsSystemBar = (showNavigation && !rail) || band != null
                    Box(
                        if (clearsSystemBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier,
                    ) { notice() }
                    band?.invoke()
                    if (showNavigation && !rail) {
                        AppNavigationBar(
                            pages = pages,
                            current = current,
                            onOpen = onOpen,
                            badges = badges,
                            hidden = hidden,
                        )
                    } else if (band != null) {
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
            val hue = checkNotNull(pages.of(current)) { "$current is no top-level page" }.hue
            CompositionLocalProvider(LocalSectionHue provides hue) {
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

/** The prompt for [state]'s refused request, or after a cancel the notice that reopens it. */
@Composable
internal fun SessionTrustPrompts(
    state: SessionTrust.State,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onReopen: () -> Unit,
) {
    val refusal = state.refusal ?: return
    if (state.promptOpen && refusal is ApiError.TrustQuestion) {
        TrustPrompt(refusal, onAccept = onAccept, onCancel = onCancel)
    } else {
        TrustRefusedNotice(refusal, onReview = onReopen)
    }
}
