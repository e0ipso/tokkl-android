package com.github.e0ipso.tokkl.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.DateWords
import com.github.e0ipso.tokkl.ui.spokenDuration
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SmallCornerShape
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.LocalDate
import com.github.e0ipso.tokkl.core.shared.R as SharedR

/**
 * The seven days of the chosen day's week, from the account's first day of the week, each with
 * its weekday, its date and, once the week's entries are known, its total. A tap on a day shows
 * it. The chosen day sits in Tokkl's frame and today's date in a filled circle, so neither is
 * told by colour alone. TalkBack reads each day as a tab, with its date and total, and with
 * [editing] offers an action that adds an entry on the day.
 *
 * Each day is at least a touch target wide. Where seven of them do not fit, as at the largest
 * display size on a phone, the strip scrolls sideways and keeps the chosen day in sight.
 */
@Composable
internal fun WeekStrip(
    state: CalendarState.Shown,
    today: LocalDate,
    now: Instant,
    onSelect: (LocalDate) -> Unit,
    editing: CalendarEditing?,
) {
    // --radius-md on the top corners, as the day heads.
    val shape = MaterialTheme.shapes.small.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    val totals = state.weekDays.map { day ->
        if (state.week.shows) {
            dayTotalSeconds(
                state.entries,
                day,
                state.zone,
                now,
            )
        } else {
            null
        }
    }
    val widestTotal = rememberWidestStripTotal(
        totals.mapNotNull { it?.let(state.settings::formatDuration) },
        stripTotalStyle(),
    )
    val inset = TokklTheme.spacing.space1 / 2
    val minDay = LocalMinimumInteractiveComponentSize.current.takeOrElse { 48.dp }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = (maxWidth - inset * 2) / state.weekDays.size >= minDay
        val scroll = rememberScrollState()
        if (!fits) KeepChosenDayInSight(scroll, state.weekDays.indexOf(state.selected), minDay, inset)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (fits) Modifier else Modifier.horizontalScroll(scroll))
                .padding(inset)
                .selectableGroup(),
        ) {
            for ((day, total) in state.weekDays.zip(totals)) {
                StripDay(
                    day = day,
                    selected = day == state.selected,
                    isToday = day == today,
                    totalSeconds = total,
                    widestTotal = widestTotal,
                    settings = state.settings,
                    onSelect = { onSelect(day) },
                    addAction = addEntryAction(day, editing),
                    modifier = if (fits) Modifier.weight(1f) else Modifier.width(minDay),
                )
            }
        }
    }
}

/** Scrolls a sideways strip of days [dayWidth] wide, after [inset], so the day at [index] shows whole. */
@Composable
private fun KeepChosenDayInSight(scroll: ScrollState, index: Int, dayWidth: Dp, inset: Dp) {
    val density = LocalDensity.current
    LaunchedEffect(index, scroll.viewportSize) {
        if (index < 0 || scroll.viewportSize == 0) return@LaunchedEffect
        val margin = with(density) { inset.roundToPx() }
        val day = with(density) { dayWidth.roundToPx() }
        stripScrollToShow(index, day, margin, scroll.value, scroll.viewportSize)?.let { scroll.scrollTo(it) }
    }
}

@Composable
private fun StripDay(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    totalSeconds: Long?,
    widestTotal: Int,
    settings: AccountSettings,
    onSelect: () -> Unit,
    addAction: CustomAccessibilityAction?,
    modifier: Modifier,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val words = remember(resources) { DateWords(resources) }
    val scheme = MaterialTheme.colorScheme
    val label = if (isToday) {
        resources.getString(
            R.string.history_today,
        )
    } else {
        resources.getString(SharedR.string.history_day, words.weekday(day), words.short(day))
    }
    val spoken = totalSeconds?.let {
        stringResource(
            R.string.history_day_spoken,
            label,
            spokenDuration(resources, it),
        )
    } ?: label
    val frame = if (selected) {
        Modifier
            .background(scheme.primaryContainer, SmallCornerShape)
            .border(FrameWidth, scheme.outline, SmallCornerShape)
    } else {
        Modifier
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1 / 2),
        modifier = modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(SmallCornerShape)
            .then(frame)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (addAction != null) customActions = listOf(addAction)
            }
            .padding(horizontal = TokklTheme.spacing.space1 / 2, vertical = TokklTheme.spacing.space1),
    ) {
        StripText(words.weekday(day).uppercase(locale), MaterialTheme.typography.labelMedium, scheme.onSurface)
        val date = day.dayOfMonth.toString()
        if (isToday) {
            // A filled circle, so today is marked by its shape as well as its colour.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(scheme.primary, CircleShape)
                    .padding(horizontal = TokklTheme.spacing.space1),
            ) {
                StripText(date, MaterialTheme.typography.bodySmall, scheme.onPrimary)
            }
        } else {
            StripText(date, MaterialTheme.typography.bodySmall, scheme.onSurface)
        }
        if (totalSeconds != null) {
            StripTotal(
                full = settings.formatDuration(totalSeconds),
                short = settings.formatDurationWithoutSeconds(totalSeconds),
                widestFull = widestTotal,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A strip day's total: [full] when the week's widest total, [widestFull] pixels at
 * [StripMinFontSize], fits the day on one line, else [short]. So a large font scale shows every
 * total whole, without seconds on every day alike, instead of cutting one with an ellipsis.
 * TalkBack reads the day's full total either way.
 */
@Composable
private fun StripTotal(full: String, short: String, widestFull: Int, color: Color) {
    BoxWithConstraints {
        StripText(if (widestFull <= constraints.maxWidth) full else short, stripTotalStyle(), color)
    }
}

/** The style of a strip day's total, with figures of one width so the totals line up. */
@Composable
private fun stripTotalStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

/** The width in pixels of the widest of [totals] on one line in [style] at [StripMinFontSize]. */
@Composable
private fun rememberWidestStripTotal(totals: List<String>, style: TextStyle): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(totals, style, measurer, density) {
        val smallest = style.copy(fontSize = StripMinFontSize)
        totals.maxOfOrNull {
            measurer.measure(
                it,
                smallest,
                softWrap = false,
                maxLines = 1,
                density = density,
            ).size.width
        } ?: 0
    }
}

/**
 * A line of a strip day, as large as [style] where it fits and smaller where it does not, since a
 * seventh of a phone's width holds little at large font scales. TalkBack reads the day whole.
 */
@Composable
private fun StripText(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = StripMinFontSize, maxFontSize = style.fontSize),
    )
}
