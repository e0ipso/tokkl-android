package com.github.e0ipso.tokkl.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.TokklNavigationBarDefaults
import com.github.e0ipso.tokkl.ui.theme.TokklNavigationBarItem
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.navigationBarLabelWeight
import kotlinx.coroutines.launch

/**
 * The app's top-level pages, in the order of NAV in Tokkl's App.tsx (docs/PRD.md B1). As on the
 * web app on a phone, the pages [inMore] sit on the More sheet, not in the bottom bar. Admin stays
 * out, since the app has no admin features.
 */
enum class TopLevelPage(
    val destination: Destination,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val inMore: Boolean = false,
) {
    TIMER(Destination.TIMER, R.string.nav_timer, R.drawable.ic_nav_timer),
    REPORTS(Destination.REPORTS, R.string.reports, R.drawable.ic_nav_reports),
    PROJECTS(Destination.PROJECTS, R.string.nav_projects, R.drawable.ic_nav_projects),
    CLIENTS(Destination.CLIENTS, R.string.nav_clients, R.drawable.ic_nav_clients, inMore = true),
    TAGS(Destination.TAGS, R.string.nav_tags, R.drawable.ic_nav_tags, inMore = true),
    TRASH(Destination.TRASH, R.string.nav_trash, R.drawable.ic_nav_trash, inMore = true),
    SETTINGS(Destination.SETTINGS, R.string.settings, R.drawable.ic_nav_settings),
    ;

    companion object {
        /** The page [destination] opens, or null for a page below one, such as Licences. */
        fun of(destination: Destination): TopLevelPage? = entries.firstOrNull { it.destination == destination }
    }
}

/**
 * The bottom bar of a signed-in screen: Timer, Reports, Projects, Settings and More, with
 * [current] marked. More opens a sheet with the other pages, and is marked while one of them
 * shows. [onOpen] opens a page. A 1dp line in the border colour sits above the bar, as the web
 * app draws it.
 *
 * While [timerRunning] is true, because a time entry runs, the Timer item carries a dot, as the
 * web app's `.running-dot`, and TalkBack reads "Timer running" as its state, so the dot is not
 * the only sign.
 */
@Composable
fun AppNavigationBar(
    current: TopLevelPage,
    onOpen: (TopLevelPage) -> Unit,
    modifier: Modifier = Modifier,
    timerRunning: Boolean = false,
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val pages = TopLevelPage.entries.filterNot { it.inMore }
    val more = stringResource(R.string.nav_more)
    val labels = pages.map { stringResource(it.label) } + more
    val running = stringResource(R.string.nav_timer_running)
    Column(modifier) {
        HorizontalDivider()
        NavigationBar {
            for ((page, label) in pages.zip(labels)) {
                val selected = page == current
                val dot = timerRunning && page == TopLevelPage.TIMER
                TokklNavigationBarItem(
                    selected = selected,
                    onClick = { onOpen(page) },
                    icon = {
                        val icon = @Composable { Icon(painterResource(page.icon), contentDescription = null) }
                        if (dot) BadgedBox(badge = { RunningDot(selected) }, content = { icon() }) else icon()
                    },
                    label = label,
                    barLabels = labels,
                    modifier = if (dot) Modifier.semantics { stateDescription = running } else Modifier,
                )
            }
            TokklNavigationBarItem(
                selected = current.inMore,
                onClick = { moreOpen = true },
                icon = { Icon(painterResource(R.drawable.ic_nav_more), contentDescription = null) },
                label = more,
                barLabels = labels,
            )
        }
    }
    if (moreOpen) {
        MoreSheet(current = current, onOpen = onOpen, onClose = { moreOpen = false })
    }
}

/**
 * The running dot, in the running colour, or in on-primary on the selected item's primary
 * indicator, where the running colour would not read, as `.nav-link.active .running-dot`.
 */
@Composable
private fun RunningDot(selected: Boolean) {
    Badge(containerColor = if (selected) MaterialTheme.colorScheme.onPrimary else TokklTheme.extraColors.running)
}

/**
 * The web app's More sheet: a heading, a close button and the pages that do not fit in the bar.
 * Choosing a page opens it and closes the sheet. Back, a tap outside and a swipe down close it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(current: TopLevelPage, onOpen: (TopLevelPage) -> Unit, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onClose() }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, dragHandle = null) {
        SheetWindowEdgeToEdge()
        Column(
            modifier = Modifier.padding(
                start = TokklTheme.spacing.space2,
                end = TokklTheme.spacing.space2,
                top = TokklTheme.spacing.space2,
                bottom = TokklTheme.spacing.space4,
            ),
        ) {
            // The heading starts where the items' icons do.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = TokklTheme.spacing.space4)) {
                Text(
                    text = stringResource(R.string.nav_more),
                    style = TokklTheme.sectionHeadingTextStyle,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = hide) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.nav_more_close))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
            ) {
                for (page in TopLevelPage.entries.filter { it.inMore }) {
                    val selected = page == current
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = stringResource(page.label),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = navigationBarLabelWeight(selected),
                            )
                        },
                        icon = { Icon(painterResource(page.icon), contentDescription = null) },
                        selected = selected,
                        onClick = {
                            onOpen(page)
                            hide()
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = TokklNavigationBarDefaults.moreItemColors(),
                    )
                }
            }
        }
    }
}

/**
 * Gives the sheet's window the edge-to-edge setup of MainActivity's. ModalBottomSheet opens in a
 * window of its own, and that window keeps Android's navigation bar contrast enforcement, which
 * paints a translucent band over a three-button navigation bar: near white under the light sheet,
 * a lighter tint under the dark one. With it off, the sheet's own colour shows behind the
 * navigation bar, as every screen's does. The sheet's content still keeps above the bar.
 */
@Composable
private fun SheetWindowEdgeToEdge() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        window?.let(WindowCompat::enableEdgeToEdge)
        onDispose {}
    }
}
