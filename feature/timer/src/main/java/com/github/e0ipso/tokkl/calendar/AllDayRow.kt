package com.github.e0ipso.tokkl.calendar

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.takeOrElse
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.DateWords
import com.github.e0ipso.tokkl.ui.compactHeightWindow
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.SmallCornerShape
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.LocalDate
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The All day row, as `.calendar-all-day` in the web app's WeekCalendar.tsx: its label over the
 * hour labels' column, then a cell for each of [days] in line with the grid's columns. A cell
 * lists the day's no-time entries as chips and, with [editing], has a + that adds one. The row
 * grows with its cells up to [AllDayMaxHeight], then scrolls, so many chips never push the grid
 * off the screen. TalkBack gets the add as an action on each cell too.
 */
@Composable
internal fun AllDayRow(
    days: List<LocalDate>,
    state: CalendarState.Shown,
    now: Instant,
    gutter: Dp,
    editing: CalendarEditing?,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(max = AllDayMaxHeight)
            .verticalScroll(rememberScrollState())
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val stroke = DividerDefaults.Thickness.toPx()
                drawLine(
                    line,
                    Offset(0f, size.height - stroke / 2),
                    Offset(size.width, size.height - stroke / 2),
                    stroke,
                )
            },
    ) {
        Text(
            text = stringResource(R.string.calendar_all_day),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = StripMinFontSize,
                maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
            ),
            modifier = Modifier
                .width(gutter)
                .padding(top = TokklTheme.spacing.space2, end = TokklTheme.spacing.space1),
        )
        for (day in days) {
            AllDayCell(day, state.allDay[day].orEmpty(), state, now, editing, Modifier.weight(1f))
        }
    }
}

/**
 * One day of the All day row: [entries] as chips over the cell's width, and the + that adds one
 * at its end, so a day with no more than a chip is one chip tall in every window. The room goes
 * to the hour grid, which a phone held sideways has little of.
 */
@Composable
private fun AllDayCell(
    day: LocalDate,
    entries: List<CalendarEntry>,
    state: CalendarState.Shown,
    now: Instant,
    editing: CalendarEditing?,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val addAction = allDayAddAction(day, editing)
    Row(
        horizontalArrangement = Arrangement.spacedBy(BlockGap),
        modifier = modifier
            .fillMaxHeight()
            .drawBehind { drawLine(line, Offset.Zero, Offset(0f, size.height), DividerDefaults.Thickness.toPx()) }
            .padding(BlockGap)
            .semantics { if (addAction != null) customActions = listOf(addAction) },
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
            for (entry in entries) {
                AllDayChip(entry, state.settings, now, onEdit = editing?.let { { it.edit(entry) } })
            }
        }
        if (editing != null) {
            // The day in the label tells the week's seven buttons apart, as the web app's do.
            val resources = LocalResources.current
            val words = remember(resources) { DateWords(resources) }
            val dayLabel = resources.getString(UiR.string.history_day, words.weekday(day), words.short(day))
            IconButton(onClick = { editing.addAllDay(day) }) {
                Icon(
                    painterResource(UiR.drawable.ic_plus),
                    contentDescription = stringResource(R.string.calendar_add_all_day_on, dayLabel),
                )
            }
        }
    }
}

/**
 * A no-time entry in the All day row: its description, else its project, else the history's "No
 * description", over its project and duration, in a block's colours and with a block's unsent
 * mark. With [onEdit], a tap opens the entry form. A chip has no drag, since its day is all it has.
 * In a window of compact height, a phone held sideways or at the largest font scale, a chip is
 * one line, as tall as a touch target, so the grid keeps its room ([oneLine]).
 */
@Composable
private fun AllDayChip(
    entry: CalendarEntry,
    settings: AccountSettings,
    now: Instant,
    onEdit: (() -> Unit)?,
    oneLine: Boolean = compactHeightWindow(),
) {
    val resources = LocalResources.current
    val colours = entryColours(entry)
    val noDescription = stringResource(R.string.history_no_description)
    val heading = entry.description.ifBlank { entry.project ?: noDescription }
    val duration = settings.formatDuration(entry.seconds(now))
    val spoken = spokenChip(resources, entry, now)
    val editLabel = stringResource(R.string.entry_edit_action)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current.takeOrElse { 48.dp })
            .clip(SmallCornerShape)
            .background(colours.tint)
            .drawBehind { drawRect(colours.bar, size = Size(BlockBar.toPx(), size.height)) }
            .then(
                if (onEdit != null) {
                    Modifier.clickable(
                        onClickLabel = editLabel,
                        role = Role.Button,
                        onClick = onEdit,
                    )
                } else {
                    Modifier
                },
            )
            .padding(
                start = BlockBar + TokklTheme.spacing.space1,
                end = TokklTheme.spacing.space1,
                top = BlockGap,
                bottom = BlockGap,
            )
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        when {
            // Where height is short, a chip is its touch target's height and no more, and says "Not sent" with its
            // icon.
            oneLine -> ChipLine(heading, duration, mark = entry.unsent)

            entry.unsent -> UnsentBlockText { mark ->
                BlockText(
                    entry,
                    heading,
                    duration,
                    twoLines = true,
                    mark = mark,
                )
            }

            else -> BlockText(entry, heading, duration, twoLines = true, mark = false)
        }
    }
}

/** A chip's text on one line: the heading, which gives way first, and the duration after it. */
@Composable
private fun ChipLine(heading: String, duration: String, mark: Boolean) {
    val small = MaterialTheme.typography.bodySmall
    MarkedLine(mark, small) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = heading,
                style = small.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = duration,
                style = small.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = TokklTheme.spacing.space1),
            )
        }
    }
}

/** What TalkBack reads for a chip: description, project and task, duration, and whether it is unsent. */
private fun spokenChip(resources: Resources, entry: CalendarEntry, now: Instant): String = listOfNotNull(
    entry.description.ifBlank { resources.getString(R.string.history_no_description) },
    entry.project,
    entry.task,
    spokenDuration(resources, entry.seconds(now)),
    if (entry.unsent) resources.getString(R.string.history_unsent) else null,
).joinToString(", ")

/** TalkBack's action on [day]'s All day cell that opens the entry form for a new no-time entry, with [editing]. */
@Composable
private fun allDayAddAction(day: LocalDate, editing: CalendarEditing?): CustomAccessibilityAction? {
    if (editing == null) return null
    return CustomAccessibilityAction(stringResource(R.string.calendar_add_all_day)) {
        editing.addAllDay(day)
        true
    }
}
