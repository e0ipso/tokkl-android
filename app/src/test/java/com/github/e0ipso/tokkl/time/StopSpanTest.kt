// Cases from ../tokkl/packages/api/test/timeEntries.test.ts at Tokkl 1.5.0 (tokkl commit 08802d4),
// for the port of stopEntryAt in stopSpan. The tests' accounts are in UTC, and Tokkl's default
// resolution is 5 minutes.
package com.github.e0ipso.tokkl.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StopSpanTest {
    private val utc = ZoneId.of("UTC")

    private fun span(start: String, stop: String) = SnappedSpan(Instant.parse(start), Instant.parse(stop))

    @Test
    fun `starting a second timer stops the first one at the new start, snapped to the resolution`() {
        // 09:13:20 is nearer 09:15 than 09:10 at the default resolution of 5, so the first entry stops there.
        assertEquals(
            span("2026-10-14T08:00:00Z", "2026-10-14T09:15:00Z"),
            stopSpan(Instant.parse("2026-10-14T08:00:00Z"), Instant.parse("2026-10-14T09:13:20Z"), 5.0, utc, oneStep = false),
        )
    }

    @Test
    fun `a switch between timers is stored back to back on either side of a half step`() {
        for ((minutes, switchAt, stop) in listOf(
            Triple(5, "09:12:00", "09:10:00"), Triple(5, "09:12:30", "09:15:00"), Triple(5, "09:13:00", "09:15:00"),
            Triple(15, "09:22:00", "09:15:00"), Triple(15, "09:22:30", "09:30:00"), Triple(15, "09:23:00", "09:30:00"),
        )) {
            val at = Instant.parse("2026-10-14T${switchAt}Z")
            val old = stopSpan(Instant.parse("2026-10-14T09:00:00Z"), at, minutes.toDouble(), utc, oneStep = false)
            val label = "R=$minutes switch at $switchAt"
            assertEquals(label, span("2026-10-14T09:00:00Z", "2026-10-14T${stop}Z"), old)
            // Where the second timer's start snaps once it stops.
            assertEquals(label, snapInstant(at, minutes.toDouble(), utc), old.stop)
        }
    }

    @Test
    fun `a switch within half a step of the old start stores a zero-length entry`() {
        assertEquals(
            span("2026-10-14T09:00:00Z", "2026-10-14T09:00:00Z"),
            stopSpan(Instant.parse("2026-10-14T09:00:40Z"), Instant.parse("2026-10-14T09:02:20Z"), 5.0, utc, oneStep = false),
        )
    }

    @Test
    fun `the stop endpoint stores one step when the nearest grid point is the start`() {
        val start = Instant.parse("2026-10-14T09:20:13.400Z")
        val stopped = stopSpan(start, start.plusMillis(1_500), 60.0, utc, oneStep = true)

        assertEquals(span("2026-10-14T09:00:00Z", "2026-10-14T10:00:00Z"), stopped)
    }

    @Test
    fun `the stop endpoint stores the grid point nearest the stop when that is later than the start`() {
        val start = Instant.parse("2026-10-14T08:00:00Z")
        assertEquals(
            span("2026-10-14T08:00:00Z", "2026-10-14T09:10:00Z"),
            stopSpan(start, Instant.parse("2026-10-14T09:12:29.999Z"), 5.0, utc, oneStep = true),
        )
    }

    @Test
    fun `a stop before the start stops on the start, and the stop endpoint one step after it`() {
        val start = Instant.parse("2026-10-14T09:02:00Z")
        val at = Instant.parse("2026-10-14T08:40:00Z")

        assertEquals(span("2026-10-14T09:00:00Z", "2026-10-14T09:00:00Z"), stopSpan(start, at, 5.0, utc, oneStep = false))
        assertEquals(span("2026-10-14T09:00:00Z", "2026-10-14T09:05:00Z"), stopSpan(start, at, 5.0, utc, oneStep = true))
    }

    @Test
    fun `a timer left running past 999 hours stops at the cap`() {
        val start = Instant.parse("2026-01-01T00:00:10Z")
        val stopped = stopSpan(start, start.plus(Duration.ofHours(1200)), 5.0, utc, oneStep = true)

        assertEquals(span("2026-01-01T00:00:00Z", "2026-02-11T15:00:00Z"), stopped)
        assertEquals(Duration.ofHours(999), Duration.between(stopped.start, stopped.stop))
    }

    @Test
    fun `the grid follows the wall clock of the zone`() {
        // Kolkata is UTC+05:30, so its hourly grid falls on the half hour in UTC.
        assertEquals(
            span("2026-10-18T10:30:00Z", "2026-10-18T12:30:00Z"),
            stopSpan(Instant.parse("2026-10-18T10:10:00Z"), Instant.parse("2026-10-18T12:50:00Z"), 60.0, ZoneId.of("Asia/Kolkata"), oneStep = true),
        )
    }

    @Test
    fun `latest typed prediction keeps positive Stop spans but rejects legacy zero switches`() {
        val madrid = ZoneId.of("Europe/Madrid")
        // The night of 2026-10-25, when Madrid's clocks go back an hour, and a plain day.
        val starts = listOf("2026-10-25T00:31:07.250Z", "2026-10-14T09:02:03.456Z").map(Instant::parse)
        for (minutes in listOf(1, 5, 15, 30, 60)) {
            for (start in starts) {
                for (offsetSeconds in listOf(0L, 45L, 150L, 899L, 3_725L, 7_201L)) {
                    for (oneStep in listOf(false, true)) {
                        val stopped = stopSpan(start, start.plusSeconds(offsetSeconds), minutes.toDouble(), madrid, oneStep)
                        assertEquals("R=$minutes $start +${offsetSeconds}s oneStep=$oneStep", stopped.takeIf { it.stop > it.start }, snapSpan(stopped.start, stopped.stop, minutes.toDouble(), madrid))
                    }
                }
            }
        }
    }

    @Test
    fun `a value that is not a time resolution throws, as snapSpan does`() {
        val start = Instant.parse("2026-10-14T09:00:00Z")
        assertThrows(IllegalArgumentException::class.java) { stopSpan(start, start.plusSeconds(600), 7.0, utc, oneStep = true) }
    }
}

class SnapStopTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    // Off the grid at every resolution and not on a whole second, so any snapping or cutting shows.
    private val start = Instant.parse("2026-10-14T09:02:03.456Z")
    private val at = Instant.parse("2026-10-14T09:31:00.789Z")

    @Test
    fun `without a time resolution the entry stops at the tap time`() {
        assertEquals(SnappedSpan(start, at), snapStop(start, at, null, madrid, oneStep = true))
    }

    @Test
    fun `without a time resolution a tap before the start stops the entry on its start`() {
        assertEquals(SnappedSpan(start, start), snapStop(start, start.minusSeconds(5), null, madrid, oneStep = true))
    }

    @Test
    fun `a value that is not a time resolution leaves the tap time`() {
        for (minutes in listOf(0.0, -5.0, 7.0, 45.0, 90.0, 2.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(minutes.toString(), SnappedSpan(start, at), snapStop(start, at, minutes, madrid, oneStep = false))
        }
    }

    @Test
    fun `with a time resolution the entry stops like stopSpan`() {
        assertEquals(stopSpan(start, at, 15.0, madrid, oneStep = true), snapStop(start, at, 15.0, madrid, oneStep = true))
        assertEquals(
            SnappedSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:30:00Z")),
            snapStop(start, at, 15.0, madrid, oneStep = true),
        )
    }

    @Test
    fun `a positive typed span and the existing Stop agree on the nearest endpoint`() {
        // 35 minutes and 7 seconds after the start, which snaps 2 minutes and 3 seconds back.
        val tap = Instant.parse("2026-10-14T09:37:10.789Z")
        assertEquals(Instant.parse("2026-10-14T09:35:00Z"), snapStop(start, tap, 5.0, madrid, oneStep = true).stop)
        assertEquals(Instant.parse("2026-10-14T09:35:00Z"), snapUnsent(start, tap, 5.0, madrid)!!.stop)
    }
}
