package com.github.e0ipso.tokkl.calendar

import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.reports.DateWords
import com.github.e0ipso.tokkl.reports.projectMark
import com.github.e0ipso.tokkl.reports.spokenDuration
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.ScrollAwayColumn
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SmallCorner
import com.github.e0ipso.tokkl.ui.theme.SmallCornerShape
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.delay

/**
 * The calendar in the Timer page's Calendar view (docs/PRD.md K1): the week from the account's
 * first day of the week, with an hour row for each hour of the day, and each entry as a block as
 * long as it lasts, in the account's timezone. On a compact window, a phone held upright, it
 * shows the chosen day alone under a strip of its week's days and their totals, a tap on a day
 * of the strip shows it, and the arrows and a swipe move a day instead of a week. On a
 * window of compact height, a phone held sideways, the toolbar scrolls away as the grid scrolls
 * on, after the Timer page's top bar, timer and switch, and the day heads stay. When the page
 * opens with those scrolled away, the toolbar starts scrolled away too. The running entry's block
 * grows with the phone's clock, once a minute while the page shows, and sends no request.
 */
@Composable
fun WeekCalendar(model: CalendarModel, modifier: Modifier = Modifier, clock: Clock = Clock.systemUTC()) {
    val state by model.state.collectAsState()
    when (val current = state) {
        // The cache reads in moments, so this shows nothing rather than a flash of a spinner.
        CalendarState.Loading -> Unit
        is CalendarState.NoAccountTime -> Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.calendar_unknown_time_zone, current.timeZone), style = MaterialTheme.typography.bodyLarge)
        }
        is CalendarState.Shown -> {
            val foregrounds by model.foregrounds.collectAsState()
            // Showing a week is the event that loads what the phone does not keep, and so is
            // coming back to the foreground with the week on screen.
            LaunchedEffect(current.weekStart, foregrounds) { model.loadWeek(current.weekStart) }
            val ticking = current.entries.any { it.running } || current.today in current.weekDays
            CalendarContent(
                state = current,
                now = rememberMinuteClock(clock, ticking),
                onStep = model::step,
                onSelect = model::select,
                onToday = model::showToday,
                onRetry = { model.loadWeek(current.weekStart) },
                modifier = modifier,
            )
        }
    }
}

/**
 * [WeekCalendar] without its model, at [now]. [onStep] moves the chosen day by the days it gets,
 * [onSelect] chooses a day of the week, [onToday] goes back to today, and [onRetry] loads the
 * shown week again after it failed. With [dayMode], the grid shows the chosen day alone, under a
 * strip of the seven days of its week in place of the day heads, and the day slides in from the
 * side it comes from, unless the system asks for reduced motion.
 */
@Composable
internal fun CalendarContent(
    state: CalendarState.Shown,
    now: Instant,
    onStep: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    dayMode: Boolean = compactWindow(),
) {
    val days = if (dayMode) listOf(state.selected) else state.weekDays
    val stepDays = if (dayMode) 1L else 7L
    val today = now.atZone(state.zone).toLocalDate()
    val blocks = remember(state.entries, days, state.zone, now) { calendarBlocks(state.entries, days, state.zone, now) }
    val labels = remember(state.settings.twelveHour) { hourLabels(state.settings) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val gutter = rememberGutterWidth(labels, labelStyle)
    val swipe by rememberUpdatedState<(Long) -> Unit> { onStep(it * stepDays) }
    val slide = rememberDaySlide(state.selected, dayMode)
    ScrollAwayColumn(
        modifier = modifier.fillMaxSize(),
        top = {
            Toolbar(state, dayMode, now, onStep = { onStep(it * stepDays) }, onToday = onToday)
            Spacer(Modifier.height(TokklTheme.spacing.inGroup))
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (dayMode) WeekStrip(state, today, now, onSelect) else DayHeads(days, today, state, now, gutter)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) { detectSwipes { swipe(it) } },
            ) {
                Grid(days, today, blocks, state.settings, state.zone, now, labels, labelStyle, gutter, slide)
                if (!state.week.shows) WeekProblem(state.week, onRetry, Modifier.matchParentSize())
            }
        }
    }
}

/** The arrows, the way back to today, the week or day shown and the week's total. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(state: CalendarState.Shown, dayMode: Boolean, now: Instant, onStep: (Long) -> Unit, onToday: () -> Unit) {
    val resources = LocalResources.current
    val words = remember(resources) { DateWords(resources) }
    val range = if (dayMode) {
        resources.getString(R.string.history_day, words.weekday(state.selected), words.short(state.selected))
    } else {
        resources.getString(R.string.reports_range, words.short(state.weekStart), words.short(state.weekStart.plusDays(6)))
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
                    painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(if (dayMode) R.string.calendar_previous_day else R.string.calendar_previous_week),
                )
            }
            OutlinedButton(onClick = onToday, shape = MaterialTheme.shapes.small) {
                Text(stringResource(if (dayMode) R.string.calendar_today else R.string.calendar_this_week))
            }
            IconButton(onClick = { onStep(1) }) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(if (dayMode) R.string.calendar_next_day else R.string.calendar_next_week),
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
 * and, once the week's entries are known, its total.
 */
@Composable
private fun DayHeads(days: List<LocalDate>, today: LocalDate, state: CalendarState.Shown, now: Instant, gutter: Dp) {
    // --radius-md on the top corners, as the web app's .calendar-head.
    val shape = MaterialTheme.shapes.small.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Spacer(Modifier.width(gutter))
        for (day in days) {
            val total = if (state.week.shows) dayTotalSeconds(state.entries, day, state.zone, now) else null
            DayHead(day, day == today, total, state.settings, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayHead(day: LocalDate, isToday: Boolean, totalSeconds: Long?, settings: AccountSettings, modifier: Modifier) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val words = remember(resources) { DateWords(resources) }
    val line = MaterialTheme.colorScheme.outlineVariant
    val label = if (isToday) resources.getString(R.string.history_today) else resources.getString(R.string.history_day, words.weekday(day), words.short(day))
    val spoken = totalSeconds?.let { stringResource(R.string.history_day_spoken, label, spokenDuration(resources, it)) } ?: label
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1 / 2),
        modifier = modifier
            .drawBehind { drawLine(line, Offset.Zero, Offset(0f, size.height), DividerDefaults.Thickness.toPx()) }
            .padding(horizontal = TokklTheme.spacing.space2, vertical = TokklTheme.spacing.space1)
            .clearAndSetSemantics {
                heading()
                contentDescription = spoken
            },
    ) {
        val headStyle = MaterialTheme.typography.labelMedium
        if (isToday) {
            // A filled badge, so today is marked by its shape as well as its colour.
            Text(
                text = resources.getString(R.string.history_today).uppercase(locale),
                style = headStyle,
                color = TokklTheme.extraColors.onPrimaryFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(TokklTheme.extraColors.primaryFill, PillShape)
                    .padding(horizontal = TokklTheme.spacing.space1),
            )
        } else {
            Text(words.weekday(day).uppercase(locale), style = headStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(words.short(day), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (totalSeconds != null) {
            Text(
                text = settings.formatDuration(totalSeconds),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The seven days of the chosen day's week, from the account's first day of the week, each with
 * its weekday, its date and, once the week's entries are known, its total. A tap on a day shows
 * it. The chosen day sits in Tokkl's frame and today's date in a filled circle, so neither is
 * told by colour alone. TalkBack reads each day as a tab, with its date and total.
 */
@Composable
private fun WeekStrip(state: CalendarState.Shown, today: LocalDate, now: Instant, onSelect: (LocalDate) -> Unit) {
    // --radius-md on the top corners, as the day heads.
    val shape = MaterialTheme.shapes.small.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    val totals = state.weekDays.map { day -> if (state.week.shows) dayTotalSeconds(state.entries, day, state.zone, now) else null }
    val widestTotal = rememberWidestStripTotal(totals.mapNotNull { it?.let(state.settings::formatDuration) }, stripTotalStyle())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(TokklTheme.spacing.space1 / 2)
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
                modifier = Modifier.weight(1f),
            )
        }
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
    modifier: Modifier,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val words = remember(resources) { DateWords(resources) }
    val scheme = MaterialTheme.colorScheme
    val label = if (isToday) resources.getString(R.string.history_today) else resources.getString(R.string.history_day, words.weekday(day), words.short(day))
    val spoken = totalSeconds?.let { stringResource(R.string.history_day_spoken, label, spokenDuration(resources, it)) } ?: label
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
            .clearAndSetSemantics { contentDescription = spoken }
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
        totals.maxOfOrNull { measurer.measure(it, smallest, softWrap = false, maxLines = 1, density = density).size.width } ?: 0
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

/** The hour labels and a column for each of [days], scrolled to the morning when it first shows. */
@Composable
private fun Grid(
    days: List<LocalDate>,
    today: LocalDate,
    blocks: List<CalendarBlock>,
    settings: AccountSettings,
    zone: ZoneId,
    now: Instant,
    labels: List<String>,
    labelStyle: TextStyle,
    gutter: Dp,
    slide: Animatable<Float, AnimationVector1D>,
) {
    val scroll = rememberScrollState()
    val distance = with(LocalDensity.current) { SlideDistance.toPx() }
    val hour = with(LocalDensity.current) { HourHeight.roundToPx() }
    // The hour's line sits a little under the top, so its label shows whole.
    val above = with(LocalDensity.current) { TokklSpacing.space4.roundToPx() }
    LaunchedEffect(Unit) { scroll.scrollTo(FIRST_HOUR * hour - above) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(HourHeight * HOURS),
        ) {
            Gutter(labels, labelStyle, Modifier.width(gutter))
            for (day in days) {
                DayColumn(
                    isToday = day == today,
                    blocks = blocks.filter { it.day == day },
                    settings = settings,
                    zone = zone,
                    now = now,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = slide.value * distance
                            alpha = 1f - abs(slide.value)
                        },
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Each hour's label, centred on its line; the first sits under the top. TalkBack skips them, since every block says its times. */
@Composable
private fun Gutter(labels: List<String>, style: TextStyle, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val end = TokklTheme.spacing.space1
    Layout(
        content = { labels.forEach { Text(it, style = style, color = muted, maxLines = 1) } },
        modifier = modifier
            .fillMaxHeight()
            .clearAndSetSemantics {},
    ) { measurables, constraints ->
        val hour = HourHeight.roundToPx()
        val endPadding = end.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val y = if (index == 0) 0 else index * hour - placeable.height / 2
                placeable.place(constraints.maxWidth - placeable.width - endPadding, y)
            }
        }
    }
}

/** One day: its hour lines, today's tint and now line, and its blocks side by side where they overlap. */
@Composable
private fun DayColumn(
    isToday: Boolean,
    blocks: List<CalendarBlock>,
    settings: AccountSettings,
    zone: ZoneId,
    now: Instant,
    modifier: Modifier,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val tint = mix(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, TODAY_TINT)
    val running = TokklTheme.extraColors.running
    val nowMinutes = now.atZone(zone).let { it.hour * 60 + it.minute }
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .drawBehind {
                if (isToday) drawRect(tint)
                val stroke = DividerDefaults.Thickness.toPx()
                drawLine(line, Offset.Zero, Offset(0f, size.height), stroke)
                val hour = HourHeight.toPx()
                for (h in 0 until HOURS) drawLine(line, Offset(0f, h * hour), Offset(size.width, h * hour), stroke)
            }
            .drawWithContent {
                drawContent()
                if (isToday) {
                    val y = nowMinutes / 60f * HourHeight.toPx()
                    drawLine(running, Offset(0f, y), Offset(size.width, y), FrameWidth.toPx())
                    drawCircle(running, radius = TokklSpacing.space1.toPx(), center = Offset(0f, y))
                }
            },
    ) {
        for (block in blocks) Block(block, maxWidth, settings, zone, now)
    }
}

/**
 * An entry's block: its description, project and duration as far as they fit, marked when the
 * server does not have the entry yet, and all of it, with its times, to TalkBack.
 */
@Composable
private fun Block(block: CalendarBlock, columnWidth: Dp, settings: AccountSettings, zone: ZoneId, now: Instant) {
    val resources = LocalResources.current
    val entry = block.entry
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val runningColour = TokklTheme.extraColors.running
    val tint = mix(projectColour(entry.projectColor) ?: muted, scheme.surface, BLOCK_TINT)
    val bar = if (entry.running) runningColour else projectMark(entry.projectColor, TokklTheme.extraColors.projectMix, muted)
    val laneWidth = columnWidth / block.lanes
    val height = max(HourHeight * ((block.bottom - block.top) / 60).toFloat(), TokklSpacing.space1)
    val noDescription = stringResource(R.string.history_no_description)
    val heading = entry.description.ifBlank { entry.project ?: noDescription }
    val duration = settings.formatDuration(entry.seconds(now))
    val spoken = spokenBlock(resources, entry, settings, zone, now)
    Box(
        Modifier
            .offset(x = laneWidth * block.lane + BlockGap, y = HourHeight * (block.top / 60).toFloat())
            .width((laneWidth - BlockGap * 2).coerceAtLeast(0.dp))
            .height(height)
            .clip(SmallCornerShape)
            .background(tint)
            .drawBehind {
                val barWidth = BlockBar.toPx()
                drawRect(bar, size = Size(barWidth, size.height))
                if (entry.running) {
                    // Dashes as well as the colour mark the running entry.
                    val stroke = FrameWidth.toPx()
                    val dash = TokklSpacing.space1.toPx()
                    drawRoundRect(
                        color = runningColour,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(SmallCorner.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                    )
                }
            }
            .padding(start = BlockBar + TokklTheme.spacing.space1, end = TokklTheme.spacing.space1)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val twoLines = block.bottom - block.top >= TWO_LINE_MINUTES
        if (entry.unsent) {
            UnsentBlockText { mark -> BlockText(entry, heading, duration, twoLines, mark) }
        } else {
            BlockText(entry, heading, duration, twoLines, mark = false)
        }
    }
}

/**
 * A block's text: its heading over its project and duration when [twoLines], or else its heading
 * and project on one line. With [mark], the unsent icon starts the first line.
 */
@Composable
private fun BlockText(entry: CalendarEntry, heading: String, duration: String, twoLines: Boolean, mark: Boolean) {
    val resources = LocalResources.current
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val strong = MaterialTheme.typography.labelSmall
    val small = MaterialTheme.typography.bodySmall
    if (twoLines) {
        Column {
            MarkedLine(mark, strong) {
                Text(heading, style = strong, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = buildAnnotatedString {
                    if (entry.project != null) append(entry.project + resources.getString(R.string.calendar_block_separator))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onSurface)) { append(duration) }
                },
                style = small.copy(fontFeatureSettings = "tnum"),
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        MarkedLine(mark, small) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onSurface)) { append(heading) }
                    if (entry.description.isNotBlank() && entry.project != null) {
                        append(resources.getString(R.string.calendar_block_separator) + entry.project)
                    }
                },
                style = small,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** [line] after the unsent icon, which is as tall as a line of [style], when [mark]; else [line] alone. */
@Composable
private fun MarkedLine(mark: Boolean, style: TextStyle, line: @Composable () -> Unit) {
    if (mark) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
            Icon(
                painterResource(R.drawable.ic_unsent),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(with(LocalDensity.current) { style.lineHeight.toDp() }),
            )
            line()
        }
    } else {
        line()
    }
}

/**
 * An unsent entry's text, marked as the history marks it, by more than colour: the history's NOT
 * SENT label under the text where the block has room for the whole label, or else the unsent icon
 * at the start of the first line. [text] draws the text, with the icon when it gets true. The
 * block keeps "Not sent" in what TalkBack reads, so the label and icon stay out of it.
 */
@Composable
private fun UnsentBlockText(text: @Composable (mark: Boolean) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Layout(
        content = {
            text(false)
            Text(
                text = stringResource(R.string.history_unsent).uppercase(locale),
                style = TokklTheme.smallCapsLabelTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            text(true)
        },
    ) { measurables, constraints ->
        val (plain, label, marked) = measurables
        // Each part as tall as its text; the block clips what goes past its end.
        val free = Constraints(maxWidth = constraints.maxWidth)
        val labelFits = label.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val lines = plain.measure(free)
        val words = label.measure(free)
        if (labelFits && lines.height + words.height <= constraints.maxHeight) {
            layout(constraints.maxWidth, lines.height + words.height) {
                lines.place(0, 0)
                words.place(0, lines.height)
            }
        } else {
            val withIcon = marked.measure(free)
            layout(constraints.maxWidth, constraints.constrainHeight(withIcon.height)) { withIcon.place(0, 0) }
        }
    }
}

/** What TalkBack reads for a block: description, project and task, times, duration, and whether it runs or is unsent. */
private fun spokenBlock(resources: Resources, entry: CalendarEntry, settings: AccountSettings, zone: ZoneId, now: Instant): String {
    val start = settings.formatTime(entry.start, zone)
    val times = entry.stop?.let { resources.getString(R.string.history_time_range_spoken, start, settings.formatTime(it, zone)) }
        ?: resources.getString(R.string.calendar_running_spoken, start)
    return listOfNotNull(
        entry.description.ifBlank { resources.getString(R.string.history_no_description) },
        entry.project,
        entry.task,
        times,
        spokenDuration(resources, entry.seconds(now)),
        if (entry.unsent) resources.getString(R.string.history_unsent) else null,
    ).joinToString(", ")
}

/** Covers the grid while a week that the phone does not keep loads, or says why it did not. */
@Composable
private fun WeekProblem(week: WeekSource, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(DividerDefaults.Thickness, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(rememberScrollState())
            .padding(TokklTheme.spacing.space4),
    ) {
        when (week) {
            WeekSource.Cached, WeekSource.Loaded -> Unit
            WeekSource.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(Modifier.size(TokklTheme.spacing.space6))
                Spacer(Modifier.width(TokklTheme.spacing.space3))
                Text(stringResource(R.string.calendar_loading), style = MaterialTheme.typography.bodyLarge)
            }
            WeekSource.Unreachable -> Problem(
                title = stringResource(R.string.calendar_offline_title),
                message = stringResource(R.string.calendar_offline_message),
                onRetry = onRetry,
            )
            WeekSource.AuthFailed -> Problem(null, stringResource(R.string.calendar_error_auth), onRetry)
            WeekSource.TrustRefused -> Problem(null, stringResource(R.string.calendar_error_trust), onRetry)
            is WeekSource.Failed -> Problem(
                title = null,
                message = week.status?.let { stringResource(R.string.calendar_error_status, it) }
                    ?: stringResource(R.string.history_error_unreadable),
                onRetry = onRetry,
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

/**
 * Calls [onSwipe] with 1 after a swipe to the left and -1 after one to the right, as classifySwipe
 * in the web app's lib/calendarDay.ts: at least [SwipeMinDistance] sideways and [SWIPE_RATIO]
 * times further sideways than up or down, so scrolling the grid never changes the week. It only
 * watches, so the grid still scrolls under it.
 */
private suspend fun PointerInputScope.detectSwipes(onSwipe: (Long) -> Unit) {
    val minDistance = SwipeMinDistance.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var last = down.position
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            last = change.position
            if (!change.pressed) break
        }
        val dx = last.x - down.position.x
        val dy = last.y - down.position.y
        if (abs(dx) >= minDistance && abs(dx) >= SWIPE_RATIO * abs(dy)) onSwipe(if (dx < 0) 1L else -1L)
    }
}

/** The current time, read from [clock] each minute while [ticking] and the screen shows, never from the server. */
@Composable
private fun rememberMinuteClock(clock: Clock, ticking: Boolean): Instant {
    var now by remember(clock) { mutableStateOf(clock.instant()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (ticking) {
        LaunchedEffect(clock, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = clock.instant()
                    delay(MINUTE_MS - Math.floorMod(now.toEpochMilli(), MINUTE_MS))
                }
            }
        }
    }
    return now
}

/**
 * Where the column of [day] is on its way in, from 1 or -1, [SlideDistance] towards the end or
 * the start, to 0 in place. After a step forward it comes from the end, as a swipe to the start
 * pulls it, and after a step back from the start. It fades in on the way. Only in [dayMode], and
 * not when the system asks for reduced motion, where the day changes at once.
 */
@Composable
private fun rememberDaySlide(day: LocalDate, dayMode: Boolean): Animatable<Float, AnimationVector1D> {
    val context = LocalContext.current
    val shown = remember { ShownDay(day) }
    val slide = remember(day) {
        val side = day.compareTo(shown.day).sign
        Animatable(if (dayMode && side != 0 && !reducedMotion(context)) side.toFloat() else 0f)
    }
    SideEffect { shown.day = day }
    LaunchedEffect(slide) { slide.animateTo(0f, tween(SLIDE_MS, easing = FastOutSlowInEasing)) }
    return slide
}

/** The day the calendar last showed. */
private class ShownDay(var day: LocalDate)

/** Whether the system asks for reduced motion: Remove animations sets the animator duration scale to 0. */
private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Whether the window is compact, a phone held upright, by Material's window size classes. */
@Composable
private fun compactWindow(): Boolean {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return TokklSpacing.isCompact(width)
}

/** The width of the hour labels' column: the widest of [labels] in [style], and the gap after it. */
@Composable
private fun rememberGutterWidth(labels: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, measurer, density) {
        val widest = labels.maxOf { measurer.measure(it, style, maxLines = 1, density = density).size.width }
        with(density) { widest.toDp() } + TokklSpacing.space2
    }
}

/** Each hour's label in the account's clock format: `07:00`, or `7:00 AM`. */
private fun hourLabels(settings: AccountSettings): List<String> =
    (0 until HOURS).map { hour -> settings.formatTime(Instant.ofEpochSecond(hour * SECONDS_PER_HOUR), ZoneOffset.UTC) }

/** [colour] at [fraction] over [base], as CSS's `color-mix(in srgb, colour fraction, base)`. */
private fun mix(colour: Color, base: Color, fraction: Float): Color = Color(
    red = colour.red * fraction + base.red * (1 - fraction),
    green = colour.green * fraction + base.green * (1 - fraction),
    blue = colour.blue * fraction + base.blue * (1 - fraction),
)

/** A project's `#rrggbb` colour, or null when it has none the app can read. */
private fun projectColour(hex: String?): Color? =
    hex?.takeIf { HEX.matches(it) }?.let { Color(0xFF000000 or it.substring(1).toLong(16)) }

private val HEX = Regex("#[0-9a-fA-F]{6}")

/** The height of an hour row: HOUR_HEIGHT in the web app's WeekCalendar.tsx, 48px, which is --space-8. */
private val HourHeight: Dp = TokklSpacing.space8

private const val HOURS = 24

/** The hour the grid shows at the top when it first shows, as in the web app. */
private const val FIRST_HOUR = 7

/** A block this many minutes tall or more shows two lines, as in the web app. */
private const val TWO_LINE_MINUTES = 45.0

/** How much of a block is its project's colour over surface, 18% in the web app's styles.css. */
private const val BLOCK_TINT = 0.18f

/** How much of today's column is --state-selected over surface: --calendar-today, 35%. */
private const val TODAY_TINT = 0.35f

/** SWIPE_MIN_PX in the web app's lib/calendarDay.ts. */
private val SwipeMinDistance: Dp = 50.dp

/** SWIPE_RATIO in the web app's lib/calendarDay.ts. */
private const val SWIPE_RATIO = 1.5f

/** The gap on each side of a block, 2px in WeekCalendar.tsx: half of --space-1. */
private val BlockGap: Dp = TokklSpacing.space1 / 2

/** The bar in the project's colour on a block's start side, --space-1. The web app's is 5px. */
private val BlockBar: Dp = TokklSpacing.space1

/** `--radius-pill`. */
private val PillShape = RoundedCornerShape(percent = 50)

/** How far a day's column travels as it slides in: Material's shared axis transition moves 30dp. */
private val SlideDistance: Dp = 30.dp

/** How long a day's column takes to slide in: Material's shared axis transition takes 300ms. */
private const val SLIDE_MS = 300

/** The smallest a line of a strip day shrinks to. */
private val StripMinFontSize = 6.sp


private const val MINUTE_MS = 60_000L

private const val SECONDS_PER_HOUR = 3600L
