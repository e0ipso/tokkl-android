package com.github.e0ipso.tokkl.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** What the tab on screen shows under the header. [filtered] is true while any filter is active. */
@Composable
internal fun Body(state: ReportsState, settings: AccountSettings?, filtered: Boolean, onRetry: () -> Unit) {
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
internal fun Failure(failure: ReportFailure, onRetry: () -> Unit) {
    when (failure) {
        ReportFailure.Unreachable -> Problem(
            title = stringResource(R.string.reports_offline_title),
            message = stringResource(R.string.reports_offline_message),
            onRetry = onRetry,
        )

        ReportFailure.AuthFailed -> Problem(
            title = null,
            message = stringResource(R.string.reports_error_auth),
            onRetry = onRetry,
        )

        ReportFailure.TrustRefused -> Problem(
            title = null,
            message = stringResource(R.string.reports_error_trust),
            onRetry = onRetry,
        )

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
            text = stringResource(
                if (filtered) R.string.reports_empty_title_filtered else R.string.reports_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                if (filtered) R.string.reports_empty_message_filtered else R.string.reports_empty_message,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
