package com.github.e0ipso.tokkl.reports

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.text.NumberFormat

/**
 * The summary of a range with time in it, as the web app's SummaryTab: the totals, the time per
 * day, week or month, and the time per project. Durations follow the account's format.
 */
@Composable
internal fun SummaryContent(report: SummaryReport, settings: AccountSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups)) {
        Totals(report.totals, settings)
        SeriesChart(report, settings)
        ProjectBreakdown(report, settings)
    }
}

/** A card in Tokkl's frame: surface, the large corners and a 2dp outline. */
@Composable
internal fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(FrameWidth, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(TokklTheme.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
            content = content,
        )
    }
}

@Composable
internal fun CardHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/**
 * Total time, billable time with its share, the number of entries and the amount in each
 * currency. Each total wraps onto its own line when the next one does not fit beside it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Totals(totals: ReportTotals, settings: AccountSettings) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    ReportCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space6),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4),
        ) {
            Total(stringResource(R.string.reports_total_time)) {
                DurationText(totals.seconds, settings, resources)
            }
            Total(stringResource(R.string.reports_billable_time)) {
                DurationText(totals.billableSeconds, settings, resources)
                if (totals.seconds > 0) {
                    Text(
                        text = stringResource(R.string.reports_share_of_total, sharePercent(totals.billableSeconds, totals.seconds)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Total(stringResource(R.string.reports_entries)) {
                Text(NumberFormat.getIntegerInstance(locale).format(totals.entryCount), style = TokklTheme.totalTextStyle)
            }
            if (totals.amounts.isNotEmpty()) {
                Total(stringResource(R.string.reports_amount)) {
                    for (amount in totals.amounts) {
                        Text(formatMoney(amount.cents, amount.currency, locale), style = TokklTheme.totalTextStyle)
                    }
                }
            }
        }
    }
}

/** One total, a small-caps label over its value, read by TalkBack as one item. */
@Composable
private fun Total(label: String, value: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = label.uppercase(), style = TokklTheme.smallCapsLabelTextStyle)
        value()
    }
}

/** A duration in the account's format, which TalkBack reads in words. */
@Composable
private fun DurationText(seconds: Long, settings: AccountSettings, resources: Resources) {
    val spoken = spokenDuration(resources, seconds)
    Text(
        text = settings.formatDuration(seconds),
        style = TokklTheme.totalTextStyle,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

/**
 * The time per bucket as horizontal bars, one row each: the bucket and its total as text above
 * a bar. Billable time sits at the start as a solid bar and the rest follows in stripes, as in
 * the web app's chart, so the two differ by texture as well as colour. TalkBack reads the whole
 * chart as one description that names every bucket's total.
 */
@Composable
private fun SeriesChart(report: SummaryReport, settings: AccountSettings) {
    val resources = LocalResources.current
    val words = DateWords(resources)
    val colours = TokklTheme.extraColors
    val longest = report.series.maxOfOrNull { it.seconds }?.takeIf { it > 0 } ?: 1L
    val description = chartDescription(report, resources, words)
    ReportCard {
        CardHeading(stringResource(CHART_TITLES.getValue(report.resolution)))
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        ) {
            ChartLegend()
            for (point in report.series) {
                Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = words.bucket(point.start, report.resolution),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(TokklTheme.spacing.space2))
                        Text(
                            text = settings.formatDuration(point.seconds),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.End,
                        )
                    }
                    Bar(point, longest, colours)
                }
            }
        }
    }
}

/**
 * "Bar chart of time per day, 2 hours in total." then each bucket and its total, in words. The
 * total adds up the bars, as the web chart's description does.
 */
private fun chartDescription(report: SummaryReport, resources: Resources, words: DateWords): String {
    val bars = report.series.joinToString(". ") { point ->
        resources.getString(R.string.reports_chart_bar_description, words.bucket(point.start, report.resolution), spokenDuration(resources, point.seconds))
    }
    val total = spokenDuration(resources, report.series.sumOf { it.seconds })
    return resources.getString(CHART_DESCRIPTIONS.getValue(report.resolution), total, bars)
}

@Composable
private fun ChartLegend() {
    val colours = TokklTheme.extraColors
    Row(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4)) {
        LegendKey(stringResource(R.string.reports_billable)) { drawRect(colours.chartBillable) }
        LegendKey(stringResource(R.string.reports_non_billable)) { stripes(colours.chartOther, colours.chartOtherWash) }
    }
}

@Composable
private fun LegendKey(label: String, swatch: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            onDraw = swatch,
        )
        Spacer(Modifier.width(TokklTheme.spacing.space1))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A point's bar, as long as its share of the [longest] bucket, on a track as wide as the chart.
 * The track is Tokkl's chart band, the colour behind the web chart's focused column. Both bar
 * colours keep 3:1 against it in light and dark, which surface-2 does not give in light.
 */
@Composable
private fun Bar(point: SeriesPoint, longest: Long, colours: TokklExtraColors) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(MaterialTheme.shapes.extraSmall),
    ) {
        drawRect(colours.chartBand)
        val billableWidth = size.width * point.billableSeconds / longest
        val otherSeconds = point.seconds - point.billableSeconds
        val gap = if (point.billableSeconds > 0 && otherSeconds > 0) SEGMENT_GAP.toPx() else 0f
        val otherWidth = (size.width * otherSeconds / longest - gap).coerceAtLeast(0f)
        if (billableWidth > 0) drawRect(colours.chartBillable, size = Size(billableWidth, size.height))
        if (otherWidth > 0) {
            val left = billableWidth + gap
            clipRect(left = left, right = left + otherWidth) { stripes(colours.chartOther, colours.chartOtherWash) }
        }
    }
}

/** Diagonal stripes of [line] on [wash], as the web chart's pattern for non-billable time. */
private fun DrawScope.stripes(line: Color, wash: Color) {
    drawRect(wash)
    val step = STRIPE_STEP.toPx()
    val width = STRIPE_WIDTH.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(line, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width)
        x += step
    }
}

/**
 * The time per project, most first: the project, its time, its share of the total and, when
 * the report has one currency, its amount. Each row is one TalkBack item.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectBreakdown(report: SummaryReport, settings: AccountSettings) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val currency = report.currency
    val colours = TokklTheme.extraColors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ReportCard {
        CardHeading(stringResource(R.string.reports_projects))
        if (report.totals.amounts.size > 1) {
            Text(stringResource(R.string.reports_mixed_currencies), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        for (project in report.projects) {
            val spoken = spokenDuration(resources, project.seconds)
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dot = projectMark(project.color, colours.projectMix, muted)
                    Canvas(Modifier.size(DOT_SIZE)) { drawCircle(dot) }
                    Spacer(Modifier.width(TokklTheme.spacing.space2))
                    val unnamed = project.title.isBlank()
                    Text(
                        text = if (unnamed) stringResource(R.string.reports_without_project) else project.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (unnamed) muted else MaterialTheme.colorScheme.onSurface,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4)) {
                    Text(
                        text = settings.formatDuration(project.seconds),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.semantics { contentDescription = spoken },
                    )
                    Text(
                        text = stringResource(R.string.reports_percent, sharePercent(project.seconds, report.totals.seconds)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = muted,
                    )
                    if (currency != null && project.amountCents != 0L) {
                        Text(
                            text = formatMoney(project.amountCents, currency, locale),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
                ShareBar(project.seconds, report.totals.seconds)
            }
        }
    }
}

/** The project's share as a bar, drawn only; the percentage beside it says the same in text. */
@Composable
private fun ShareBar(part: Long, whole: Long) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(SHARE_HEIGHT)
            .clip(CircleShape),
    ) {
        drawRect(track)
        if (whole > 0) drawRect(fill, size = Size(size.width * (part.toFloat() / whole).coerceIn(0f, 1f), size.height))
    }
}

/**
 * A project's dot colour: its hex mixed 60% with [mix] in sRGB, as styles.css does, so it shows
 * against the card. A project without a colour, or with one that is not `#rrggbb`, takes [fallback].
 */
internal fun projectMark(hex: String?, mix: Color, fallback: Color): Color {
    val colour = hex?.takeIf { HEX.matches(it) }?.let { Color(0xFF000000 or it.substring(1).toLong(16)) } ?: fallback
    return Color(
        red = colour.red * 0.6f + mix.red * 0.4f,
        green = colour.green * 0.6f + mix.green * 0.4f,
        blue = colour.blue * 0.6f + mix.blue * 0.4f,
    )
}

private val HEX = Regex("#[0-9a-fA-F]{6}")

private val BAR_HEIGHT = 12.dp
private val SEGMENT_GAP = 2.dp
private val STRIPE_STEP = 6.dp
private val STRIPE_WIDTH = 3.dp
private val DOT_SIZE = 12.dp
private val SHARE_HEIGHT = 6.dp

private val CHART_TITLES = mapOf(
    Resolution.DAY to R.string.reports_chart_title_day,
    Resolution.WEEK to R.string.reports_chart_title_week,
    Resolution.MONTH to R.string.reports_chart_title_month,
)

private val CHART_DESCRIPTIONS = mapOf(
    Resolution.DAY to R.string.reports_chart_description_day,
    Resolution.WEEK to R.string.reports_chart_description_week,
    Resolution.MONTH to R.string.reports_chart_description_month,
)
