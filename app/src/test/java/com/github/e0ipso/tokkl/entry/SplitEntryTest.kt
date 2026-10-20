package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.time.AccountTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The split port. The snappedInstant and snappedSplit cases are those of
 * ../tokkl/packages/web/src/lib/settings.test.ts, and the overnight ones those of
 * ../tokkl/e2e/editor.spec.ts, at tokkl commit 0031de3.
 */
class SplitEntryTest {

    @Test
    fun snappedInstantPicksTheNearestGridPointAndATieGoesUp() {
        assertEquals(at("2026-10-16T09:00:00.000Z"), snappedInstant(at("2026-10-16T09:07:29.999Z"), utc(15.0)))
        assertEquals(at("2026-10-16T09:15:00.000Z"), snappedInstant(at("2026-10-16T09:07:30.000Z"), utc(15.0)))
        assertEquals(at("2026-10-16T09:15:00.000Z"), snappedInstant(at("2026-10-16T09:15:00.000Z"), utc(15.0)))
        assertEquals(at("2026-10-16T09:00:00.000Z"), snappedInstant(at("2026-10-16T09:00:29.500Z"), utc(1.0)))
    }

    @Test
    fun snappedInstantFollowsTheWallClockOfTheTimeZone() {
        // Kathmandu is UTC+5:45, so a whole local hour falls at :15 UTC.
        val kathmandu = zone("Asia/Kathmandu", 60.0)
        assertEquals(at("2026-10-16T04:15:00.000Z"), snappedInstant(at("2026-10-16T04:40:00Z"), kathmandu))
        assertEquals(at("2026-10-16T05:15:00.000Z"), snappedInstant(at("2026-10-16T04:50:00Z"), kathmandu))
        // On Lord Howe, local 02:00 never happens on 2026-10-03; 02:30 local (15:30 UTC) is the grid point.
        assertEquals(at("2026-10-03T15:30:00.000Z"), snappedInstant(at("2026-10-03T15:20:00Z"), zone("Australia/Lord_Howe", 30.0)))
    }

    @Test
    fun halvesSplitAtSnappedInstantAddUpToTheSnappedWhole() {
        val settings = utc(15.0)
        val start = at("2026-10-16T09:00:00Z")
        val stop = at("2026-10-16T10:00:00Z")
        val split = snappedInstant(at("2026-10-16T09:22:10.700Z"), settings)
        assertEquals(at("2026-10-16T09:15:00.000Z"), split)
        val first = snappedSeconds(start, split, settings)
        val second = snappedSeconds(split, stop, settings)
        assertEquals(listOf(900L, 2700L), listOf(first, second))
        assertEquals(first + second, snappedSeconds(start, stop, settings))
    }

    @Test
    fun snappedSplitReturnsTheHalvesTheServerStoresMeetingAtTheSplit() {
        val halves = snappedSplit(at("2026-10-16T09:00:00Z"), at("2026-10-16T10:00:00Z"), at("2026-10-16T09:15:00Z"), utc(15.0))
        assertEquals(
            listOf(
                listOf("2026-10-16T09:00:00Z", "2026-10-16T09:15:00Z"),
                listOf("2026-10-16T09:15:00Z", "2026-10-16T10:00:00Z"),
            ),
            iso(halves),
        )
    }

    @Test
    fun snappedSplitCutsAnEntrySavedUnderAnotherResolutionFromItsSnappedWhole() {
        // At 15 minutes, 09:08 to 10:08 stores as 09:15 to 10:15. Snapping 09:08 to 09:30 on its own would
        // store 09:15 to 09:45, over the second half.
        val halves = snappedSplit(at("2026-10-16T09:08:00Z"), at("2026-10-16T10:08:00Z"), at("2026-10-16T09:30:00Z"), utc(15.0))
        assertEquals(
            listOf(
                listOf("2026-10-16T09:15:00Z", "2026-10-16T09:30:00Z"),
                listOf("2026-10-16T09:30:00Z", "2026-10-16T10:15:00Z"),
            ),
            iso(halves),
        )
    }

    @Test
    fun snappedSplitRefusesASplitOnOrOutsideTheSnappedStartAndStop() {
        val start = at("2026-10-16T09:08:00Z")
        val stop = at("2026-10-16T10:08:00Z")
        assertEquals(SplitProblem.OUTSIDE, snappedSplit(start, stop, at("2026-10-16T09:15:00Z"), utc(15.0)))
        assertEquals(SplitProblem.OUTSIDE, snappedSplit(start, stop, at("2026-10-16T10:15:00Z"), utc(15.0)))
        assertEquals(SplitProblem.OUTSIDE, snappedSplit(start, stop, at("2026-10-16T09:00:00Z"), utc(15.0)))
    }

    @Test
    fun snappedSplitRefusesASplitWhoseHalvesWouldOverlapAcrossAHalfHourClockChange() {
        // Lord Howe springs forward from 02:00 (UTC+10:30) to 02:30 (UTC+11) at 15:30 UTC on 2026-10-03.
        // At a resolution of 60, 14:30 to 16:30 UTC is local 01:00 to 03:30, and local 03:00 (16:00 UTC)
        // is the only grid point inside it. Saved on their own, the halves would be 14:30 to 16:30 and
        // 16:00 to 17:00 UTC: a 30-minute overlap and three hours in total.
        val lordHowe = zone("Australia/Lord_Howe", 60.0)
        val start = at("2026-10-03T14:30:00Z")
        val stop = at("2026-10-03T16:30:00Z")
        val split = snappedInstant(at("2026-10-03T16:00:00Z"), lordHowe)
        assertEquals(at("2026-10-03T16:00:00.000Z"), split)
        assertEquals(SplitProblem.CLOCK_CHANGE, snappedSplit(start, stop, split, lordHowe))
        // At a resolution of 30, the half hour on either side of the change is a whole step, so it splits.
        assertEquals(
            listOf(
                listOf("2026-10-03T14:30:00Z", "2026-10-03T16:00:00Z"),
                listOf("2026-10-03T16:00:00Z", "2026-10-03T16:30:00Z"),
            ),
            iso(snappedSplit(start, stop, split, zone("Australia/Lord_Howe", 30.0))),
        )
    }

    /** Overnight entries split at a clock time before their start, on the nights the clocks change. */
    @Test
    fun anOvernightSplitUsesTheNextDaysClockTimeOnTheNightsTheClocksChange() {
        val madrid = zone("Europe/Madrid", 15.0)
        // 22:00 CEST to 6:00 CET, split at 3:00 CET.
        assertEquals(
            listOf(listOf("2025-10-25T20:00:00Z", "2025-10-26T02:00:00Z"), listOf("2025-10-26T02:00:00Z", "2025-10-26T05:00:00Z")),
            iso(splitTimes("3:00", at("2025-10-25T20:00:00Z"), at("2025-10-26T05:00:00Z"), madrid, twelveHour = false)),
        )
        // 22:00 CET to 7:00 CEST, split at 3:30 CEST.
        assertEquals(
            listOf(listOf("2026-03-28T21:00:00Z", "2026-03-29T01:30:00Z"), listOf("2026-03-29T01:30:00Z", "2026-03-29T05:00:00Z")),
            iso(splitTimes("3:30", at("2026-03-28T21:00:00Z"), at("2026-03-29T05:00:00Z"), madrid, twelveHour = false)),
        )
    }

    @Test
    fun aSplitTimeOnTheEntrysDayMovesToTheGridAndOneOutsideOrUnreadableIsRefused() {
        val madrid = zone("Europe/Madrid", 15.0)
        // 09:00 to 10:30 in Madrid, CEST.
        val start = at("2026-10-16T07:00:00Z")
        val stop = at("2026-10-16T08:30:00Z")
        assertEquals(
            "09:37 moves to the grid point at 09:30",
            listOf(listOf("2026-10-16T07:00:00Z", "2026-10-16T07:30:00Z"), listOf("2026-10-16T07:30:00Z", "2026-10-16T08:30:00Z")),
            iso(splitTimes("937", start, stop, madrid, twelveHour = false)),
        )
        assertEquals("after the stop", SplitProblem.OUTSIDE, splitTimes("11:00", start, stop, madrid, twelveHour = false))
        assertEquals("on the start, so the next day's, after the stop", SplitProblem.OUTSIDE, splitTimes("9", start, stop, madrid, twelveHour = false))
        assertEquals(SplitProblem.UNREADABLE, splitTimes("half past", start, stop, madrid, twelveHour = false))
        assertEquals(SplitProblem.UNREADABLE, splitTimes("", start, stop, madrid, twelveHour = false))
    }

    @Test
    fun forTwelveHourUsersATimeWithoutAmOrPmIsTheOneNearerTheStart() {
        val madrid = zone("Europe/Madrid", 15.0)
        // 1 PM to 4 PM in Madrid: "2" is 2 PM, not 2 AM the next day.
        val start = at("2026-10-16T11:00:00Z")
        val stop = at("2026-10-16T14:00:00Z")
        assertEquals(
            listOf(listOf("2026-10-16T11:00:00Z", "2026-10-16T12:00:00Z"), listOf("2026-10-16T12:00:00Z", "2026-10-16T14:00:00Z")),
            iso(splitTimes("2", start, stop, madrid, twelveHour = true)),
        )
    }

    private fun iso(result: SplitResult): List<List<String>> {
        val halves = result as? SplitHalves ?: throw AssertionError("no halves: $result")
        return listOf(halves.first, halves.second).map { listOf(it.start.toString(), it.stop.toString()) }
    }

    private fun at(iso: String): Instant = Instant.parse(iso)

    private fun utc(resolution: Double) = zone("UTC", resolution)

    private fun zone(id: String, resolution: Double) = AccountTime(ZoneId.of(id), DayOfWeek.MONDAY, resolution)
}
