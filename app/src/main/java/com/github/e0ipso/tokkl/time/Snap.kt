package com.github.e0ipso.tokkl.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneRules
import kotlin.math.abs
import kotlin.math.floor

// Port of isTimeResolution, snapSpan and snapInstant in ../tokkl/packages/common/snap.ts at
// tokkl commit e3f15ff628d88a476b77e51ef77c9172903d6db4 (Tokkl 1.6.1+, tested against 1.7.0).
// SnapTest replays fixtures/snap.json and the snapInstant cases from snap.test.ts.
//
// As in JavaScript, the arithmetic runs on doubles that hold epoch milliseconds, and an instant
// outside the range of a JavaScript Date behaves like an invalid Date.

private const val MINUTE_MS = 60_000.0

// MAX_DURATION_SECONDS in ../tokkl/packages/common/duration.ts: 999 hours, a whole number of steps
// at every resolution.
private val MAX_DURATION: Duration = Duration.ofHours(999)

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
 * Independently snaps both ends to the account's wall-clock grid, taking the later point on a
 * tie. Returns null below half a step or when the snapped endpoints coincide. Grid points use
 * their own UTC offsets, including across fractional-hour DST changes.
 *
 * Inputs have JavaScript Date millisecond precision. Invalid resolutions, reversed spans and
 * invalid Date values throw [IllegalArgumentException], corresponding to the upstream RangeError.
 */
fun snapSpan(start: Instant, stop: Instant, minutes: Double, timeZone: ZoneId): SnappedSpan? {
    require(isTimeResolution(minutes)) { "Invalid time resolution: $minutes" }
    val elapsed = jsTime(stop) - jsTime(start)
    require(elapsed >= 0) { "Stop must not be before start" }
    if (elapsed * 2 < minutes * MINUTE_MS) return null
    // Compare milliseconds before making Instants: an upstream invalid Date compares false.
    val snappedStart = nearestGridPoint(jsTime(start), minutes * MINUTE_MS, timeZone.rules)
    val snappedStop = nearestGridPoint(jsTime(stop), minutes * MINUTE_MS, timeZone.rules)
    return if (snappedStop > snappedStart) SnappedSpan(jsDate(snappedStart), jsDate(snappedStop)) else null
}

/** The nearest wall-clock grid point to [at], taking the later point on a tie. */
fun snapInstant(at: Instant, minutes: Double, timeZone: ZoneId): Instant {
    require(isTimeResolution(minutes)) { "Invalid time resolution: $minutes" }
    return jsDate(nearestGridPoint(jsTime(at), minutes * MINUTE_MS, timeZone.rules))
}

/**
 * The span the server will store for a stopped entry it has not received yet, so the phone can
 * show those times before sending it. Null means the typed span would be rejected as too short.
 * Never pass the running entry: its clock is not snapped.
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
): SnappedSpan? {
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
 * The span Tokkl stores for a running entry from [start] that stops at [at], at a resolution of
 * [minutes], which must divide 60, on the wall clock of [timeZone]. Port of stopEntryAt in
 * ../tokkl/packages/api/src/models/timeEntries.ts at Tokkl 1.5.0 (tokkl commit 08802d4); StopSpanTest
 * replays the cases of its tests in packages/api/test/timeEntries.test.ts.
 *
 * Both ends are cut to whole seconds first, as Tokkl's server does. The start moves to the grid
 * point nearest it, and the stop to the grid point nearest [at], or to the start when that is
 * later. With [oneStep], as Tokkl's stop endpoint and so the web app's Stop have it, a stop that
 * lands on the start moves one step later. Without it, as when a new timer stops the running one,
 * the entry ends where the new timer's start snaps once that stops too, which can leave it with
 * no length. The stop never goes past 999 hours after the start.
 *
 * This preserves the existing Android Stop contract pending TKA-0259/TKA-0261. It is not the
 * latest server short-Stop/trash behavior. In particular a zero-length switch can be rejected.
 * Typed entries use the nullable [snapUnsent] separately.
 */
fun stopSpan(start: Instant, at: Instant, minutes: Double, timeZone: ZoneId, oneStep: Boolean): SnappedSpan {
    val snappedStart = startGridPoint(start, minutes, timeZone)
    var stop = maxOf(startGridPoint(at, minutes, timeZone), snappedStart)
    if (oneStep && stop == snappedStart) stop = stop.plusMillis((minutes * MINUTE_MS).toLong())
    stop = minOf(stop, snappedStart.plus(MAX_DURATION))
    return SnappedSpan(snappedStart, stop)
}

/**
 * The existing Android Stop prediction, retained pending TKA-0259/TKA-0261 (docs/PRD.md T3).
 * This is [stopSpan] with
 * the account's [timeResolutionMinutes]. A server without Tokkl's `time_resolution_minutes`, or
 * with a value that is not a resolution, does not snap, so the span stops at [at] as tapped, or at
 * [start] when the tap came earlier.
 */
fun snapStop(
    start: Instant,
    at: Instant,
    timeResolutionMinutes: Double?,
    timeZone: ZoneId,
    oneStep: Boolean,
): SnappedSpan {
    if (timeResolutionMinutes == null || !isTimeResolution(timeResolutionMinutes)) {
        return SnappedSpan(start, maxOf(at, start))
    }
    return stopSpan(start, at, timeResolutionMinutes, timeZone, oneStep)
}

/**
 * The grid point nearest [at], cut to whole seconds, where Tokkl's server snaps a start at [at]:
 * nearestGridPoint in ../tokkl/packages/api/src/models/timeEntries.ts.
 */
private fun startGridPoint(at: Instant, minutes: Double, timeZone: ZoneId): Instant {
    val time = at.truncatedTo(ChronoUnit.SECONDS)
    return snapInstant(time, minutes, timeZone)
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
