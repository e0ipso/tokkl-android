package com.github.e0ipso.tokkl.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SmallCornerShape
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The hour labels and a column for each of [days], scrolled to the morning when it first shows.
 * With [drags], each column takes taps and drags, and holds the drag in progress.
 */
@Composable
internal fun Grid(
    days: List<LocalDate>,
    today: LocalDate,
    blocks: List<CalendarBlock>,
    state: CalendarState.Shown,
    now: Instant,
    labels: List<String>,
    labelStyle: TextStyle,
    gutter: Dp,
    slide: Animatable<Float, AnimationVector1D>,
    drags: CalendarDrags?,
) {
    val drag = remember { mutableStateOf<CalendarDrag?>(null) }
    val scroll = rememberScrollState()
    val distance = with(LocalDensity.current) { SlideDistance.toPx() }
    val hour = with(LocalDensity.current) { HourHeight.roundToPx() }
    // The hour's line sits a little under the top, so its label shows whole.
    val above = with(LocalDensity.current) { TokklSpacing.space4.roundToPx() }
    var initiallyPositioned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initiallyPositioned) {
            scroll.scrollTo(FIRST_HOUR * hour - above)
            initiallyPositioned = true
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Row(Modifier.fillMaxWidth().height(HourHeight * HOURS)) {
            Gutter(labels, labelStyle, Modifier.width(gutter))
            for (day in days) {
                DayColumn(
                    day = day,
                    isToday = day == today,
                    blocks = blocks.filter { it.day == day },
                    settings = state.settings,
                    zone = state.zone,
                    now = now,
                    drag = drag,
                    drags = drags,
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

/**
 * Each hour's label, centred on its line; the first sits under the top. TalkBack skips them, since every block says its
 * times.
 */
@Composable
private fun Gutter(labels: List<String>, style: TextStyle, modifier: Modifier = Modifier) {
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

/**
 * One day's column. With [drags], [drag] holds the drag in progress for every column, and the
 * dragged block, or the new entry's outline, shows where the drag would save it.
 */
@Composable
private fun DayColumn(
    day: LocalDate,
    isToday: Boolean,
    blocks: List<CalendarBlock>,
    settings: AccountSettings,
    zone: ZoneId,
    now: Instant,
    drag: MutableState<CalendarDrag?>,
    drags: CalendarDrags?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val geometry = remember(density) {
        with(density) { BlockGeometry(HourHeight.toPx(), BlockGap.toPx(), TokklSpacing.space1.toPx(), EdgeZone.toPx()) }
    }
    val shown by rememberUpdatedState(blocks)
    val gestures = if (drags == null) {
        Modifier
    } else {
        Modifier.pointerInput(day, drags, geometry) {
            detectCalendarGestures(
                targetAt = { position ->
                    geometry.targetAt(position, size.width.toFloat(), shown, day, zone, drags.step)
                },
                onTap = drags.editing::edit,
                onHold = { target, position ->
                    drags.gesture.held = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag.value = target.drag(day, position.y)
                },
                // The handlers read and write the state itself, since events can come faster than frames.
                onMove = { position -> drag.value = drag.value?.let { drags.moved(it, position.y, geometry.hour) } },
                onLift = {
                    val done = drag.value
                    drag.value = null
                    done?.let(drags::finish)
                },
                onCancel = { drag.value = null },
            )
        }
    }
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
            }
            .then(gestures),
    ) {
        val current = drag.value
        for (block in blocks) {
            // A move takes every block of the entry along; a resize only the block of the edge.
            val change = (current as? CalendarDrag.Change)?.takeIf {
                it.entry.id == block.entry.id &&
                    (it.kind == DragKind.MOVE || it.day == block.day)
            }
            val dragged = change != null
            val placed = if (change != null && drags != null) drags.preview(block, change) else block
            val onEdit = drags?.takeIf { !block.entry.running }?.let { { it.editing.edit(block.entry) } }
            Block(placed, maxWidth, settings, zone, now, dragged, onEdit)
        }
        if (current is CalendarDrag.Create && current.day == day &&
            current.fromMinute != current.toMinute
        ) {
            NewEntryOutline(current)
        }
    }
}

/** Where a new entry's drag would put it: an outline in Tokkl's frame, which TalkBack skips. */
@Composable
private fun NewEntryOutline(drag: CalendarDrag.Create) {
    val top = minOf(drag.fromMinute, drag.toMinute)
    val minutes = abs(drag.toMinute - drag.fromMinute)
    Box(
        Modifier
            .offset(x = BlockGap, y = HourHeight * (top / 60f))
            .fillMaxWidth()
            .padding(end = BlockGap * 2)
            .height(HourHeight * (minutes / 60f))
            .background(MaterialTheme.colorScheme.primaryContainer, SmallCornerShape)
            .border(FrameWidth, MaterialTheme.colorScheme.outline, SmallCornerShape)
            .clearAndSetSemantics {},
    )
}
