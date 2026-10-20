package com.github.e0ipso.tokkl.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.LocalDate
import java.time.ZoneId

/** What the calendar's taps, drags and TalkBack actions do. Without it the calendar only shows entries. */
internal interface CalendarEditing {
    /** Creates the entry that a drag from clock minute [fromMinute] to [toMinute] on [day] makes. */
    fun create(day: LocalDate, fromMinute: Int, toMinute: Int)

    /** Moves the stopped [entry] [deltaMinutes] from its block on [day]; [dayMode] keeps it on that day. */
    fun move(entry: CalendarEntry, day: LocalDate, deltaMinutes: Int, dayMode: Boolean)

    /** Moves the [edge] of the stopped [entry] [deltaMinutes]. */
    fun resize(entry: CalendarEntry, edge: ResizeEdge, deltaMinutes: Int)

    /** Opens the entry form on the stopped [entry], where its times change as a move or resize would. */
    fun edit(entry: CalendarEntry)

    /** Opens the entry form for a new entry on [day], as a drag on empty space would make. */
    fun add(day: LocalDate)
}

/** What a drag in progress does to a block. */
internal enum class DragKind { MOVE, RESIZE_START, RESIZE_END }

/** A drag in progress, from a long press until the finger lifts. */
internal sealed interface CalendarDrag {
    val day: LocalDate

    /** A new entry on [day] from clock minute [fromMinute], where the press was, to [toMinute]. */
    data class Create(override val day: LocalDate, val fromMinute: Int, val toMinute: Int) : CalendarDrag

    /**
     * A move or resize of [entry] from its block on [day], from clock minute [top] to [bottom],
     * pressed at [originY] pixels down the column, by [deltaMinutes] so far.
     */
    data class Change(
        val entry: CalendarEntry,
        override val day: LocalDate,
        val kind: DragKind,
        val top: Double,
        val bottom: Double,
        val originY: Float,
        val deltaMinutes: Int,
    ) : CalendarDrag
}

/** Whether the touch on the grid that is down now was held for a drag, so it is no swipe. */
internal class HeldGesture {
    var held: Boolean = false
}

/**
 * The calendar's drags for [editing], snapped to the account's drag step as the web app's
 * WeekCalendar.tsx snaps them. In [dayMode] a move keeps the entry on the shown day.
 */
internal class CalendarDrags(
    val editing: CalendarEditing,
    settings: AccountSettings,
    private val dayMode: Boolean,
    val gesture: HeldGesture,
) {
    private val resolution = settings.dragResolution

    val step = dragStep(resolution)

    /** [drag] with the finger at [y] pixels down its column, where an hour is [hour] pixels tall. */
    fun moved(drag: CalendarDrag, y: Float, hour: Float): CalendarDrag = when (drag) {
        is CalendarDrag.Create -> drag.copy(toMinute = snapToStep(y / hour * 60.0, step))
        is CalendarDrag.Change -> {
            val snapped = (Math.round((y - drag.originY) / hour * 60.0 / step) * step).toInt()
            val delta = if (dayMode && drag.kind == DragKind.MOVE) clampDayMove(snapped, drag.top, drag.bottom) else snapped
            drag.copy(deltaMinutes = delta)
        }
    }

    /** Where [block] shows while [drag] moves or resizes its entry: where the drop would save it. */
    fun preview(block: CalendarBlock, drag: CalendarDrag.Change): CalendarBlock {
        val delta = drag.deltaMinutes
        return when (drag.kind) {
            DragKind.MOVE -> {
                val top = droppedMinute(block.top, delta, resolution)
                block.copy(top = top, bottom = block.bottom + top - block.top)
            }
            DragKind.RESIZE_START -> block.copy(top = minOf(droppedMinute(block.top, delta, resolution), block.bottom - step))
            DragKind.RESIZE_END -> block.copy(bottom = maxOf(block.top + step, droppedMinute(block.bottom, delta, resolution)))
        }
    }

    /**
     * Saves [drag] as the finger lifts. A create that never grew makes nothing, and a block held
     * and let go where it was opens in the entry form, as in the web app.
     */
    fun finish(drag: CalendarDrag) {
        when (drag) {
            is CalendarDrag.Create -> editing.create(drag.day, drag.fromMinute, drag.toMinute)
            is CalendarDrag.Change -> when {
                drag.deltaMinutes == 0 -> editing.edit(drag.entry)
                drag.kind == DragKind.MOVE -> editing.move(drag.entry, drag.day, drag.deltaMinutes, dayMode)
                drag.kind == DragKind.RESIZE_START -> editing.resize(drag.entry, ResizeEdge.START, drag.deltaMinutes)
                else -> editing.resize(drag.entry, ResizeEdge.END, drag.deltaMinutes)
            }
        }
    }
}

/** What a press on a day's column would drag. */
internal sealed interface DragTarget {
    /** The drag that a long press at [y] pixels down the column of [day] starts. */
    fun drag(day: LocalDate, y: Float): CalendarDrag

    /** Empty space at clock minute [minute], already on the drag step. */
    data class Empty(val minute: Int) : DragTarget {
        override fun drag(day: LocalDate, y: Float): CalendarDrag = CalendarDrag.Create(day, minute, minute)
    }

    /** The stopped entry's [block], in its middle or on one of its own edges as [kind] says. */
    data class OnBlock(val block: CalendarBlock, val kind: DragKind) : DragTarget {
        override fun drag(day: LocalDate, y: Float): CalendarDrag =
            CalendarDrag.Change(block.entry, block.day, kind, block.top, block.bottom, y, deltaMinutes = 0)
    }
}

/**
 * Where blocks sit in a day's column, in pixels, as the calendar draws them: an hour is [hour]
 * tall, a block has [gap] on each side of its lane and is at least [minHeight] tall. A press
 * within [edge] of a block's top or bottom, and within a third of a short block, takes that edge.
 */
internal class BlockGeometry(val hour: Float, private val gap: Float, private val minHeight: Float, private val edge: Float) {

    /**
     * What a press at [position] in the column of [day], [width] pixels wide, would drag: the
     * topmost of [blocks] under it, or empty space. A block's edge resizes only where it is the
     * entry's own start or stop, not where the block is cut off at midnight. The running entry's
     * block gives null, since the timer edits it.
     */
    fun targetAt(position: Offset, width: Float, blocks: List<CalendarBlock>, day: LocalDate, zone: ZoneId, step: Int): DragTarget? {
        val hit = blocks.lastOrNull { contains(it, position, width) } ?: return DragTarget.Empty(snapToStep(position.y / hour * 60.0, step))
        val stop = hit.entry.stop ?: return null
        val top = top(hit)
        val bottom = top + height(hit)
        val reach = minOf(edge, (bottom - top) / 3)
        val ownStart = hit.entry.start >= day.atStartOfDay(zone).toInstant()
        val ownEnd = stop <= day.plusDays(1).atStartOfDay(zone).toInstant()
        val kind = when {
            ownStart && position.y < top + reach -> DragKind.RESIZE_START
            ownEnd && position.y > bottom - reach -> DragKind.RESIZE_END
            else -> DragKind.MOVE
        }
        return DragTarget.OnBlock(hit, kind)
    }

    private fun contains(block: CalendarBlock, position: Offset, width: Float): Boolean {
        val lane = width / block.lanes
        val left = lane * block.lane + gap
        val top = top(block)
        return position.x >= left && position.x <= left + lane - gap * 2 && position.y >= top && position.y <= top + height(block)
    }

    private fun top(block: CalendarBlock): Float = (block.top / 60 * hour).toFloat()

    private fun height(block: CalendarBlock): Float = maxOf(((block.bottom - block.top) / 60 * hour).toFloat(), minHeight)
}

/**
 * The calendar's touch gestures on a day's column. A tap on a block that [targetAt] finds calls
 * [onTap] with its entry. A press held for the system's long-press time without moving past the
 * touch slop calls [onHold] with its target and position, and the drag then takes the touch from
 * the grid's scroll: [onMove] gets each position, and [onLift] the end. A touch that the scroll or
 * another gesture takes first, or that moves before the hold, is left to them; a drag that
 * loses its touch calls [onCancel].
 */
internal suspend fun PointerInputScope.detectCalendarGestures(
    targetAt: (Offset) -> DragTarget?,
    onTap: (CalendarEntry) -> Unit,
    onHold: (DragTarget, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onLift: () -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val target = targetAt(down.position)
        val before = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitBeforeHold(down.id, down.position) }
        when (before) {
            BeforeHold.TAP -> if (target is DragTarget.OnBlock) onTap(target.block.entry)
            BeforeHold.LEFT -> Unit
            // Held: the press is a drag now.
            null -> if (target != null) drag(down.id, down.position, target, onHold, onMove, onLift, onCancel)
        }
    }
}

/** How a press ended before the long-press time: lifted where it was, or left to another gesture. */
private enum class BeforeHold { TAP, LEFT }

private suspend fun AwaitPointerEventScope.awaitBeforeHold(id: PointerId, start: Offset): BeforeHold {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return BeforeHold.LEFT
        if (change.changedToUp()) return if (change.isConsumed) BeforeHold.LEFT else BeforeHold.TAP
        if (change.isConsumed || (change.position - start).getDistance() > viewConfiguration.touchSlop) return BeforeHold.LEFT
    }
}

private suspend fun AwaitPointerEventScope.drag(
    id: PointerId,
    start: Offset,
    target: DragTarget,
    onHold: (DragTarget, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onLift: () -> Unit,
    onCancel: () -> Unit,
) {
    onHold(target, start)
    var lifted = false
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: break
            change.consume()
            if (!change.pressed) {
                lifted = true
                onLift()
                break
            }
            onMove(change.position)
        }
    } finally {
        if (!lifted) onCancel()
    }
}
