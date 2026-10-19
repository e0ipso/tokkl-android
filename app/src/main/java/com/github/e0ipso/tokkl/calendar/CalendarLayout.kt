package com.github.e0ipso.tokkl.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A time entry as the calendar shows it.
 *
 * @property start the server's start, or for a stopped entry with a change the server has not
 *   received, the start the server will store, snapped on the phone, as the history shows it.
 * @property stop as [start], or null while the entry runs.
 * @property unsent the queue holds a change to the entry that the server has not received.
 */
data class CalendarEntry(
    val id: Long,
    val description: String,
    val project: String?,
    val projectColor: String?,
    val task: String?,
    val start: Instant,
    val stop: Instant?,
    val unsent: Boolean,
) {
    val running: Boolean get() = stop == null

    /** Whole seconds from [start] to [stop], or to [now] while it runs, as entrySeconds in the web app's lib/settings.ts. */
    fun seconds(now: Instant): Long =
        maxOf(0L, Math.floorDiv((stop ?: now).toEpochMilli() - start.toEpochMilli(), 1000L))
}

/**
 * The part of [entry] that falls on [day], placed as WeekCalendar.tsx places it in the web app.
 * [top] and [bottom] are minutes after midnight on the account's wall clock, so a 10:00 start sits
 * at 600 also on a day the clocks change. A block [lane] of [lanes] wide sits beside the blocks it
 * overlaps.
 */
data class CalendarBlock(
    val entry: CalendarEntry,
    val day: LocalDate,
    val top: Double,
    val bottom: Double,
    val lane: Int,
    val lanes: Int,
)

/** Minutes in a day on the wall clock, and the height of the grid in minutes. */
const val DAY_MINUTES = 24 * 60

/** The shortest block in minutes, so a short entry still shows. */
private const val MIN_BLOCK_MINUTES = 5.0

/**
 * Where the part of [start] to [stop] that falls on [day] in [zone] sits, in wall-clock minutes
 * after midnight, or null when the span misses the day. A start before the day begins at 0, and a
 * stop at or after the next midnight ends at [DAY_MINUTES]. Port of clockSpanOnDay in
 * ../tokkl/packages/common/time.ts: inside the repeated autumn hour the bottom can be above the
 * top.
 */
fun clockSpanOnDay(start: Instant, stop: Instant, day: LocalDate, zone: ZoneId): Pair<Double, Double>? {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    if (stop <= dayStart || start >= dayEnd) return null
    val top = if (start < dayStart) 0.0 else clockMinutes(start, zone)
    val bottom = if (stop >= dayEnd) DAY_MINUTES.toDouble() else clockMinutes(stop, zone)
    return top to bottom
}

/** Wall-clock minutes after midnight in [zone], with whole seconds as a fraction, as clockMinutes in time.ts. */
private fun clockMinutes(instant: Instant, zone: ZoneId): Double {
    val local = instant.atZone(zone)
    return local.hour * 60 + local.minute + local.second / 60.0
}

/**
 * The blocks of [entries] on each of [days] in [zone], as layout in the web app's WeekCalendar.tsx
 * lays them out. An entry across midnight gives a block on each day it touches. A running entry
 * ends at [now]. Each block is at least five minutes tall. Blocks that overlap share their
 * cluster's width in lanes, each taking the first lane free at its top.
 */
fun calendarBlocks(entries: List<CalendarEntry>, days: List<LocalDate>, zone: ZoneId, now: Instant): List<CalendarBlock> {
    val ordered = entries.sortedWith(compareBy<CalendarEntry> { it.start }.thenBy { it.id })
    return days.flatMap { day ->
        val onDay = ordered
            .mapNotNull { entry ->
                clockSpanOnDay(entry.start, entry.stop ?: now, day, zone)?.let { (top, bottom) ->
                    Placed(entry, top, maxOf(bottom, top + MIN_BLOCK_MINUTES))
                }
            }
            .sortedBy { it.top }
        lanesOf(onDay).map { (placed, lane, lanes) -> CalendarBlock(placed.entry, day, placed.top, placed.bottom, lane, lanes) }
    }
}

private class Placed(val entry: CalendarEntry, val top: Double, val bottom: Double)

/**
 * Each of [blocks], sorted by top, with its lane and its cluster's lane count. A cluster is a run
 * of blocks that overlap one another in a chain; it ends at the first block that starts at or
 * after the bottom of every block before it.
 */
private fun lanesOf(blocks: List<Placed>): List<Triple<Placed, Int, Int>> {
    val result = mutableListOf<Triple<Placed, Int, Int>>()
    var cluster = mutableListOf<Pair<Placed, Int>>()
    var clusterEnd = -1.0
    fun flush() {
        val lanes = maxOf(1, (cluster.maxOfOrNull { it.second } ?: 0) + 1)
        cluster.mapTo(result) { (block, lane) -> Triple(block, lane, lanes) }
        cluster = mutableListOf()
    }
    for (block in blocks) {
        if (block.top >= clusterEnd) {
            flush()
            clusterEnd = -1.0
        }
        val used = cluster.filter { it.first.bottom > block.top }.mapTo(HashSet()) { it.second }
        var lane = 0
        while (lane in used) lane++
        cluster += block to lane
        clusterEnd = maxOf(clusterEnd, block.bottom)
    }
    flush()
    return result
}

/**
 * The seconds of the [entries] that start on [day] in [zone], running ones up to [now], as the
 * web app's dayTotal. An entry across midnight counts on the day it starts.
 */
fun dayTotalSeconds(entries: List<CalendarEntry>, day: LocalDate, zone: ZoneId, now: Instant): Long =
    entries.filter { it.start.atZone(zone).toLocalDate() == day }.sumOf { it.seconds(now) }
