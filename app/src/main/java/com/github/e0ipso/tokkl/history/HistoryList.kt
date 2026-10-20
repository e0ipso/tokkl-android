package com.github.e0ipso.tokkl.history

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.reports.projectMark
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The history in the Timer page's List view: the days with entries, newest first, each with its
 * total, and at the end the older days, which load from the server as the end shows. A tap on an
 * entry the phone holds hands its id to [onEdit] (docs/PRD.md H2). Its menu deletes it through
 * [actions], whose messages show at the bottom of the list (H3).
 */
@Composable
fun HistoryList(model: HistoryModel, actions: EntryActionsModel, onEdit: (Long) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val actionsState by actions.state.collectAsState()
    Box(modifier.fillMaxSize()) {
        HistoryContent(state = state, onLoadOlder = model::loadOlder, onEdit = onEdit, onDelete = actions::delete)
        EntryActionsHost(
            state = actionsState,
            onUndo = actions::undo,
            onNoticeDone = actions::noticeDone,
            onConfirmDelete = actions::confirmDelete,
            onCancelDelete = actions::cancelDelete,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** [HistoryList] without its models. */
@Composable
internal fun HistoryContent(
    state: HistoryState,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
) {
    when (state) {
        // The cache reads in moments, so this shows nothing rather than a flash of a spinner.
        HistoryState.Loading -> Unit
        is HistoryState.NoAccountTime -> Column(modifier.fillMaxSize()) {
            Text(stringResource(R.string.history_unknown_time_zone, state.timeZone), style = MaterialTheme.typography.bodyLarge)
        }
        is HistoryState.Shown -> Days(state, onLoadOlder, EntryCallbacks(onEdit, onDelete), modifier)
    }
}

/** What a tap on an entry the phone holds, or on one of its actions, does with the entry's id. */
private class EntryCallbacks(val onEdit: (Long) -> Unit, val onDelete: (Long) -> Unit)

@Composable
private fun Days(state: HistoryState.Shown, onLoadOlder: () -> Unit, callbacks: EntryCallbacks, modifier: Modifier) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val words = remember(resources) { DateWords(resources) }
    val listState = rememberLazyListState()
    val olderShows by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == OLDER_KEY } }
    }
    val older = state.older
    // Scrolling to the end is the event that loads older days. A page that leaves the end in
    // view loads the next one, until the server has no more or a page comes back empty.
    LaunchedEffect(olderShows, older) {
        if (olderShows && older is OlderDays.More && older.auto) onLoadOlder()
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        if (state.days.isEmpty()) {
            item(key = EMPTY_KEY) { Empty() }
        }
        for (day in state.days) {
            item(key = "day-${day.date}", contentType = DAY_TYPE) {
                DayHeader(dayLabel(day.date, state.today, resources, words), day.totalSeconds, state.settings)
            }
            items(day.entries, key = { "entry-${it.id}" }, contentType = { ENTRY_TYPE }) { entry ->
                EntryRow(entry, state.zone, state.settings, locale, callbacks)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item(key = OLDER_KEY) { OlderRow(older, onLoadOlder) }
        item(key = BOTTOM_KEY) { Spacer(Modifier.navigationBarsPadding()) }
    }
}

/**
 * Today, Yesterday, or the weekday and date, with the year when it is not this year's, as
 * dayLabel in the web app's lib/settings.ts.
 */
private fun dayLabel(date: LocalDate, today: LocalDate, resources: Resources, words: DateWords): String = when (date) {
    today -> resources.getString(R.string.history_today)
    today.minusDays(1) -> resources.getString(R.string.history_yesterday)
    else -> resources.getString(
        R.string.history_day,
        words.weekday(date),
        if (date.year == today.year) words.short(date) else words.shortWithYear(date),
    )
}

/** The day and its total, one heading to TalkBack. */
@Composable
private fun DayHeader(label: String, totalSeconds: Long, settings: AccountSettings) {
    val locale = LocalConfiguration.current.locales[0]
    val spoken = stringResource(R.string.history_day_spoken, label, spokenDuration(LocalResources.current, totalSeconds))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TokklTheme.spacing.betweenGroups, bottom = TokklTheme.spacing.inGroup)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = spoken
            },
    ) {
        Text(label.uppercase(locale), style = TokklTheme.dayHeaderTextStyle, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Text(settings.formatDuration(totalSeconds), style = TokklTheme.dayTotalTextStyle)
    }
}

/**
 * One stopped entry, one item to TalkBack, and beside it the button for its actions. Its marks
 * are words, never a colour alone. A tap on an entry the phone holds opens it for editing, and its
 * menu deletes it. The older days the server sent are not on the phone, so the change queue
 * cannot change them, and their rows take no tap and have no menu.
 */
@Composable
private fun EntryRow(entry: HistoryEntry, zone: ZoneId, settings: AccountSettings, locale: Locale, callbacks: EntryCallbacks) {
    // Where the description's first line has its middle, in pixels from the row's top, so the
    // menu button lines up with it at any font scale.
    var firstLineMiddle by remember { mutableFloatStateOf(0f) }
    Row(Modifier.fillMaxWidth()) {
        EntryText(entry, zone, settings, locale, callbacks.onEdit, { firstLineMiddle = it }, Modifier.weight(1f))
        if (entry.cached) EntryMenu(entry, callbacks, firstLineMiddle)
    }
}

/** The menu of actions on the entry the phone holds, behind a button that names the entry. */
@Composable
private fun EntryMenu(entry: HistoryEntry, callbacks: EntryCallbacks, firstLineMiddle: Float) {
    var open by remember { mutableStateOf(false) }
    val name = entry.description.ifBlank { stringResource(R.string.history_no_description) }
    val top = with(LocalDensity.current) { (firstLineMiddle.toDp() - MENU_BUTTON_SIZE / 2).coerceAtLeast(0.dp) }
    Box(Modifier.padding(top = top)) {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_more),
                contentDescription = stringResource(R.string.history_more_actions, name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_nav_trash), contentDescription = null) },
                onClick = {
                    open = false
                    callbacks.onDelete(entry.id)
                },
            )
        }
    }
}

/** The entry's description, duration, project, times and marks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryText(
    entry: HistoryEntry,
    zone: ZoneId,
    settings: AccountSettings,
    locale: Locale,
    onEdit: (Long) -> Unit,
    onFirstLineMiddle: (Float) -> Unit,
    modifier: Modifier,
) {
    val topPadding = with(LocalDensity.current) { TokklTheme.spacing.space3.toPx() }
    val resources = LocalResources.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val start = settings.formatTime(entry.start, zone)
    val stop = settings.formatTime(entry.stop, zone)
    val spokenRange = stringResource(R.string.history_time_range_spoken, start, stop)
    val spokenSeconds = spokenDuration(resources, entry.seconds)
    val editLabel = stringResource(R.string.entry_edit_action)
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
        modifier = modifier
            .then(if (entry.cached) Modifier.clickable(onClickLabel = editLabel, role = Role.Button) { onEdit(entry.id) } else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TokklTheme.spacing.space3),
    ) {
        Row {
            val untitled = entry.description.isBlank()
            Text(
                text = if (untitled) stringResource(R.string.history_no_description) else entry.description,
                style = MaterialTheme.typography.bodyLarge,
                color = if (untitled) muted else MaterialTheme.colorScheme.onSurface,
                onTextLayout = { onFirstLineMiddle(topPadding + (it.getLineTop(0) + it.getLineBottom(0)) / 2) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(TokklTheme.spacing.space3))
            Text(
                text = settings.formatDuration(entry.seconds),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.semantics { contentDescription = spokenSeconds },
            )
        }
        if (entry.project != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = projectMark(entry.projectColor, TokklTheme.extraColors.projectMix, muted)
                Canvas(Modifier.size(TokklTheme.spacing.space3)) { drawCircle(dot) }
                Spacer(Modifier.width(TokklTheme.spacing.space2))
                Text(
                    text = entry.task?.let { stringResource(R.string.history_project_task, entry.project, it) } ?: entry.project,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3)) {
            Text(
                text = stringResource(R.string.history_time_range, start, stop),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = muted,
                modifier = Modifier.semantics { contentDescription = spokenRange },
            )
            if (entry.tags.isNotEmpty()) {
                Text(entry.tags.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            if (entry.billable) {
                Text(stringResource(R.string.history_billable), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            if (entry.unsent) {
                Text(
                    text = stringResource(R.string.history_unsent).uppercase(locale),
                    style = TokklTheme.smallCapsLabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** The end of the list: what loads, or why nothing more does. */
@Composable
private fun OlderRow(older: OlderDays, onLoadOlder: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TokklTheme.spacing.betweenGroups),
    ) {
        when (older) {
            is OlderDays.More -> OutlinedButton(onClick = onLoadOlder) { Text(stringResource(R.string.history_load_older)) }
            OlderDays.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(Modifier.size(TokklTheme.spacing.space6))
                Spacer(Modifier.width(TokklTheme.spacing.space3))
                Text(stringResource(R.string.history_loading_older), style = MaterialTheme.typography.bodyLarge)
            }
            OlderDays.End -> Text(
                stringResource(R.string.history_no_older),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OlderDays.Unreachable -> Problem(
                title = stringResource(R.string.history_offline_title),
                message = stringResource(R.string.history_offline_message),
                onRetry = onLoadOlder,
            )
            OlderDays.AuthFailed -> Problem(title = null, message = stringResource(R.string.history_error_auth), onRetry = onLoadOlder)
            OlderDays.TrustRefused -> Problem(title = null, message = stringResource(R.string.history_error_trust), onRetry = onLoadOlder)
            is OlderDays.Failed -> Problem(
                title = null,
                message = older.status?.let { stringResource(R.string.history_error_status, it) }
                    ?: stringResource(R.string.history_error_unreadable),
                onRetry = onLoadOlder,
            )
        }
    }
}

@Composable
private fun Problem(title: String?, message: String, onRetry: () -> Unit) {
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
}

/** No entry in the days shown, as the web app's empty list. */
@Composable
private fun Empty() {
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.history_empty_message), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The height Material gives an IconButton: its minimum touch target. */
private val MENU_BUTTON_SIZE = 48.dp

private const val EMPTY_KEY = "empty"
private const val OLDER_KEY = "older"
private const val BOTTOM_KEY = "bottom"
private const val DAY_TYPE = "day"
private const val ENTRY_TYPE = "entry"
