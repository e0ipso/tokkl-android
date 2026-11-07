package com.github.e0ipso.tokkl.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.UnsentIcon
import com.github.e0ipso.tokkl.ui.projectMark
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.ZoneId
import java.util.Locale
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * One stopped entry, one item to TalkBack, and beside it the buttons that continue it and open its
 * actions, in the web app's order. Its marks are words, never a colour alone. A tap opens the entry
 * for editing, and the menu of an entry the phone holds duplicates, splits or deletes it, in the
 * order of the web app's editor. The older days the server sent are not on the phone, so their
 * rows have no menu, and the entry form edits them as the history holds them. Continue needs only
 * the entry's fields, so every row has it.
 */
@Composable
internal fun EntryRow(
    entry: HistoryEntry,
    zone: ZoneId,
    settings: AccountSettings,
    locale: Locale,
    callbacks: EntryCallbacks,
) {
    // Where the description's first line has its middle, in pixels from the row's top, so the
    // menu button lines up with it at any font scale.
    var firstLineMiddle by remember { mutableFloatStateOf(0f) }
    val editLabel = stringResource(R.string.entry_edit_action)
    Row(Modifier.fillMaxWidth()) {
        EntryText(
            line = RowLine(entry),
            zone = zone,
            settings = settings,
            locale = locale,
            onFirstLineMiddle = { firstLineMiddle = it },
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = editLabel, role = Role.Button) { callbacks.onEdit(entry.id) }
                .semantics(mergeDescendants = true) {},
        )
        ContinueButton(entry, callbacks, firstLineMiddle)
        if (entry.cached) EntryMenu(entry, callbacks, firstLineMiddle)
    }
}

/**
 * Similar entries of a day as one line, as the web app's timer list groups them (docs/PRD.md H4):
 * the newest member's fields, a badge with the count, the members' total, and the time from the
 * earliest timed member's start to the latest timed member's stop (none for a group of only
 * no-time entries), with a button that continues the newest
 * member. A tap shows or hides the members, each with its own tap, Continue and menu. TalkBack
 * reads the line as one button with the count, the total and the state, Expanded or Collapsed,
 * and says the new state after a tap. A chevron shows the state too, so it never rests on colour.
 */
@Composable
internal fun GroupRow(
    row: HistoryRow,
    zone: ZoneId,
    settings: AccountSettings,
    locale: Locale,
    callbacks: EntryCallbacks,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var firstLineMiddle by remember { mutableFloatStateOf(0f) }
    val state = stringResource(if (expanded) R.string.history_group_expanded else R.string.history_group_collapsed)
    val action = stringResource(if (expanded) R.string.history_group_hide else R.string.history_group_show)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = action, role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) { stateDescription = state },
        ) {
            EntryText(
                line = RowLine(row),
                zone = zone,
                settings = settings,
                locale = locale,
                onFirstLineMiddle = { firstLineMiddle = it },
                modifier = Modifier.weight(1f),
            )
            ContinueButton(row.newest, callbacks, firstLineMiddle)
            val top = buttonTop(firstLineMiddle)
            Box(Modifier.padding(top = top).size(MENU_BUTTON_SIZE), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (expanded) 90f else 0f),
                )
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = TokklTheme.spacing.space4)) {
                for (member in row.entries) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EntryRow(member, zone, settings, locale, callbacks)
                }
            }
        }
    }
}

/**
 * The button that continues [entry] (docs/PRD.md T2), Tokkl's play icon as on the web app's row,
 * named for TalkBack with the entry's description.
 */
@Composable
private fun ContinueButton(entry: HistoryEntry, callbacks: EntryCallbacks, firstLineMiddle: Float) {
    val name = entry.description.ifBlank { stringResource(R.string.history_no_description) }
    IconButton(
        onClick = { callbacks.onContinue(entry.id) },
        modifier = Modifier.padding(top = buttonTop(firstLineMiddle)),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_play),
            contentDescription = stringResource(R.string.history_continue, name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The menu of actions on the entry the phone holds, behind a button that names the entry. */
@Composable
private fun EntryMenu(entry: HistoryEntry, callbacks: EntryCallbacks, firstLineMiddle: Float) {
    var open by remember { mutableStateOf(false) }
    val name = entry.description.ifBlank { stringResource(R.string.history_no_description) }
    Box(Modifier.padding(top = buttonTop(firstLineMiddle))) {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_more),
                contentDescription = stringResource(R.string.history_more_actions, name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_duplicate)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
                onClick = {
                    open = false
                    callbacks.onDuplicate(entry.id)
                },
            )
            // A no-time entry has no time to cut at, as in the web app's editor.
            if (!entry.allDay) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_split)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_split), contentDescription = null) },
                    onClick = {
                        open = false
                        callbacks.onSplit(entry.id)
                    },
                )
            }
            val pin = callbacks.pin
            if (pin != null) {
                // TalkBack reads the item's text as its label, after the menu button that names the entry.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_pin)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
                    enabled = pin.enabled,
                    onClick = {
                        open = false
                        pin.onPin(entry.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete)) },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_nav_trash), contentDescription = null) },
                onClick = {
                    open = false
                    callbacks.onDelete(entry.id)
                },
            )
        }
    }
}

/**
 * What a line of the history shows: an entry, or a group of similar ones. [entry] gives the
 * fields, [count] is the group's size or null for one entry, and [seconds], [range] and [unsent]
 * cover the whole line. [range] is null when the line has no timed entry, so it shows only the
 * duration.
 */
private class RowLine(
    val entry: HistoryEntry,
    val count: Int?,
    val seconds: Long,
    val range: HistoryRange?,
    val unsent: Boolean,
) {
    constructor(entry: HistoryEntry) : this(
        entry,
        null,
        entry.seconds,
        HistoryRange(entry.start, entry.stop).takeIf { !entry.allDay },
        entry.unsent,
    )

    constructor(row: HistoryRow) : this(
        row.newest,
        row.entries.size,
        row.seconds,
        row.range,
        row.entries.any { it.unsent },
    )
}

/** The line's count, description, duration, project, times and marks. [modifier] takes its tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryText(
    line: RowLine,
    zone: ZoneId,
    settings: AccountSettings,
    locale: Locale,
    onFirstLineMiddle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = line.entry
    val topPadding = with(LocalDensity.current) { TokklTheme.spacing.space3.toPx() }
    val resources = LocalResources.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val range = line.range
    val start = range?.let { settings.formatTime(it.start, zone) }
    val stop = range?.let { settings.formatTime(it.stop, zone) }
    val spokenRange = if (start != null && stop != null) {
        stringResource(
            R.string.history_time_range_spoken,
            start,
            stop,
        )
    } else {
        null
    }
    val spokenSeconds = spokenDuration(resources, line.seconds)
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
        modifier = modifier.padding(vertical = TokklTheme.spacing.space3),
    ) {
        Row {
            if (line.count != null) {
                CountBadge(line.count, Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.width(TokklTheme.spacing.space2))
            }
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
                text = settings.formatDuration(line.seconds),
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
                    text = entry.task?.let {
                        stringResource(
                            R.string.history_project_task,
                            entry.project,
                            it,
                        )
                    } ?: entry.project,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3)) {
            if (start != null && stop != null && spokenRange != null) {
                Text(
                    text = stringResource(R.string.history_time_range, start, stop),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = muted,
                    modifier = Modifier.semantics { contentDescription = spokenRange },
                )
            }
            if (entry.tags.isNotEmpty()) {
                Text(entry.tags.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            if (entry.billable) {
                Text(
                    stringResource(R.string.history_billable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
            if (line.unsent) {
                // The icon and the words, so colour is never the only sign. TalkBack reads the words.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    UnsentIcon(TokklTheme.smallCapsLabelTextStyle)
                    Text(
                        text = stringResource(R.string.history_unsent).uppercase(locale),
                        style = TokklTheme.smallCapsLabelTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * How many entries a group holds, in a pill with a border, as the web app's count badge. TalkBack
 * reads it as that many entries.
 */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val spoken = LocalResources.current.getQuantityString(R.plurals.history_group_entries, count, count)
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = TokklTheme.spacing.space2)
            .semantics { contentDescription = spoken },
    )
}

/**
 * How far below a row's top its buttons start, so their middles line up with the middle of the
 * description's first line, [firstLineMiddle] pixels from the row's top, at any font scale.
 */
@Composable
private fun buttonTop(firstLineMiddle: Float) =
    with(LocalDensity.current) { (firstLineMiddle.toDp() - MENU_BUTTON_SIZE / 2).coerceAtLeast(0.dp) }

/** The height Material gives an IconButton: its minimum touch target. */
private val MENU_BUTTON_SIZE = 48.dp
