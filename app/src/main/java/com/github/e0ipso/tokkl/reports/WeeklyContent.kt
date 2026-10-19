package com.github.e0ipso.tokkl.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The weekly report as the web app's WeeklyTab shows a week: projects as rows, the week's days as
 * columns, and a total for each row, each day and the week. Durations follow the account's
 * format, and a day without time shows a dash, as in the web app.
 *
 * The project column stays put while the days scroll sideways under it, on any screen too narrow
 * for the whole week. Every row scrolls with one shared state, so the columns stay lined up, and
 * each row's height follows its project's name, which wraps instead of being cut. TalkBack reads
 * every cell with its project and day, never a number alone.
 */
@Composable
internal fun WeeklyContent(report: WeeklyReport, settings: AccountSettings) {
    ReportCard {
        CardHeading(stringResource(R.string.reports_weekly_title))
        WeeklyGrid(report, settings)
    }
}

@Composable
private fun WeeklyGrid(report: WeeklyReport, settings: AccountSettings) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val words = DateWords(resources)
    val styles = GridStyles(
        cell = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        total = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
        head = TokklTheme.smallCapsLabelTextStyle,
        date = MaterialTheme.typography.bodySmall,
        name = MaterialTheme.typography.bodyMedium,
    )
    val noTime = stringResource(R.string.reports_weekly_no_time)
    fun shown(seconds: Long) = if (seconds > 0) settings.formatDuration(seconds) else noTime
    val withoutProject = stringResource(R.string.reports_without_project)
    fun title(row: WeeklyRow) = row.title.ifBlank { withoutProject }
    val weekTotal = stringResource(R.string.reports_weekly_total)
    val project = stringResource(R.string.reports_weekly_project)

    // Each day column, then the total column, is as wide as its widest text.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun widthOf(text: String, style: TextStyle): Int = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
    val dayHeads = report.days.map { words.weekday(it).uppercase(locale) to words.short(it) }
    val columnWidths: List<Dp> = (0..DAYS_IN_WEEK).map { column ->
        val pixels = if (column < DAYS_IN_WEEK) {
            maxOf(
                widthOf(dayHeads[column].first, styles.head),
                widthOf(dayHeads[column].second, styles.date),
                widthOf(shown(report.dayTotals[column]), styles.total),
                report.rows.maxOfOrNull { widthOf(shown(it.seconds[column]), styles.cell) } ?: 0,
            )
        } else {
            maxOf(
                widthOf(weekTotal.uppercase(locale), styles.head),
                widthOf(settings.formatDuration(report.total), styles.total),
                report.rows.maxOfOrNull { widthOf(settings.formatDuration(it.total), styles.total) } ?: 0,
            )
        }
        with(density) { pixels.toDp() }
    }
    val dotSpace = TokklTheme.spacing.space3 + TokklTheme.spacing.space2
    val widestName = with(density) {
        maxOf(
            widthOf(project.uppercase(locale), styles.head),
            widthOf(weekTotal, styles.total),
            report.rows.maxOfOrNull { widthOf(title(it), styles.name) } ?: 0,
        ).toDp()
    } + dotSpace
    val widestWord = with(density) {
        (report.rows.flatMap { title(it).split(WHITESPACE) }.maxOfOrNull { widthOf(it, styles.name) } ?: 0).toDp()
    } + dotSpace

    val scroll = rememberScrollState()
    BoxWithConstraints {
        // A long name wraps rather than push every day off the screen, but never inside a word
        // that fits in the most the names may take.
        val nameWidth = min(widestName, min(max(maxWidth * NAME_SHARE, widestWord), maxWidth * NAME_MAX_SHARE))
        Column {
            GridRow(nameWidth, columnWidths, scroll, name = {
                Text(project.uppercase(locale), style = styles.head)
            }) { column ->
                if (column < DAYS_IN_WEEK) {
                    val day = report.days[column]
                    val spoken = words.bucket(day, Resolution.DAY)
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                        Text(dayHeads[column].first, style = styles.head, maxLines = 1, softWrap = false)
                        Text(
                            dayHeads[column].second,
                            style = styles.date,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                } else {
                    Text(weekTotal.uppercase(locale), style = styles.head, maxLines = 1, softWrap = false)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            for (row in report.rows) {
                val name = title(row)
                GridRow(nameWidth, columnWidths, scroll, name = { ProjectName(row, name, styles.name) }) { column ->
                    if (column < DAYS_IN_WEEK) {
                        val seconds = row.seconds[column]
                        Cell(
                            text = shown(seconds),
                            style = styles.cell,
                            muted = seconds == 0L,
                            spoken = resources.getString(
                                R.string.reports_weekly_cell_description,
                                name,
                                words.bucket(report.days[column], Resolution.DAY),
                                spokenDuration(resources, seconds),
                            ),
                        )
                    } else {
                        Cell(
                            text = settings.formatDuration(row.total),
                            style = styles.total,
                            muted = false,
                            spoken = resources.getString(R.string.reports_weekly_row_total_description, name, spokenDuration(resources, row.total)),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            GridRow(nameWidth, columnWidths, scroll, name = { Text(weekTotal, style = styles.total) }) { column ->
                if (column < DAYS_IN_WEEK) {
                    val seconds = report.dayTotals[column]
                    Cell(
                        text = shown(seconds),
                        style = styles.total,
                        muted = seconds == 0L,
                        spoken = resources.getString(
                            R.string.reports_weekly_day_total_description,
                            words.bucket(report.days[column], Resolution.DAY),
                            spokenDuration(resources, seconds),
                        ),
                    )
                } else {
                    Cell(
                        text = settings.formatDuration(report.total),
                        style = styles.total,
                        muted = false,
                        spoken = resources.getString(R.string.reports_weekly_total_description, spokenDuration(resources, report.total)),
                    )
                }
            }
        }
    }
}

private class GridStyles(val cell: TextStyle, val total: TextStyle, val head: TextStyle, val date: TextStyle, val name: TextStyle)

/**
 * One row of the grid: [name] in a column [nameWidth] wide that stays put, then a cell per column
 * of [columnWidths], which scroll sideways with [scroll].
 */
@Composable
private fun GridRow(
    nameWidth: Dp,
    columnWidths: List<Dp>,
    scroll: ScrollState,
    name: @Composable () -> Unit,
    cell: @Composable (column: Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = TokklTheme.spacing.space2),
    ) {
        Box(Modifier.width(nameWidth)) { name() }
        // Every row shares the scroll state; one row's stretch at the edge would part it from the others.
        Row(Modifier.horizontalScroll(scroll, overscrollEffect = null)) {
            for ((column, width) in columnWidths.withIndex()) {
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .padding(start = TokklTheme.spacing.space3)
                        .width(width),
                ) { cell(column) }
            }
        }
    }
}

/** The project's dot and name, or the words for time without a project. */
@Composable
private fun ProjectName(row: WeeklyRow, name: String, style: TextStyle) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        val dot = projectMark(row.color, TokklTheme.extraColors.projectMix, muted)
        Canvas(Modifier.size(TokklTheme.spacing.space3)) { drawCircle(dot) }
        Spacer(Modifier.width(TokklTheme.spacing.space2))
        Text(name, style = style, color = if (row.title.isBlank()) muted else MaterialTheme.colorScheme.onSurface)
    }
}

/** A number, which TalkBack reads as [spoken]: its project or day, and the time in words. */
@Composable
private fun Cell(text: String, style: TextStyle, muted: Boolean, spoken: String) {
    Text(
        text = text,
        style = style,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

/** The share of the grid's width the project names take before they wrap. */
private const val NAME_SHARE = 0.4f

/** The most of the grid's width the project names take, to keep a long word whole. */
private const val NAME_MAX_SHARE = 0.6f

private val WHITESPACE = Regex("\\s+")
