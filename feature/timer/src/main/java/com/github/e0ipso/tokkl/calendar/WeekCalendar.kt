package com.github.e0ipso.tokkl.calendar

import android.content.Context
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.feature.timer.R
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.ui.ScrollAwayColumn
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.math.sign

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
 *
 * A tap on a stopped entry's block calls [onEdit] with its id, and with the server's copy when
 * the phone does not keep it, so the entry form opens on it. In both views a long press then a
 * drag on empty space creates an entry (K2), which then opens in the form; on a block it moves
 * the entry, and on the block's top or bottom edge it moves that end. Each change goes to the
 * change queue through [model]. TalkBack gets the form instead: a block's click opens it, and each
 * day's head has an action that calls [onAdd] with the day for a new entry.
 *
 * Where the server stores no-time entries, an All day row sits between the heads, or the strip,
 * and the hour grid, with a chip for each day's no-time entries. A tap on a chip calls [onEdit]
 * as a block's does. The + in each day's cell, and TalkBack's action on the cell, call
 * [onAddAllDay] with the day for a new no-time entry.
 */
@Composable
fun WeekCalendar(
    model: CalendarViewModel,
    onEdit: (entryId: EntryId, older: TimeEntry?) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onAddAllDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemUTC(),
) {
    val edit by rememberUpdatedState(onEdit)
    val add by rememberUpdatedState(onAdd)
    val addAllDay by rememberUpdatedState(onAddAllDay)
    val editing = remember(model) {
        object : CalendarEditing {
            override fun create(day: LocalDate, fromMinute: Int, toMinute: Int) =
                model.create(day, fromMinute, toMinute) { id -> edit(id, null) }

            override fun move(entry: CalendarEntry, day: LocalDate, deltaMinutes: Int, dayMode: Boolean) =
                model.move(entry, day, deltaMinutes, dayMode)

            override fun resize(entry: CalendarEntry, edge: ResizeEdge, deltaMinutes: Int) = model.resize(
                entry,
                edge,
                deltaMinutes,
            )

            override fun edit(entry: CalendarEntry) = edit(entry.id, model.serverEntry(entry.id))

            override fun add(day: LocalDate) = add(day)

            override fun addAllDay(day: LocalDate) = addAllDay(day)
        }
    }
    val state by model.state.collectAsStateWithLifecycle()
    when (val current = state) {
        // The cache reads in moments, so this shows nothing rather than a flash of a spinner.
        CalendarState.Loading -> Unit

        is CalendarState.NoAccountTime -> Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.calendar_unknown_time_zone, current.timeZone),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        is CalendarState.Shown -> {
            val foregrounds by model.foregrounds.collectAsStateWithLifecycle()
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
                editing = editing,
            )
        }
    }
}

/**
 * [WeekCalendar] without its model, at [now]. [onStep] moves the chosen day by the days it gets,
 * [onSelect] chooses a day of the week, [onToday] goes back to today, and [onRetry] loads the
 * shown week again after it failed. With [dayMode], the grid shows the chosen day alone, under a
 * strip of the seven days of its week in place of the day heads, and the day slides in from the
 * side it comes from, unless the system asks for reduced motion. With [editing], taps, drags
 * and TalkBack actions change entries, while the week's entries are known; without it the
 * calendar only shows them.
 */
@Composable
fun CalendarContent(
    state: CalendarState.Shown,
    now: Instant,
    onStep: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    dayMode: Boolean = compactWindow(),
    editing: CalendarEditing? = null,
) {
    val edits = editing?.takeIf { state.week.shows }
    val gesture = remember { HeldGesture() }
    val drags = remember(
        edits,
        state.settings,
        dayMode,
    ) { edits?.let { CalendarDrags(it, state.settings, dayMode, gesture) } }
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
            if (dayMode) {
                WeekStrip(
                    state,
                    today,
                    now,
                    onSelect,
                    edits,
                )
            } else {
                DayHeads(days, today, state, now, gutter, edits)
            }
            if (state.allDayOffered) AllDayRow(days, state, now, gutter, edits)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) { detectSwipes(gesture) { swipe(it) } },
            ) {
                Grid(
                    days = days,
                    today = today,
                    blocks = blocks,
                    state = state,
                    now = now,
                    labels = labels,
                    labelStyle = labelStyle,
                    gutter = gutter,
                    slide = slide,
                    drags = drags,
                )
                if (!state.week.shows) WeekProblem(state.week, onRetry, Modifier.matchParentSize())
            }
        }
    }
}

/** Covers the grid while a week that the phone does not keep loads, or says why it did not. */
@Composable
private fun WeekProblem(week: WeekSource, onRetry: () -> Unit, modifier: Modifier = Modifier) {
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
private fun ColumnScope.Problem(title: String?, message: String, onRetry: () -> Unit) {
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
 * watches, so the grid still scrolls under it. A touch that [gesture] says was held for a drag is
 * no swipe, as gestureEditsNothing in the web app's WeekCalendar.tsx.
 */
private suspend fun PointerInputScope.detectSwipes(gesture: HeldGesture, onSwipe: (Long) -> Unit) {
    val minDistance = SwipeMinDistance.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gesture.held = false
        var last = down.position
        do {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change != null) last = change.position
        } while (change?.pressed == true)
        val dx = last.x - down.position.x
        val dy = last.y - down.position.y
        if (!gesture.held) swipeSteps(dx, dy, minDistance)?.let(onSwipe)
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
