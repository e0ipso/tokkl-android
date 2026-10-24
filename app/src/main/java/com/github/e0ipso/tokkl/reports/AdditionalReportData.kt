package com.github.e0ipso.tokkl.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** Optional additions stay associated with their owner, separate from formatted known values. */
@Composable
internal fun AdditionalReportData(report: SummaryReport) {
    val hasData = report.additionalData.isNotEmpty() || report.totals.additionalData.isNotEmpty() ||
        report.totals.amounts.any { it.additionalData.isNotEmpty() } ||
        report.projects.any { it.additionalData.isNotEmpty() } || report.series.any { it.additionalData.isNotEmpty() }
    if (!hasData && !report.additionalDataTruncated) return
    ReportCard {
        CardHeading(stringResource(R.string.reports_additional_data))
        ExtraSection(stringResource(R.string.reports_extra_report), report.additionalData)
        ExtraSection(stringResource(R.string.reports_extra_totals), report.totals.additionalData)
        for (amount in report.totals.amounts) {
            ExtraSection(stringResource(R.string.reports_extra_currency, amount.currency), amount.additionalData)
        }
        for (project in report.projects) {
            val title = project.title.ifBlank { stringResource(R.string.reports_without_project) }
            ExtraSection(stringResource(R.string.reports_extra_project, title), project.additionalData)
        }
        for (point in report.series) {
            ExtraSection(stringResource(R.string.reports_extra_bucket, point.start.toString()), point.additionalData)
        }
        if (report.additionalDataTruncated) Text(stringResource(R.string.reports_extra_truncated), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExtraSection(title: String, fields: List<ReportExtraField>) {
    if (fields.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        for (field in fields) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(field.name, style = MaterialTheme.typography.labelLarge)
                Text(field.value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
