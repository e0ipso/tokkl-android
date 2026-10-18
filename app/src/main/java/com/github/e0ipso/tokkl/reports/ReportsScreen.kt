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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * The reports screen (docs/PRD.md R1 and R5): a tab per report, so far the summary, under the
 * range they share. Opening the screen asks the server for this week; leaving it forgets the
 * report, while a configuration change keeps it. Offline, it says that reports need the server
 * and shows no numbers. The detailed (TKA-0034) and weekly (TKA-0035) reports add their tabs here.
 */
@Composable
fun ReportsScreen(model: ReportsModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    DisposableEffect(model) {
        model.open()
        onDispose { if (activity?.isChangingConfigurations != true) model.close() }
    }
    val state by model.state.collectAsState()
    val settings by model.accountSettings.collectAsState()
    val time = remember(settings) { model.accountTime() }
    ReportsContent(
        state = state,
        settings = settings,
        time = time,
        onBack = onBack,
        onShift = model::shift,
        onPreset = model::select,
        onCustom = model::selectCustom,
        onRetry = model::retry,
        modifier = modifier,
    )
}

/** [ReportsScreen] without its model. [time] is null when the phone cannot resolve the account's timezone. */
@Composable
internal fun ReportsContent(
    state: ReportsState,
    settings: AccountSettings?,
    time: AccountTime?,
    onBack: () -> Unit,
    onShift: (Int) -> Unit,
    onPreset: (RangePreset) -> Unit,
    onCustom: (ReportRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = stringResource(R.string.reports),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
            ReportTabs()
            Spacer(Modifier.height(TokklTheme.spacing.space4))
            RangeControl(
                selection = state.selection(),
                time = time,
                onShift = onShift,
                onPreset = onPreset,
                onCustom = onCustom,
            )
            Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            Body(state, settings, onRetry)
        }
    }
}

/** The report types. Only the summary exists so far. */
@Composable
private fun ReportTabs() {
    PrimaryTabRow(selectedTabIndex = 0, containerColor = MaterialTheme.colorScheme.background) {
        Tab(selected = true, onClick = {}, text = { Text(stringResource(R.string.reports_tab_summary)) })
    }
}

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
        is ReportsState.Loaded -> when {
            settings == null -> Loading()
            state.report.totals.entryCount == 0 -> Empty()
            else -> SummaryContent(state.report, settings)
        }
    }
}

@Composable
private fun Loading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Text(stringResource(R.string.reports_loading), style = MaterialTheme.typography.bodyLarge)
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
