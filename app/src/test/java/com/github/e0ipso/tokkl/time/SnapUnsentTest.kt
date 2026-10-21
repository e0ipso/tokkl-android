package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SnapUnsentTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    // Off the grid at every resolution and not on a whole second, so any snapping or cutting shows.
    private val start = Instant.parse("2026-10-14T09:02:03.456Z")
    private val stop = Instant.parse("2026-10-14T09:31:00.789Z")

    @Test
    fun `without a time resolution the span comes back unchanged`() {
        assertEquals(SnappedSpan(start, stop), snapUnsent(start, stop, null, madrid))
    }

    @Test
    fun `a value that is not a time resolution leaves the span unchanged`() {
        for (minutes in listOf(0.0, -5.0, 7.0, 45.0, 90.0, 2.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(minutes.toString(), SnappedSpan(start, stop), snapUnsent(start, stop, minutes, madrid))
        }
    }

    @Test
    fun `with a time resolution the span snaps like snapSpan`() {
        assertEquals(snapSpan(start, stop, 15.0, madrid), snapUnsent(start, stop, 15.0, madrid))
        assertEquals(
            SnappedSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:30:00Z")),
            snapUnsent(start, stop, 15.0, madrid),
        )
    }

    @Test
    fun `both ends are cut to whole seconds before snapping, as the server does`() {
        // 29.2 elapsed seconds are too short; truncating each endpoint gives the 30-second tie.
        val from = Instant.parse("2026-10-14T09:00:00.900Z")
        val to = Instant.parse("2026-10-14T09:00:30.100Z")
        assertEquals(null, snapSpan(from, to, 1.0, madrid))
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:01:00Z")), snapUnsent(from, to, 1.0, madrid))
    }

    @Test
    fun `a stop before the start is refused once there is a resolution to snap to`() {
        assertThrows(IllegalArgumentException::class.java) { snapUnsent(stop, start, 15.0, madrid) }
    }
}
