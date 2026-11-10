package com.github.e0ipso.tokkl.reports

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * The reports screen (docs/PRD.md R1 to R5). On the weekly tab the range is always a week from the
 * account's week start, and the range button offers only the week presets. A configuration change
 * keeps the report and asks nothing. It is a page of the bottom bar, so it has no back button;
 * system Back goes to Timer.
 *
 * [time] is null when the phone cannot resolve the account's timezone. A detailed report with
 * entries scrolls as a lazy list, so a long range composes only the rows in view; everything else
 * scrolls as one column.
 */
@Composable
fun ReportsScreen(
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
        val rows = detailed?.takeIf { it.entries.isNotEmpty() }
        if (rows != null && settings != null && time != null) {
            DetailedContent(
                report = rows,
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
            val width = with(
                density,
            ) { measurer.measure(label, style, softWrap = false, maxLines = 1).size.width.toDp() }
            // Material pads a tab's text by 16dp on each side, the spacing scale's space4.
            width + TokklTheme.spacing.space4 * 2 <= share
        }
        val background = MaterialTheme.colorScheme.background
        if (fit) {
            PrimaryTabRow(selectedTabIndex = selected.ordinal, containerColor = background, tabs = tabs)
        } else {
            PrimaryScrollableTabRow(
                selectedTabIndex = selected.ordinal,
                containerColor = background,
                edgePadding = 0.dp,
                tabs = tabs,
            )
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
