// Cases copied verbatim from ../tokkl/packages/common/snap.test.ts at tokkl commit
// 51aed28fbc2769e4868f915230a558c2f2ce989e, for the port of isTimeResolution and snapSpan in
// ../tokkl/packages/common/snap.ts at the same commit.
// Diff the TypeScript file against this one to spot drift.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SnapTest {
    /** JavaScript's `Date.prototype.toISOString`, which always shows milliseconds. */
    private val isoString = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** Snaps an ISO span and returns the result as ISO strings. */
    private fun snap(start: String, stop: String, minutes: Int, timeZone: String = "UTC"): List<String> {
        val span = snapSpan(Instant.parse(start), Instant.parse(stop), minutes.toDouble(), ZoneId.of(timeZone))
        return listOf(isoString.format(span.start), isoString.format(span.stop))
    }

    private fun minutesBetween(span: List<String>): Double =
        (Instant.parse(span[1]).toEpochMilli() - Instant.parse(span[0]).toEpochMilli()) / 60_000.0

    @Test
    fun `a duration that is already a multiple of the resolution keeps its length`() {
        assertEquals(listOf("2026-10-14T09:00:00.000Z", "2026-10-14T09:45:00.000Z"), snap("2026-10-14T09:00:00Z", "2026-10-14T09:45:00Z", 15))
        assertEquals(listOf("2026-10-14T09:00:00.000Z", "2026-10-14T09:00:00.000Z"), snap("2026-10-14T09:00:00Z", "2026-10-14T09:00:00Z", 15))
        // Off the grid, the start moves but the duration stays 30 minutes.
        assertEquals(listOf("2026-10-14T09:00:00.000Z", "2026-10-14T09:30:00.000Z"), snap("2026-10-14T09:02:00Z", "2026-10-14T09:32:00Z", 15))
    }

    @Test
    fun `the duration rounds up, padding less than one step`() {
        assertEquals(15.0, minutesBetween(snap("2026-10-14T09:00:00Z", "2026-10-14T09:00:01Z", 15)), 0.0)
        assertEquals(30.0, minutesBetween(snap("2026-10-14T09:00:00Z", "2026-10-14T09:15:01Z", 15)), 0.0)
        assertEquals(30.0, minutesBetween(snap("2026-10-14T09:00:00Z", "2026-10-14T09:29:59Z", 15)), 0.0)
        assertEquals(5.0, minutesBetween(snap("2026-10-14T09:00:00Z", "2026-10-14T09:04:59.999Z", 5)), 0.0)
    }

    @Test
    fun `the start snaps to the nearest grid point, down or up, and a tie goes up`() {
        assertEquals(listOf("2026-10-14T09:00:00.000Z", "2026-10-14T09:15:00.000Z"), snap("2026-10-14T09:07:29Z", "2026-10-14T09:20:00Z", 15))
        assertEquals(listOf("2026-10-14T09:15:00.000Z", "2026-10-14T09:30:00.000Z"), snap("2026-10-14T09:07:31Z", "2026-10-14T09:20:00Z", 15))
        assertEquals(listOf("2026-10-14T09:15:00.000Z", "2026-10-14T09:30:00.000Z"), snap("2026-10-14T09:07:30Z", "2026-10-14T09:20:00Z", 15))
    }

    @Test
    fun `a resolution of one minute snaps to whole minutes and one of 60 to whole hours`() {
        assertEquals(listOf("2026-10-14T09:00:00.000Z", "2026-10-14T09:10:00.000Z"), snap("2026-10-14T09:00:29Z", "2026-10-14T09:10:00Z", 1))
        assertEquals(listOf("2026-10-14T09:01:00.000Z", "2026-10-14T09:11:00.000Z"), snap("2026-10-14T09:00:30Z", "2026-10-14T09:10:10Z", 1))
        assertEquals(listOf("2026-10-14T09:00:00.000Z", "2026-10-14T11:00:00.000Z"), snap("2026-10-14T09:20:00Z", "2026-10-14T10:25:00Z", 60))
        assertEquals(listOf("2026-10-14T10:00:00.000Z", "2026-10-14T11:00:00.000Z"), snap("2026-10-14T09:31:00Z", "2026-10-14T09:40:00Z", 60))
    }

    @Test
    fun `the grid follows the account clock in zones 30 or 45 minutes off UTC`() {
        // Asia/Kolkata is UTC+5:30, so its whole hours fall at half past in UTC.
        assertEquals(listOf("2026-10-14T04:30:00.000Z", "2026-10-14T05:30:00.000Z"), snap("2026-10-14T04:40:00Z", "2026-10-14T05:20:00Z", 60, "Asia/Kolkata"))
        // Asia/Kathmandu is UTC+5:45: local 10:00 is 04:15 UTC and local 10:30 is 04:45 UTC.
        assertEquals(listOf("2026-10-14T04:15:00.000Z", "2026-10-14T05:15:00.000Z"), snap("2026-10-14T04:20:00Z", "2026-10-14T05:00:00Z", 60, "Asia/Kathmandu"))
        assertEquals(listOf("2026-10-14T04:45:00.000Z", "2026-10-14T05:15:00.000Z"), snap("2026-10-14T04:40:00Z", "2026-10-14T05:00:00Z", 30, "Asia/Kathmandu"))
    }

    @Test
    fun `a span across midnight snaps like any other`() {
        assertEquals(listOf("2026-10-14T23:45:00.000Z", "2026-10-15T00:15:00.000Z"), snap("2026-10-14T23:52:00Z", "2026-10-15T00:20:00Z", 15))
        assertEquals(listOf("2026-10-15T00:00:00.000Z", "2026-10-15T00:30:00.000Z"), snap("2026-10-14T23:53:00Z", "2026-10-15T00:20:00Z", 15))
    }

    @Test
    fun `a span across a DST change keeps its rounded duration as elapsed time`() {
        // Europe/Madrid falls back from 03:00 CEST to 02:00 CET at 01:00 UTC on 2026-10-25.
        val autumn = snap("2026-10-25T00:20:00Z", "2026-10-25T01:50:00Z", 60, "Europe/Madrid")
        assertEquals(listOf("2026-10-25T00:00:00.000Z", "2026-10-25T02:00:00.000Z"), autumn)
        assertEquals(120.0, minutesBetween(autumn), 0.0)
        // Europe/Madrid springs forward from 02:00 CET to 03:00 CEST at 01:00 UTC on 2026-03-29.
        val spring = snap("2026-03-29T00:50:00Z", "2026-03-29T01:40:00Z", 30, "Europe/Madrid")
        assertEquals(listOf("2026-03-29T01:00:00.000Z", "2026-03-29T02:00:00.000Z"), spring)
        assertEquals(60.0, minutesBetween(spring), 0.0)
    }

    @Test
    fun `the start stays on the clock grid across a DST change of half an hour`() {
        val lordHowe = { start: String, stop: String, minutes: Int -> snap(start, stop, minutes, "Australia/Lord_Howe") }
        // Lord Howe Island springs forward from 02:00 (UTC+10:30) to 02:30 (UTC+11) at 15:30 UTC on
        // 2026-10-03. Local 01:00 is 14:30 UTC, 02:00 never happens and local 03:00 is 16:00 UTC.
        assertEquals(listOf("2026-10-03T16:00:00.000Z", "2026-10-03T17:00:00.000Z"), lordHowe("2026-10-03T15:20:00Z", "2026-10-03T15:25:00Z", 60))
        assertEquals(listOf("2026-10-03T14:30:00.000Z", "2026-10-03T15:30:00.000Z"), lordHowe("2026-10-03T15:10:00Z", "2026-10-03T15:25:00Z", 60))
        // Local 02:30 exists on that day, and it is on the grid at a resolution of 30.
        assertEquals(listOf("2026-10-03T15:30:00.000Z", "2026-10-03T16:00:00.000Z"), lordHowe("2026-10-03T15:20:00Z", "2026-10-03T15:25:00Z", 30))
        // It falls back from 02:00 (UTC+11) to 01:30 (UTC+10:30) at 15:00 UTC on 2026-04-04. Local 01:00
        // is 14:00 UTC and local 02:00 is 15:30 UTC; a tie between them goes to the later one.
        assertEquals(listOf("2026-04-04T14:00:00.000Z", "2026-04-04T15:00:00.000Z"), lordHowe("2026-04-04T14:40:00Z", "2026-04-04T14:45:00Z", 60))
        assertEquals(listOf("2026-04-04T15:30:00.000Z", "2026-04-04T16:30:00.000Z"), lordHowe("2026-04-04T15:10:00Z", "2026-04-04T15:15:00Z", 60))
        assertEquals(listOf("2026-04-04T15:30:00.000Z", "2026-04-04T16:30:00.000Z"), lordHowe("2026-04-04T14:45:00Z", "2026-04-04T14:50:00Z", 60))
        // Local 01:30 happens twice, at 14:30 and 15:00 UTC, and both are on the grid at 30.
        assertEquals(listOf("2026-04-04T15:00:00.000Z", "2026-04-04T15:30:00.000Z"), lordHowe("2026-04-04T14:50:00Z", "2026-04-04T14:55:00Z", 30))
        assertEquals(listOf("2026-04-04T14:30:00.000Z", "2026-04-04T15:00:00.000Z"), lordHowe("2026-04-04T14:35:00Z", "2026-04-04T14:40:00Z", 30))
    }

    @Test
    fun `only divisors of 60 are resolutions, and a stop before the start is refused`() {
        for (minutes in listOf(1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30, 60)) assertEquals(minutes.toString(), true, isTimeResolution(minutes.toDouble()))
        for (minutes in listOf(0.0, -5.0, 7.0, 45.0, 90.0, 120.0, 2.5, Double.NaN)) assertEquals(minutes.toString(), false, isTimeResolution(minutes))
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T10:00:00Z"), 7.0, ZoneId.of("UTC")) }
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("2026-10-14T10:00:00Z"), Instant.parse("2026-10-14T09:00:00Z"), 5.0, ZoneId.of("UTC")) }
    }
}
