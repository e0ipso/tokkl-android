package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.parseTimeOfDay
import java.time.Instant
import java.time.temporal.ChronoUnit

// Split (docs/PRD.md H6): splitTimes ports splitHalves in
// ../tokkl/packages/web/src/components/EntryEditor.tsx, and snappedSplit and snappedInstant port
// those in ../tokkl/packages/web/src/lib/settings.ts, at tokkl commit 0031de3. SplitEntryTest
// replays the cases from settings.test.ts and the overnight splits of e2e/editor.spec.ts.

/** What a split time gives: the two halves, or why it cuts nowhere. */
sealed interface SplitResult

/** The halves a split cuts a stopped entry into, as the server will store them. They meet at one instant. */
data class SplitHalves(val first: SnappedSpan, val second: SnappedSpan) : SplitResult

/** Why a split time cuts nowhere, as SPLIT_PROBLEMS in the web app's EntryEditor.tsx names it. */
enum class SplitProblem : SplitResult {
    /** The time cannot be read. */
    UNREADABLE,

    /** The time does not fall strictly inside the entry as the server stores it. */
    OUTSIDE,

    /** The stored halves would not meet at one instant, across a clock change of part of a step. */
    CLOCK_CHANGE,
}

/**
 * The halves that the clock time [text], typed for the stopped entry from [start] to [stop], cuts
 * it into, in the account's timezone. The time is read as the entry form reads a start, for
 * 12-hour users the one nearer to the entry's start. It falls on the entry's first day, or, when
 * that is not after the start, on the next calendar day at the same clock time: adding 24 hours
 * would be off by the shift on a night the clocks change. It then moves to the nearest grid point
 * of the time resolution, so both halves keep it as their shared boundary when the server snaps them.
 */
fun splitTimes(text: String, start: Instant, stop: Instant, time: AccountTime, twelveHour: Boolean): SplitResult {
    val minutes = parseTimeOfDay(text, twelveHour = twelveHour, reference = time.minutesOfDay(start)) ?: return SplitProblem.UNREADABLE
    val startDay = time.local(start).toLocalDate()
    var at = time.instantAt(startDay, minutes)
    if (!at.isAfter(start)) at = time.instantAt(startDay.plusDays(1), minutes)
    return snappedSplit(start, stop, snappedInstant(at, time), time)
}

/**
 * The grid point of the account's time resolution nearest [at], cut to whole seconds first: where
 * the server moves a start it saves. Without a resolution, [at] in whole seconds.
 */
fun snappedInstant(at: Instant, time: AccountTime): Instant = snap(at, at, time).start

/**
 * The two halves of the stopped entry from [start] to [stop] cut at [at], as the server will store
 * them. Both come from the snapped whole, so an entry saved under another resolution splits into
 * halves that add up to what it would store now. [SplitProblem.OUTSIDE] when [at] is not strictly
 * inside the snapped whole, and [SplitProblem.CLOCK_CHANGE] when the stored halves would not meet
 * at one instant and add up to the whole, as across Lord Howe Island's half-hour change at a
 * resolution of 60.
 */
fun snappedSplit(start: Instant, stop: Instant, at: Instant, time: AccountTime): SplitResult {
    if (stop.isBefore(start)) return SplitProblem.OUTSIDE
    val whole = snap(start, stop, time)
    val cut = at.truncatedTo(ChronoUnit.SECONDS)
    if (!cut.isAfter(whole.start) || !cut.isBefore(whole.stop)) return SplitProblem.OUTSIDE
    val first = snap(whole.start, cut, time)
    val second = snap(cut, whole.stop, time)
    val meets = first.start == whole.start && first.stop == second.start && second.stop == whole.stop
    return if (meets) SplitHalves(first, second) else SplitProblem.CLOCK_CHANGE
}

/** The span from [from] to [to], both cut to whole seconds, snapped as the server will store it. */
private fun snap(from: Instant, to: Instant, time: AccountTime): SnappedSpan =
    time.snapUnsent(from.truncatedTo(ChronoUnit.SECONDS), to.truncatedTo(ChronoUnit.SECONDS))
