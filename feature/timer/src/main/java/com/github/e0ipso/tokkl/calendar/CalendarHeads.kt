package com.github.e0ipso.tokkl.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.DateWords
import com.github.e0ipso.tokkl.ui.daySticker
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.LocalDate
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** The arrows, the way back to today, the week or day shown and the week's total. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Toolbar(
    state: CalendarState.Shown,
    dayMode: Boolean,
    now: Instant,
    onStep: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val resources = LocalResources.current
    val words = remember(resources) { DateWords(resources) }
    val range = if (dayMode) {
        resources.getString(UiR.string.history_day, words.weekday(state.selected), words.short(state.selected))
    } else {
        resources.getString(
            UiR.string.reports_range,
            words.short(state.weekStart),
            words.short(state.weekStart.plusDays(6)),
        )
    }
    val weekSeconds = state.weekDays.sumOf { dayTotalSeconds(state.entries, it, state.zone, now) }
    val weekTotal = stringResource(R.string.calendar_week_total, state.settings.formatDuration(weekSeconds))
    val spokenTotal = stringResource(R.string.calendar_week_total, spokenDuration(resources, weekSeconds))
    FlowRow(
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }) {
                Icon(
                    painterResource(UiR.drawable.ic_chevron_left),
                    contentDescription = stringResource(
                        if (dayMode) R.string.calendar_previous_day else R.string.calendar_previous_week,
                    ),
                )
            }
            OutlinedButton(onClick = onToday, shape = MaterialTheme.shapes.small) {
                Text(stringResource(if (dayMode) R.string.calendar_today else R.string.calendar_this_week))
            }
            IconButton(onClick = { onStep(1) }) {
                Icon(
                    painterResource(UiR.drawable.ic_chevron_right),
                    contentDescription = stringResource(
                        if (dayMode) R.string.calendar_next_day else R.string.calendar_next_week,
                    ),
                )
            }
        }
        Column(Modifier.padding(start = TokklTheme.spacing.space2)) {
            Text(
                text = range,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            // A week the phone does not have yet has no total to show.
            if (state.week.shows) {
                Text(
                    text = weekTotal,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = spokenTotal },
                )
            }
        }
    }
}

/**
 * A head for each of [days] above its column: its weekday, or Today in a filled badge, its date
 * and, once the week's entries are known, its total. Each head is a day sticker in the section's
 * hue, as `.calendar-day-head` in styles.css since tokkl d012228. Their band is surface, not
 * surface-2, as the web app's: in dark the violet is under 3:1 on surface-2.
 */
@Composable
internal fun DayHeads(
    days: List<LocalDate>,
    today: LocalDate,
    state: CalendarState.Shown,
    now: Instant,
    gutter: Dp,
    editing: CalendarEditing?,
) {
    // --radius-md on the top corners, as the web app's .calendar-head.
    val shape = MaterialTheme.shapes.small.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Spacer(Modifier.width(gutter))
        for (day in days) {
            val total = if (state.week.shows) dayTotalSeconds(state.entries, day, state.zone, now) else null
            DayHead(day, day == today, total, state.settings, editing, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayHead(
    day: LocalDate,
    isToday: Boolean,
    totalSeconds: Long?,
    settings: AccountSettings,
    editing: CalendarEditing?,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val words = remember(resources) { DateWords(resources) }
    val hue = TokklTheme.sectionHue
    val label = if (isToday) {
        resources.getString(
            R.string.history_today,
        )
    } else {
        resources.getString(UiR.string.history_day, words.weekday(day), words.short(day))
    }
    val spoken = totalSeconds?.let {
        stringResource(
            R.string.history_day_spoken,
            label,
            spokenDuration(resources, it),
        )
    } ?: label
    val addAction = addEntryAction(day, editing)
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1 / 2),
        modifier = modifier
            // The web app's 4px by 2px margin, inside the column's width, so heads and columns still line up.
            .padding(horizontal = TokklTheme.spacing.space1 / 2, vertical = TokklTheme.spacing.space1)
            .daySticker(hue, MaterialTheme.shapes.small, TokklTheme.extraColors.stickerOutline)
            .padding(horizontal = TokklTheme.spacing.space2, vertical = TokklTheme.spacing.space1)
            .clearAndSetSemantics {
                heading()
                contentDescription = spoken
                if (addAction != null) customActions = listOf(addAction)
            },
    ) {
        val headStyle = MaterialTheme.typography.labelMedium
        if (isToday) {
            // An inverted badge, the hue on the hue's text colour, so today is marked by its shape
            // as well as its colour on every hue.
            Text(
                text = resources.getString(R.string.history_today).uppercase(locale),
                style = headStyle,
                color = hue.fill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(hue.onFill, PillShape)
                    .padding(horizontal = TokklTheme.spacing.space1),
            )
        } else {
            Text(
                words.weekday(day).uppercase(locale),
                style = headStyle,
                color = hue.onFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            words.short(day),
            style = MaterialTheme.typography.bodySmall,
            color = hue.onFill,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (totalSeconds != null) {
            Text(
                text = settings.formatDuration(totalSeconds),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = hue.onFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** TalkBack's action on [day]'s head that opens the entry form for a new entry on the day, with [editing]. */
@Composable
internal fun addEntryAction(day: LocalDate, editing: CalendarEditing?): CustomAccessibilityAction? {
    if (editing == null) return null
    val resources = LocalResources.current
    val words = remember(resources) { DateWords(resources) }
    val label = stringResource(
        R.string.calendar_add_entry,
        resources.getString(UiR.string.history_day, words.weekday(day), words.short(day)),
    )
    return CustomAccessibilityAction(label) {
        editing.add(day)
        true
    }
}
