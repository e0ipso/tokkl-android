package com.github.e0ipso.tokkl.navhost

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SectionHue
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import com.github.e0ipso.tokkl.ui.theme.TokklNavigationBarDefaults
import com.github.e0ipso.tokkl.ui.theme.TokklNavigationBarItem
import com.github.e0ipso.tokkl.ui.theme.TokklNavigationRailItem
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.navigationBarLabelWeight
import kotlinx.coroutines.launch
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.core.timerstate.R as TimerStateR
import com.github.e0ipso.tokkl.core.ui.R as UiR
import com.github.e0ipso.tokkl.feature.clients.R as ClientsR
import com.github.e0ipso.tokkl.feature.pomodoro.R as PomodoroR
import com.github.e0ipso.tokkl.feature.projects.R as ProjectsR
import com.github.e0ipso.tokkl.feature.reports.R as ReportsR
import com.github.e0ipso.tokkl.feature.settings.R as SettingsR
import com.github.e0ipso.tokkl.feature.tags.R as TagsR
import com.github.e0ipso.tokkl.feature.timer.R as TimerR
import com.github.e0ipso.tokkl.feature.trash.R as TrashR

/** The page's label in the bar, the rail and the More sheet. */
@get:StringRes
val TopLevelPage.label: Int
    get() = when (this) {
        TopLevelPage.TIMER -> TimerR.string.nav_timer
        TopLevelPage.POMODORO -> SharedR.string.pomodoro_title
        TopLevelPage.REPORTS -> ReportsR.string.reports
        TopLevelPage.PROJECTS -> ProjectsR.string.nav_projects
        TopLevelPage.TEMPLATES -> TimerR.string.nav_templates
        TopLevelPage.CLIENTS -> ClientsR.string.nav_clients
        TopLevelPage.TAGS -> TagsR.string.nav_tags
        TopLevelPage.TRASH -> TrashR.string.nav_trash
        TopLevelPage.SETTINGS -> SettingsR.string.settings
    }

/** The page's icon in the bar, the rail and the More sheet. */
@get:DrawableRes
val TopLevelPage.icon: Int
    get() = when (this) {
        TopLevelPage.TIMER -> TimerStateR.drawable.ic_nav_timer
        TopLevelPage.POMODORO -> PomodoroR.drawable.ic_nav_pomodoro
        TopLevelPage.REPORTS -> R.drawable.ic_nav_reports
        TopLevelPage.PROJECTS -> SharedR.drawable.ic_nav_projects
        TopLevelPage.TEMPLATES -> TimerR.drawable.ic_nav_templates
        TopLevelPage.CLIENTS -> R.drawable.ic_nav_clients
        TopLevelPage.TAGS -> SharedR.drawable.ic_nav_tags
        TopLevelPage.TRASH -> UiR.drawable.ic_nav_trash
        TopLevelPage.SETTINGS -> R.drawable.ic_nav_settings
    }

/**
 * The page's section hue, as NAV in App.tsx gives each item a section since tokkl d012228. The
 * pages on the More sheet take More's, except Reports, which keeps its own.
 */
val TopLevelPage.hue: SectionHue
    get() = when (this) {
        TopLevelPage.TIMER, TopLevelPage.POMODORO -> SectionHues.timer
        TopLevelPage.REPORTS -> SectionHues.reports
        TopLevelPage.PROJECTS -> SectionHues.projects
        TopLevelPage.TEMPLATES, TopLevelPage.CLIENTS, TopLevelPage.TAGS, TopLevelPage.TRASH -> SectionHues.more
        TopLevelPage.SETTINGS -> SectionHues.settings
    }

/**
 * The bottom bar of a signed-in screen on a compact window: Timer, Pomodoro, Projects, Settings and
 * More, with [current] marked. More opens a sheet with the other pages, and is marked while one of
 * them shows. [onOpen] opens a page. A 1dp line in the border colour sits above the bar, as the
 * web app draws it. Wider windows get [AppNavigationRail] with the same items.
 *
 * While [timerRunning] is true, because a time entry runs, the Timer item carries a dot, as the
 * web app's `.running-dot`, and TalkBack reads "Timer running" as its state, so the dot is not
 * the only sign. [pomodoroRunning] gives Pomodoro the same mark and its own spoken state.
 * The pages in [hidden], such as Trash on a server without it, show nowhere.
 */
@Composable
fun AppNavigationBar(
    current: TopLevelPage,
    onOpen: (TopLevelPage) -> Unit,
    modifier: Modifier = Modifier,
    timerRunning: Boolean = false,
    pomodoroRunning: Boolean = false,
    hidden: Set<TopLevelPage> = emptySet(),
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val items = navigationItems(hidden)
    Column(modifier) {
        HorizontalDivider()
        NavigationBar {
            for (item in items) {
                TokklNavigationBarItem(
                    selected = item.selected(current),
                    onClick = { if (item.page == null) moreOpen = true else onOpen(item.page) },
                    icon = { ItemIcon(item, current, timerRunning, pomodoroRunning) },
                    label = item.label,
                    hue = item.hue,
                    barLabels = items.map { it.label },
                    modifier = Modifier.runningState(item, timerRunning, pomodoroRunning),
                )
            }
        }
    }
    if (moreOpen) MoreSheet(current, hidden, onOpen, onClose = { moreOpen = false })
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
    current: TopLevelPage,
    onOpen: (TopLevelPage) -> Unit,
    modifier: Modifier = Modifier,
    timerRunning: Boolean = false,
    pomodoroRunning: Boolean = false,
    hidden: Set<TopLevelPage> = emptySet(),
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val items = navigationItems(hidden)
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
                        selected = item.selected(current),
                        onClick = { if (item.page == null) moreOpen = true else onOpen(item.page) },
                        icon = { ItemIcon(item, current, timerRunning, pomodoroRunning) },
                        label = item.label,
                        hue = item.hue,
                        railLabels = items.map { it.label },
                        modifier = Modifier.runningState(item, timerRunning, pomodoroRunning),
                    )
                }
            }
        }
        VerticalDivider()
    }
    if (moreOpen) MoreSheet(current, hidden, onOpen, onClose = { moreOpen = false })
}

/** An item of the bar or the rail: a [page], or More when [page] is null. */
private class NavigationItem(
    val page: TopLevelPage?,
    val label: String,
    @param:DrawableRes val icon: Int,
    val runningLabel: String,
) {
    /** The hue of the item's indicator: its page's, or for More the hue of the pages on the sheet. */
    val hue: SectionHue get() = page?.hue ?: SectionHues.more

    /** Whether the item marks [current]: its own page, or for More a page on the sheet. */
    fun selected(current: TopLevelPage): Boolean = if (page == null) current.inMore else page == current

    /** Whether the item carries the running dot. */
    fun dot(timerRunning: Boolean, pomodoroRunning: Boolean): Boolean = when (page) {
        TopLevelPage.TIMER -> timerRunning
        TopLevelPage.POMODORO -> pomodoroRunning
        else -> false
    }
}

/** TalkBack's running state on [item], while it carries the dot. */
private fun Modifier.runningState(item: NavigationItem, timerRunning: Boolean, pomodoroRunning: Boolean): Modifier =
    if (item.dot(timerRunning, pomodoroRunning)) semantics { stateDescription = item.runningLabel } else this

/** The pages outside More, less those in [hidden], then More. */
@Composable
private fun navigationItems(hidden: Set<TopLevelPage>): List<NavigationItem> {
    val running = stringResource(R.string.nav_timer_running)
    val pomodoroRunning = stringResource(R.string.nav_pomodoro_running)
    val pages = TopLevelPage.entries.filterNot { it.inMore || it in hidden }
        .map {
            NavigationItem(
                it,
                stringResource(it.label),
                it.icon,
                if (it == TopLevelPage.POMODORO) pomodoroRunning else running,
            )
        }
    return pages + NavigationItem(null, stringResource(R.string.nav_more), TimerR.drawable.ic_nav_more, running)
}

/** The [item]'s icon, with its own running dot for Timer or Pomodoro. */
@Composable
private fun ItemIcon(item: NavigationItem, current: TopLevelPage, timerRunning: Boolean, pomodoroRunning: Boolean) {
    val icon = @Composable { Icon(painterResource(item.icon), contentDescription = null) }
    if (item.dot(
            timerRunning,
            pomodoroRunning,
        )
    ) {
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
 * The web app's More sheet: a heading, a close button and the pages that do not fit in the bar,
 * less those in [hidden].
 * Choosing a page opens it and closes the sheet. Back, a tap outside and a swipe down close it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    current: TopLevelPage,
    hidden: Set<TopLevelPage>,
    onOpen: (TopLevelPage) -> Unit,
    onClose: () -> Unit,
) {
    val pages = TopLevelPage.entries.filter { it.inMore && it !in hidden }
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
                for (page in pages) {
                    val selected = page == current
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
                            onOpen(page)
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
