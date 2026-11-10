package com.github.e0ipso.tokkl.navhost

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SectionHue
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.launch
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The bottom bar of a signed-in screen on a compact window: the [pages] outside More, which are
 * Timer, Pomodoro, Projects and Settings, then More, with the page of the route [current] marked.
 * More opens a sheet with the other pages, and is marked while one of them shows. A 1dp line in
 * the border colour sits above the bar, as the web app draws it. Wider windows get
 * [AppNavigationRail] with the same items.
 *
 * The item of each page in [badges] carries a dot, as the web app's `.running-dot`, such as Timer
 * while a time entry runs, and TalkBack reads the page's label in [badges] as its state, such as
 * "Timer running", so the dot is not the only sign. The pages whose routes are in [hidden], such
 * as Trash on a server without it, show nowhere.
 */
@Composable
fun AppNavigationBar(
    pages: TopLevelPages,
    current: Route,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Route, Int> = emptyMap(),
    hidden: Set<Route> = emptySet(),
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val items = navigationItems(pages, hidden, badges)
    val currentPage = pages.of(current)
    Column(modifier) {
        HorizontalDivider()
        NavigationBar {
            for (item in items) {
                TokklNavigationBarItem(
                    selected = item.selected(currentPage),
                    onClick = { if (item.page == null) moreOpen = true else onOpen(item.page.route) },
                    icon = { ItemIcon(item, currentPage) },
                    label = item.label,
                    hue = item.hue,
                    barLabels = items.map { it.label },
                    modifier = Modifier.runningState(item),
                )
            }
        }
    }
    if (moreOpen) MoreSheet(pages, current, hidden, onOpen, onClose = { moreOpen = false })
}

/**
 * The navigation rail of a signed-in screen on a medium or expanded window, at the start of the
 * screen: the items of [AppNavigationBar], top to bottom, with the same More sheet, the same
 * marks and the same dot. A 1dp line in the border colour sits at its end, where the bar has its
 * line above it. Where the window is too short for every item, as at the largest font scale on a
 * phone held sideways, the items scroll.
 */
@Composable
fun AppNavigationRail(
    pages: TopLevelPages,
    current: Route,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Route, Int> = emptyMap(),
    hidden: Set<Route> = emptySet(),
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val items = navigationItems(pages, hidden, badges)
    val currentPage = pages.of(current)
    Row(modifier) {
        NavigationRail(containerColor = NavigationBarDefaults.containerColor) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
            ) {
                for (item in items) {
                    TokklNavigationRailItem(
                        selected = item.selected(currentPage),
                        onClick = { if (item.page == null) moreOpen = true else onOpen(item.page.route) },
                        icon = { ItemIcon(item, currentPage) },
                        label = item.label,
                        hue = item.hue,
                        railLabels = items.map { it.label },
                        modifier = Modifier.runningState(item),
                    )
                }
            }
        }
        VerticalDivider()
    }
    if (moreOpen) MoreSheet(pages, current, hidden, onOpen, onClose = { moreOpen = false })
}

/** An item of the bar or the rail: a [page], or More when [page] is null. */
private class NavigationItem(
    val page: TopLevelDestination?,
    val label: String,
    @param:DrawableRes val icon: Int,
    val runningLabel: String?,
) {
    /** The hue of the item's indicator: its page's, or for More the hue of the pages on the sheet. */
    val hue: SectionHue get() = page?.hue ?: SectionHues.more

    /** Whether the item marks [current]: its own page, or for More a page on the sheet. */
    fun selected(current: TopLevelDestination?): Boolean =
        if (page == null) current?.inMore == true else page == current

    val dot: Boolean get() = runningLabel != null
}

private fun Modifier.runningState(item: NavigationItem): Modifier =
    item.runningLabel?.let { label -> semantics { stateDescription = label } } ?: this

@Composable
private fun navigationItems(pages: TopLevelPages, hidden: Set<Route>, badges: Map<Route, Int>): List<NavigationItem> {
    val items = pages.all.filterNot { it.inMore || it.route in hidden }
        .map {
            NavigationItem(
                it,
                stringResource(it.label),
                it.icon,
                badges[it.route]?.let { label -> stringResource(label) },
            )
        }
    return items + NavigationItem(null, stringResource(R.string.nav_more), UiR.drawable.ic_nav_more, null)
}

@Composable
private fun ItemIcon(item: NavigationItem, current: TopLevelDestination?) {
    val icon = @Composable { Icon(painterResource(item.icon), contentDescription = null) }
    if (item.dot) {
        BadgedBox(badge = { RunningDot(item.selected(current), item.hue) }, content = { icon() })
    } else {
        icon()
    }
}

/**
 * The running dot, in the running colour, or on the selected item's indicator in the colour of
 * the icon on [hue], where the running colour would not read, as `.nav-link.active .running-dot`
 * takes currentColor: the running pink is only 1.32:1 on the orange.
 */
@Composable
private fun RunningDot(selected: Boolean, hue: SectionHue) {
    Badge(containerColor = if (selected) hue.onFill else TokklTheme.extraColors.running)
}

/**
 * The web app's More sheet: a heading, a close button and the [pages] that do not fit in the bar,
 * less those whose routes are in [hidden].
 * Choosing a page opens it and closes the sheet. Back, a tap outside and a swipe down close it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    pages: TopLevelPages,
    current: Route,
    hidden: Set<Route>,
    onOpen: (Route) -> Unit,
    onClose: () -> Unit,
) {
    val onSheet = pages.all.filter { it.inMore && it.route !in hidden }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onClose() }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, dragHandle = null) {
        SheetWindowEdgeToEdge()
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(
                start = TokklTheme.spacing.space2,
                end = TokklTheme.spacing.space2,
                top = TokklTheme.spacing.space2,
                bottom = TokklTheme.spacing.space4,
            ),
        ) {
            // The heading starts where the items' icons do.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = TokklTheme.spacing.space4),
            ) {
                Text(
                    text = stringResource(R.string.nav_more),
                    style = TokklTheme.sectionHeadingTextStyle,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = hide) {
                    Icon(
                        painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.nav_more_close),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
            ) {
                for (page in onSheet) {
                    val selected = page.route == current
                    val shape = MaterialTheme.shapes.small
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
                            onOpen(page.route)
                            hide()
                        },
                        // The active item's outline, as the bar's indicator has one.
                        modifier = if (selected) {
                            Modifier.border(
                                FrameWidth,
                                MaterialTheme.colorScheme.outline,
                                shape,
                            )
                        } else {
                            Modifier
                        },
                        shape = shape,
                        colors = TokklNavigationBarDefaults.moreItemColors(page.hue),
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
