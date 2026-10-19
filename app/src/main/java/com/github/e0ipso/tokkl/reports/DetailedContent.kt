package com.github.e0ipso.tokkl.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import java.time.LocalDate

/**
 * The detailed report as the web app's DetailedTab lists it: one row per entry, newest first,
 * with its day, times, description, project and duration, in the account's timezone and clock.
 * [header] scrolls away with the list. When the end of the list comes near, it asks for the next
 * page with [onLoadMore], so the phone never holds more than the pages scrolled to.
 */
@Composable
internal fun DetailedContent(
    report: DetailedReport,
    settings: AccountSettings,
    today: LocalDate,
    header: @Composable () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= layout.totalItemsCount - 1 - LOAD_AHEAD
        }
    }
    // A page that leaves the end in view asks for the next one, until the server sends the last.
    LaunchedEffect(nearEnd, report.nextRow, report.loadingMore) {
        if (nearEnd && report.nextRow != null && !report.loadingMore) onLoadMore()
    }
    val words = DateWords(LocalResources.current)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = TokklTheme.spacing.space2),
        modifier = modifier,
    ) {
        item(key = HEADER_KEY) {
            Column(Modifier.screenContent()) { header() }
        }
        items(report.entries) { entry ->
            Column(Modifier.screenContent()) {
                EntryRow(entry, settings, words.day(entry.start.toLocalDate(), today))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item(key = END_KEY) {
            Column(
                Modifier
                    .screenContent()
                    .padding(vertical = TokklTheme.spacing.betweenGroups),
            ) {
                if (report.loadingMore) Loading(stringResource(R.string.reports_loading_more))
            }
        }
        item(key = BOTTOM_KEY) { Spacer(Modifier.navigationBarsPadding()) }
    }
}

/** One entry, one item to TalkBack, which reads its times and duration in words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(entry: DetailedEntry, settings: AccountSettings, day: String) {
    val resources = LocalResources.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val start = settings.formatTime(entry.start.toInstant(), entry.start.zone)
    val stop = entry.stop?.let { settings.formatTime(it.toInstant(), it.zone) }
    val times = if (stop == null) start else stringResource(R.string.reports_time_range, start, stop)
    val spokenTimes = if (stop == null) start else stringResource(R.string.reports_time_range_spoken, start, stop)
    val spokenSeconds = spokenDuration(resources, entry.seconds)
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TokklTheme.spacing.space3),
    ) {
        Row {
            val untitled = entry.description.isBlank()
            Text(
                text = if (untitled) stringResource(R.string.reports_no_description) else entry.description,
                style = MaterialTheme.typography.bodyLarge,
                color = if (untitled) muted else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(TokklTheme.spacing.space3))
            Text(
                text = settings.formatDuration(entry.seconds),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.semantics { contentDescription = spokenSeconds },
            )
        }
        if (entry.projectName == null) {
            Text(stringResource(R.string.reports_without_project), style = MaterialTheme.typography.bodyMedium, color = muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = projectMark(entry.projectColor, TokklTheme.extraColors.projectMix, muted)
                Canvas(Modifier.size(TokklTheme.spacing.space3)) { drawCircle(dot) }
                Spacer(Modifier.width(TokklTheme.spacing.space2))
                Text(
                    text = entry.taskName?.let { stringResource(R.string.reports_project_task, entry.projectName, it) } ?: entry.projectName,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3)) {
            Text(day, style = MaterialTheme.typography.bodyMedium, color = muted)
            Text(
                text = times,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = muted,
                modifier = Modifier.semantics { contentDescription = spokenTimes },
            )
        }
    }
}

/** How many rows before the end the next page is asked for. */
private const val LOAD_AHEAD = 10

private const val HEADER_KEY = "header"
private const val END_KEY = "end"
private const val BOTTOM_KEY = "bottom"
