package com.github.e0ipso.tokkl.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 */
@Composable
fun AppNavigationBar(current: TopLevelPage, onOpen: (TopLevelPage) -> Unit, modifier: Modifier = Modifier) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val pages = TopLevelPage.entries.filterNot { it.inMore }
    val more = stringResource(R.string.nav_more)
    val labels = pages.map { stringResource(it.label) } + more
    Column(modifier) {
        HorizontalDivider()
        NavigationBar {
            for ((page, label) in pages.zip(labels)) {
                TokklNavigationBarItem(
                    selected = page == current,
                    onClick = { onOpen(page) },
                    icon = { Icon(painterResource(page.icon), contentDescription = null) },
                    label = label,
                    barLabels = labels,
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
