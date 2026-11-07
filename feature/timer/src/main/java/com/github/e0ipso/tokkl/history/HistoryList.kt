package com.github.e0ipso.tokkl.history

import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.favorites.FavoritesViewModel
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.DateWords
import com.github.e0ipso.tokkl.ui.daySticker
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.LocalDate

/**
 * The history in the Timer page's List view: the days with entries, newest first, each with its
 * total, and at the end the older days, which load from the server as the end shows. A tap on an
 * entry hands its id to [onEdit] (docs/PRD.md H2), also on the older days. The menu of an entry the
 * phone holds duplicates, splits or deletes it through [actions], whose messages show at the
 * bottom of the list (H3, H5, H6). When the server keeps Tokkl's [favorites], the menu also saves
 * the entry as a timer template (T5). Its Continue button, on every entry and group, hands the entry's id to
 * [onContinue] (T2).
 */
@Composable
fun HistoryList(
    model: HistoryViewModel,
    actions: EntryActionsViewModel,
    onEdit: (Long) -> Unit,
    onContinue: (Long) -> Unit,
    modifier: Modifier = Modifier,
    favorites: FavoritesViewModel? = null,
    showActions: Boolean = true,
) {
    val state by model.state.collectAsState()
    val actionsState by actions.state.collectAsState()
    val pin = favorites?.let { pinAction(actions, it) }
    Box(modifier.fillMaxSize()) {
        HistoryContent(
            state = state,
            onLoadOlder = model::loadOlder,
            onEdit = onEdit,
            onContinue = onContinue,
            onDuplicate = actions::duplicate,
            onSplit = actions::split,
            onDelete = actions::delete,
            pin = pin,
        )
        if (showActions) EntryActionsHost(
            state = actionsState,
            onUndo = actions::undo,
            onNoticeDone = actions::noticeDone,
            onConfirmDelete = actions::confirmDelete,
            onCancelDelete = actions::cancelDelete,
            modifier = Modifier.align(Alignment.BottomCenter),
            settings = (state as? HistoryState.Shown)?.settings,
            onSplitTextChange = actions::setSplitText,
            onConfirmSplit = actions::confirmSplit,
            onCancelSplit = actions::cancelSplit,
        )
    }
}

/**
 * The menu's Save as template: [onPin] gets the entry's id, and [enabled] is false while another
 * change to the templates is on its way to the server.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class PinAction(val enabled: Boolean, val onPin: (Long) -> Unit)

/** Save as template through [favorites], or null when the server keeps no timer templates. */
@Composable
internal fun pinAction(actions: EntryActionsViewModel, favorites: FavoritesViewModel): PinAction? {
    val shown by favorites.state.collectAsState()
    val offline = rememberOffline()
    if (!shown.available) return null
    return PinAction(enabled = !shown.saving) { entryId -> actions.pin(entryId, favorites, offline) }
}

/** [HistoryList] without its models. Without [pin], the menu offers no Save as template. */
@Composable
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
fun HistoryContent(
    state: HistoryState,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (Long) -> Unit = {},
    onContinue: (Long) -> Unit = {},
    onDuplicate: (Long) -> Unit = {},
    onSplit: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    pin: PinAction? = null,
) {
    when (state) {
        // The cache reads in moments, so this shows nothing rather than a flash of a spinner.
        HistoryState.Loading -> Unit
        is HistoryState.NoAccountTime -> Column(modifier.fillMaxSize()) {
            Text(stringResource(R.string.history_unknown_time_zone, state.timeZone), style = MaterialTheme.typography.bodyLarge)
        }
        is HistoryState.Shown -> Days(state, onLoadOlder, EntryCallbacks(onEdit, onContinue, onDuplicate, onSplit, onDelete, pin), modifier)
    }
}

/** What a tap on an entry, or on one of its actions, does with the entry's id. */
internal class EntryCallbacks(
    val onEdit: (Long) -> Unit,
    val onContinue: (Long) -> Unit,
    val onDuplicate: (Long) -> Unit,
    val onSplit: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val pin: PinAction?,
)

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
            for (row in day.rows) {
                if (row.grouped) {
                    item(key = "group-${row.key}", contentType = GROUP_TYPE) {
                        GroupRow(row, state.zone, state.settings, locale, callbacks)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                } else {
                    item(key = "entry-${row.newest.id}", contentType = ENTRY_TYPE) {
                        EntryRow(row.newest, state.zone, state.settings, locale, callbacks)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
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
        SharedR.string.history_day,
        words.weekday(date),
        if (date.year == today.year) words.short(date) else words.shortWithYear(date),
    )
}

/**
 * The day and its total, one heading to TalkBack, on a day sticker: a pill in the section's hue
 * with the text in the hue's text colour, as `.day-header` in styles.css since tokkl d012228. The
 * day stickers take the section's hue rather than one per day, as the web app's do.
 */
@Composable
private fun DayHeader(label: String, totalSeconds: Long, settings: AccountSettings) {
    val locale = LocalConfiguration.current.locales[0]
    val spoken = stringResource(R.string.history_day_spoken, label, spokenDuration(LocalResources.current, totalSeconds))
    val hue = TokklTheme.sectionHue
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TokklTheme.spacing.betweenGroups, bottom = TokklTheme.spacing.inGroup)
            .daySticker(hue, CircleShape, TokklTheme.extraColors.stickerOutline)
            .padding(
                start = TokklTheme.spacing.space3,
                end = TokklTheme.spacing.space4,
                top = TokklTheme.spacing.space2,
                bottom = TokklTheme.spacing.space2,
            )
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = spoken
            },
    ) {
        Text(label.uppercase(locale), style = TokklTheme.dayHeaderTextStyle, color = hue.onFill, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Text(settings.formatDuration(totalSeconds), style = TokklTheme.dayTotalTextStyle, color = hue.onFill)
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

private const val EMPTY_KEY = "empty"
private const val OLDER_KEY = "older"
private const val BOTTOM_KEY = "bottom"
private const val DAY_TYPE = "day"
private const val ENTRY_TYPE = "entry"
private const val GROUP_TYPE = "group"
