package com.github.e0ipso.tokkl.reports

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * The reports screen (docs/PRD.md R1, R2 and R5): a tab per report, the summary and the detailed
 * list, under the range they share. Opening the screen asks the server for this week, and coming
 * back to the foreground asks again for the range on screen; leaving it forgets the report, while
 * a configuration change keeps it and asks nothing. Offline, it says that reports need the server
 * and shows no numbers or entries. The weekly report (TKA-0035) adds its tab here. It is a page
 * of the bottom bar, so it has no back button; system Back goes to Timer.
 */
@Composable
fun ReportsScreen(model: ReportsModel, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model) {
        model.open()
        onDispose { if (activity?.isChangingConfigurations != true) model.close() }
    }
    DisposableEffect(model, lifecycle) {
        // Adding the observer replays ON_START at once, which the model ignores until a stop.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) model.wentToBackground()
                Lifecycle.Event.ON_START -> model.cameToForeground()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val state by model.state.collectAsState()
    val tab by model.tab.collectAsState()
    val settings by model.accountSettings.collectAsState()
    val time = remember(settings) { model.accountTime() }
    ReportsContent(
        state = state,
        tab = tab,
        settings = settings,
        time = time,
        onTab = model::selectTab,
        onShift = model::shift,
        onPreset = model::select,
        onCustom = model::selectCustom,
        onRetry = model::retry,
        onLoadMore = model::loadMore,
        modifier = modifier,
    )
}

/**
 * [ReportsScreen] without its model. [time] is null when the phone cannot resolve the account's
 * timezone. A detailed report with entries scrolls as a lazy list, so a long range composes only
 * the rows in view; everything else scrolls as one column.
 */
@Composable
internal fun ReportsContent(
    state: ReportsState,
    tab: ReportTab,
    settings: AccountSettings?,
    time: AccountTime?,
    onTab: (ReportTab) -> Unit,
    onShift: (Int) -> Unit,
    onPreset: (RangePreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val header: @Composable () -> Unit = {
        Header(state, tab, time, onTab, onShift, onPreset, onCustom)
    }
    val detailed = (state as? ReportsState.Loaded)?.report as? DetailedReport
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
        if (detailed != null && detailed.entries.isNotEmpty() && settings != null && time != null) {
            DetailedContent(
                report = detailed,
                settings = settings,
                today = time.today(),
                header = header,
                onLoadMore = onLoadMore,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .screenContent()
                    .padding(vertical = TokklTheme.spacing.space2),
            ) {
                header()
                Body(state, settings, onRetry)
            }
        }
    }
}

/** The page title, the tabs and the range, above every tab's report. */
@Composable
private fun Header(
    state: ReportsState,
    tab: ReportTab,
    time: AccountTime?,
    onTab: (ReportTab) -> Unit,
    onShift: (Int) -> Unit,
    onPreset: (RangePreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
) {
    PageTitle(stringResource(R.string.reports))
    Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
    ReportTabs(tab, onTab)
    Spacer(Modifier.height(TokklTheme.spacing.space4))
    RangeControl(
        selection = state.selection(),
        time = time,
        onShift = onShift,
        onPreset = onPreset,
        onCustom = onCustom,
    )
    Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
}

/**
 * The report types, in the web app's order. The tabs not shown are muted, as in the web app, and
 * the indicator under the one shown marks it by shape too.
 */
@Composable
private fun ReportTabs(selected: ReportTab, onTab: (ReportTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = MaterialTheme.colorScheme.background) {
        for (tab in ReportTab.entries) {
            Tab(
                selected = tab == selected,
                onClick = { onTab(tab) },
                text = { Text(stringResource(TAB_LABELS.getValue(tab))) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TAB_LABELS = mapOf(
    ReportTab.SUMMARY to R.string.reports_tab_summary,
    ReportTab.DETAILED to R.string.reports_tab_detailed,
)

@Composable
private fun Body(state: ReportsState, settings: AccountSettings?, onRetry: () -> Unit) {
    when (state) {
        ReportsState.Closed, is ReportsState.Loading -> Loading()
        is ReportsState.NoAccountTime -> Problem(
            title = null,
            message = if (state.timeZone == null) {
                stringResource(R.string.reports_no_account_settings)
            } else {
                stringResource(R.string.reports_unknown_time_zone, state.timeZone)
            },
            onRetry = onRetry,
        )
        is ReportsState.Failed -> Failure(state.failure, onRetry)
        is ReportsState.Loaded -> when (val report = state.report) {
            is SummaryReport -> when {
                settings == null -> Loading()
                report.totals.entryCount == 0 -> Empty()
                else -> SummaryContent(report, settings)
            }
            // A detailed report with entries shows as a list, once the settings and the zone are known.
            is DetailedReport -> if (report.entries.isEmpty()) Empty() else Loading()
        }
    }
}

/** A spinner beside [text], which TalkBack reads when it shows. */
@Composable
internal fun Loading(text: String = stringResource(R.string.reports_loading)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Why there is no report, in words, with no number from any earlier answer. */
@Composable
private fun Failure(failure: ReportFailure, onRetry: () -> Unit) {
    when (failure) {
        ReportFailure.Unreachable -> Problem(
            title = stringResource(R.string.reports_offline_title),
            message = stringResource(R.string.reports_offline_message),
            onRetry = onRetry,
        )
        ReportFailure.AuthFailed -> Problem(title = null, message = stringResource(R.string.reports_error_auth), onRetry = onRetry)
        ReportFailure.TrustRefused -> Problem(title = null, message = stringResource(R.string.reports_error_trust), onRetry = onRetry)
        is ReportFailure.Unexpected -> Problem(
            title = null,
            message = failure.status?.let { stringResource(R.string.reports_error_status, it) }
                ?: stringResource(R.string.reports_error_unreadable),
            onRetry = onRetry,
        )
    }
}

@Composable
private fun Problem(title: String?, message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.reports_retry)) }
    }
}

/** A range without time: what to do instead, as the web app's empty state. */
@Composable
private fun Empty() {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
    ) {
        Text(stringResource(R.string.reports_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reports_empty_message), style = MaterialTheme.typography.bodyLarge)
    }
}
