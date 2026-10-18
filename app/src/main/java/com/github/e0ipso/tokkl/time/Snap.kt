package com.github.e0ipso.tokkl.time

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneRules
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// Port of isTimeResolution and snapSpan in ../tokkl/packages/common/snap.ts at tokkl commit
// 51aed28fbc2769e4868f915230a558c2f2ce989e, with timeZoneOffsetMs from time.ts next to it.
// SnapTest replays the cases from snap.test.ts.
//
// As in JavaScript, the arithmetic runs on doubles that hold epoch milliseconds, and an instant
// outside the range of a JavaScript Date behaves like an invalid Date.

private const val MINUTE_MS = 60_000.0

// A JavaScript Date holds up to 8.64e15 ms either side of the epoch.
private const val MAX_TIME_MS = 8_640_000_000_000_000L

// The instants that fall within that range once cut to whole milliseconds.
private val JS_DATE_RANGE =
    Instant.ofEpochMilli(-MAX_TIME_MS)..<Instant.ofEpochMilli(MAX_TIME_MS + 1)

data class SnappedSpan(val start: Instant, val stop: Instant)

/**
 * True for the resolutions that divide an hour evenly: 1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30 and
 * 60.
 */
fun isTimeResolution(minutes: Double): Boolean =
    minutes.isFinite() && floor(minutes) == minutes && minutes >= 1 && 60 % minutes == 0.0

/**
 * Snaps the span from [start] to [stop] to a resolution of [minutes], which must divide 60.
 *
 * The duration rounds up to a multiple of the resolution: it never shrinks, it gains less than one
 * step, and a duration that is already a multiple stays as it is. The start moves to the nearest
 * point of the grid on the wall clock of [timeZone], rounding a tie up. Because the resolution
 * divides an hour, the grid falls on every whole hour, also in zones 30 or 45 minutes off UTC.
 * Near a DST change each grid point uses the offset in force at that point, so a change of half an
 * hour, as on Lord Howe Island, never leaves the start off the clock grid.
 * The stop is the snapped start plus the rounded duration, counted in elapsed time, so a span
 * across a DST change keeps its duration.
 *
 * [start] and [stop] are cut to whole milliseconds, the precision of a JavaScript Date. Where the
 * TypeScript throws a RangeError, this throws [IllegalArgumentException]: for a resolution that
 * does not divide 60, a stop before the start, an instant outside a JavaScript Date's range, and a
 * start within a step or two of either end of that range. Within hours of the range's end the
 * TypeScript can also return an invalid Date; an [Instant] has no such value, so this throws there
 * too.
 */
fun snapSpan(start: Instant, stop: Instant, minutes: Double, timeZone: ZoneId): SnappedSpan {
    require(isTimeResolution(minutes)) { "Invalid time resolution: $minutes" }
    val startMs = jsTime(start)
    val elapsed = jsTime(stop) - startMs
    // Written like the TypeScript's !(elapsed >= 0), so NaN from an invalid Date fails it too.
    require(elapsed >= 0) { "Stop must not be before start" }
    val step = minutes * MINUTE_MS
    val duration = ceil(elapsed / step) * step
    val snappedStart = nearestGridPoint(startMs, step, timeZone.rules)
    return SnappedSpan(jsDate(snappedStart), jsDate(snappedStart + duration))
}

/**
 * The span the server will store for a stopped entry it has not received yet, so the phone can
 * show those times before sending it. Never pass the running entry: its clock is not snapped.
 *
 * A server without Tokkl's `time_resolution_minutes` in `GET /api/v9/me` does not snap, so with
 * [timeResolutionMinutes] null the span comes back unchanged (docs/PRD.md section 8.3). A value
 * that is not a resolution leaves it unchanged as well, where [snapSpan] would throw. Otherwise
 * both ends are cut to whole seconds first, as Tokkl's server does before it snaps
 * (snapToResolution in ../tokkl/packages/api/src/models/timeEntries.ts).
 */
fun snapUnsent(
    start: Instant,
    stop: Instant,
    timeResolutionMinutes: Double?,
    timeZone: ZoneId,
): SnappedSpan {
    if (timeResolutionMinutes == null || !isTimeResolution(timeResolutionMinutes)) {
        return SnappedSpan(start, stop)
    }
    return snapSpan(
        start.truncatedTo(ChronoUnit.SECONDS),
        stop.truncatedTo(ChronoUnit.SECONDS),
        timeResolutionMinutes,
        timeZone,
    )
}

/**
 * The last instant at or before `time` whose wall-clock time is a multiple of `step` at `offset`.
 */
private fun gridFloor(time: Double, step: Double, offset: Double): Double =
    floor((time + offset) / step) * step - offset

/**
 * The instant nearest `time` whose wall-clock time in `rules` is a multiple of `step`, taking
 * the later one on a tie. Without an offset change within a step of `time`, the grid points on
 * either side share the offset at `time`. Otherwise the candidates come from the offsets on both
 * sides of the change, and each one counts only if it is on the grid with the offset in force at
 * that instant, which also drops wall-clock times that a DST gap skips.
 */
private fun nearestGridPoint(time: Double, step: Double, rules: ZoneRules): Double {
    val offsetAt = { at: Double -> offsetMs(at, rules) }
    val offsets = listOf(time - step, time, time + step).map(offsetAt).distinct()
    if (offsets.size == 1) {
        val below = gridFloor(time, step, offsets[0])
        return if ((time - below) * 2 >= step) below + step else below
    }
    return offsets
        .flatMap { offset ->
            val below = gridFloor(time, step, offset)
            listOf(below - step, below, below + step, below + 2 * step)
        }
        .filter { candidate -> gridFloor(candidate, step, offsetAt(candidate)) == candidate }
        .reduce { best, candidate ->
            val nearer = abs(candidate - time) - abs(best - time)
            if (nearer < 0 || (nearer == 0.0 && candidate > best)) candidate else best
        }
}

/**
 * The offset from UTC in force at `at`, in milliseconds, like timeZoneOffsetMs in time.ts. That
 * one formats `new Date(at)` with Intl, which throws a RangeError for an invalid Date, and reads
 * the wall clock back with Date.UTC, which gives NaN for a wall clock past the range's end.
 */
private fun offsetMs(at: Double, rules: ZoneRules): Double {
    require(abs(at) <= MAX_TIME_MS) { "Invalid time value" }
    val offset = rules.getOffset(Instant.ofEpochMilli(at.toLong())).totalSeconds * 1000.0
    return if (floor(at / 1000) * 1000 + offset > MAX_TIME_MS) Double.NaN else offset
}

/** `instant.getTime()` of the JavaScript Date for [instant]: NaN outside a Date's range. */
private fun jsTime(instant: Instant): Double =
    if (instant in JS_DATE_RANGE) instant.toEpochMilli().toDouble() else Double.NaN

/** The instant of `new Date(ms)`, refusing what would be an invalid Date. */
private fun jsDate(ms: Double): Instant {
    require(abs(ms) <= MAX_TIME_MS) { "Invalid time value" }
    return Instant.ofEpochMilli(ms.toLong())
}
