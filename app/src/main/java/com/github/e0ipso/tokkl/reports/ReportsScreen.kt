package com.github.e0ipso.tokkl.reports

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * The reports screen (docs/PRD.md R1 to R5): a tab per report, the summary, the detailed list and
 * the weekly grid, under the range and the filters they share. On the weekly tab the range is
 * always a week from the account's week start, and the range button offers only the week presets.
 * Opening the screen asks the server for this week, and coming back to the foreground asks again
 * for the range on screen; leaving it forgets the report, while a configuration change keeps it
 * and asks nothing. Offline, it says that reports need the server and shows no numbers or entries,
 * and once the network comes back with the app on screen it asks again.
 * It is a page of the bottom bar, so it has no back button; system Back goes to Timer.
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
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.reports_share)
    val exportState by model.exportState.collectAsState()
    val capabilities by model.exportCapabilities.collectAsState()
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The chooser may return RESULT_CANCELED even after a selection. Files stay bounded
        // and readable for receivers that open their stream after the chooser has closed.
        model.exportShareFinished()
    }
    LaunchedEffect(exportState, lifecycleState) {
        if (exportState is ReportExportState.Ready && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            model.launchExportShare { file ->
                try {
                    val send = ReportCsvShare.intent(context, file)
                    if (ReportCsvShare.hasTarget(context, send)) {
                        chooser.launch(Intent.createChooser(send, shareTitle))
                    } else {
                        model.exportNoShareTarget()
                    }
                } catch (e: ActivityNotFoundException) {
                    model.exportNoShareTarget()
                } catch (e: IllegalArgumentException) {
                    model.exportShareFailed()
                } catch (e: SecurityException) {
                    model.exportShareFailed()
                }
            }
        }
    }
    val state by model.state.collectAsState()
    val tab by model.tab.collectAsState()
    val settings by model.accountSettings.collectAsState()
    val time = remember(settings) { model.accountTime() }
    val filters by model.filters.collectAsState()
    val filterOptions by model.filterOptions.collectAsState()
    ReportsContent(
        state = state,
        tab = tab,
        settings = settings,
        time = time,
        filters = filters,
        filterOptions = filterOptions,
        onTab = model::selectTab,
        onShift = model::shift,
        onPreset = model::select,
        onCustom = model::selectCustom,
        onFilters = model::applyFilters,
        onRetry = model::retry,
        onLoadMore = model::loadMore,
        exportSupported = ReportCsvKind.of(tab)?.let(capabilities::supports) == true,
        exportState = exportState,
        onExport = model::exportCsv,
        modifier = modifier,
    )
}

/**
 * [ReportsScreen] without its model. [time] is null when the phone cannot resolve the account's
 * timezone. [filterOptions] are what the filter sheet offers. A detailed report with entries
 * scrolls as a lazy list, so a long range composes only the rows in view; everything else scrolls
 * as one column.
 */
@Composable
internal fun ReportsContent(
    state: ReportsState,
    tab: ReportTab,
    settings: AccountSettings?,
    time: AccountTime?,
    filters: ReportFilters,
    filterOptions: FilterOptions,
    onTab: (ReportTab) -> Unit,
    onShift: (Int) -> Unit,
    onPreset: (RangePreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    onFilters: (ReportFilters) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    exportSupported: Boolean = false,
    exportState: ReportExportState = ReportExportState.Idle,
    onExport: () -> Unit = {},
) {
    val header: @Composable () -> Unit = {
        Header(state, tab, time, onTab, onShift, onPreset, onCustom)
        FilterControl(filters, filterOptions, onFilters)
        if (exportSupported && tab != ReportTab.WEEKLY) {
            OutlinedButton(
                onClick = onExport,
                enabled = state is ReportsState.Loaded && time != null && settings != null &&
                    (exportState == ReportExportState.Idle || exportState is ReportExportState.Failed),
            ) { Text(stringResource(R.string.reports_share)) }
        }
        when (exportState) {
            ReportExportState.Preparing -> Loading(stringResource(R.string.reports_export_preparing))
            is ReportExportState.Failed -> when {
                exportState.noTargets -> ErrorBanner(stringResource(R.string.reports_export_no_targets))
                exportState.error != null -> Failure(reportFailureOf(exportState.error), onExport)
                else -> ErrorBanner(stringResource(R.string.reports_export_failed))
            }
            else -> Unit
        }
        Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
    }
    val detailed = (state as? ReportsState.Loaded)?.report as? DetailedReport
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(stringResource(R.string.reports)) },
        contentWindowInsets = ScrollingScreenInsets,
    ) { innerPadding ->
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
                    .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
            ) {
                header()
                Body(state, settings, filtered = !filters.isEmpty, onRetry = onRetry)
            }
        }
    }
}

/** The tabs and the range, above the filters and every tab's report. */
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
    ReportTabs(tab, onTab)
    Spacer(Modifier.height(TokklTheme.spacing.space4))
    RangeControl(
        selection = state.selection(),
        time = time,
        onShift = onShift,
        onPreset = onPreset,
        onCustom = onCustom,
        presets = if (tab == ReportTab.WEEKLY) WEEK_PRESETS else RangePreset.entries,
    )
    Spacer(Modifier.height(TokklTheme.spacing.inGroup))
}

/**
 * The report types, in the web app's order. The tabs not shown are muted, as in the web app, and
 * the indicator under the one shown marks it by shape too. The tabs share the width equally while
 * every label fits its share on one line; at larger font scales the row scrolls sideways instead,
 * so no label breaks inside a word.
 */
@Composable
private fun ReportTabs(selected: ReportTab, onTab: (ReportTab) -> Unit) {
    val labels = ReportTab.entries.map { stringResource(TAB_LABELS.getValue(it)) }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    val tabs: @Composable () -> Unit = {
        for (tab in ReportTab.entries) {
            Tab(
                selected = tab == selected,
                onClick = { onTab(tab) },
                text = { Text(labels[tab.ordinal], style = style) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    BoxWithConstraints {
        val share = maxWidth / labels.size
        val fit = labels.all { label ->
            val width = with(density) { measurer.measure(label, style, softWrap = false, maxLines = 1).size.width.toDp() }
            // Material pads a tab's text by 16dp on each side, the spacing scale's space4.
            width + TokklTheme.spacing.space4 * 2 <= share
        }
        val background = MaterialTheme.colorScheme.background
        if (fit) {
            PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = background, tabs = tabs)
        } else {
            PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal, containerColor = background, edgePadding = 0.dp, tabs = tabs)
        }
    }
}

private val TAB_LABELS = mapOf(
    ReportTab.SUMMARY to R.string.reports_tab_summary,
    ReportTab.DETAILED to R.string.reports_tab_detailed,
    ReportTab.WEEKLY to R.string.reports_tab_weekly,
)

/** The presets that name a whole week, the only ranges the weekly tab shows. */
private val WEEK_PRESETS = listOf(RangePreset.THIS_WEEK, RangePreset.LAST_WEEK)

/** What the tab on screen shows under the header. [filtered] is true while any filter is active. */
@Composable
private fun Body(state: ReportsState, settings: AccountSettings?, filtered: Boolean, onRetry: () -> Unit) {
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
                report.totals.entryCount == 0 && report.totals.seconds == 0L &&
                    report.totals.amounts.isEmpty() && report.projects.isEmpty() -> Column {
                    Empty(filtered)
                    AdditionalReportData(report)
                }
                else -> SummaryContent(report, settings)
            }
            // A detailed report with entries shows as a list, once the settings and the zone are known.
            is DetailedReport -> if (report.entries.isEmpty()) Empty(filtered) else Loading()
            is WeeklyReport -> when {
                settings == null -> Loading()
                report.rows.isEmpty() -> Empty(filtered)
                else -> WeeklyContent(report, settings)
            }
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

/**
 * A range without time: what to do instead, as the web app's empty state, which names the filters
 * when [filtered].
 */
@Composable
private fun Empty(filtered: Boolean) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
    ) {
        Text(
            text = stringResource(if (filtered) R.string.reports_empty_title_filtered else R.string.reports_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(if (filtered) R.string.reports_empty_message_filtered else R.string.reports_empty_message),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
