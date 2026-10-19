// Cases copied verbatim from ../tokkl/packages/common/time.test.ts at tokkl commit
// bafe8eb953c258804bb100850a5faa475823ce91, for the port of zonedToUtc and addClockMinutes in
// ../tokkl/packages/common/time.ts at the same commit. The helpers below stand in for the
// TypeScript's dateKey, clockMinutes and addDays, which the cases call.
// Diff the TypeScript file against this one to spot drift.
package com.github.e0ipso.tokkl.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ZonedTimeTest {

    @Test
    fun `zonedToUtc and dateKey round-trip`() {
        val instant = zonedToUtc(2026, 10, 14, 23, 30, 0, zone("Asia/Tokyo"))
        assertEquals("2026-10-14T14:30:00.000Z", iso(instant))
        assertEquals("2026-10-14", dateKey(instant, "Asia/Tokyo"))
        assertEquals("2026-10-14", dateKey(instant, "UTC"))
        assertEquals("2026-10-14", dateKey(instant, "America/Los_Angeles"))
    }

    @Test
    fun `addClockMinutes shifts by local clock time across Madrid DST changes`() {
        // Spring: 01:00 CET plus 120 clock minutes is 03:00 CEST, one hour of elapsed time.
        val one = at("2026-03-29T00:00:00Z")
        assertEquals("2026-03-29T01:00:00.000Z", iso(addClockMinutes(one, 120, MADRID)))
        // A result inside the spring gap (02:30) lands after it, at 03:30 CEST.
        assertEquals("2026-03-29T01:30:00.000Z", iso(addClockMinutes(at("2026-03-29T00:30:00Z"), 60, MADRID)))
        // Autumn: 01:30 CEST plus 60 is 02:30, which happens twice; it resolves to the later one (CET).
        assertEquals("2026-10-25T01:30:00.000Z", iso(addClockMinutes(at("2026-10-24T23:30:00Z"), 60, MADRID)))
        // 00:00 CEST plus 240 is 04:00 CET, five hours of elapsed time.
        assertEquals("2026-10-25T03:00:00.000Z", iso(addClockMinutes(at("2026-10-24T22:00:00Z"), 240, MADRID)))
    }

    @Test
    fun `addClockMinutes handles negative shifts, midnight crossings and milliseconds`() {
        // 03:00 CEST minus 120 is 01:00 CET.
        assertEquals("2026-03-29T00:00:00.000Z", iso(addClockMinutes(at("2026-03-29T01:00:00Z"), -120, MADRID)))
        // 23:00 CET on the 28th plus 240 is 03:00 CEST on the 29th.
        assertEquals("2026-03-29T01:00:00.000Z", iso(addClockMinutes(at("2026-03-28T22:00:00Z"), 240, MADRID)))
        // 00:30 CET on the 29th minus 60 is 23:30 CET on the 28th.
        assertEquals("2026-03-28T22:30:00.000Z", iso(addClockMinutes(at("2026-03-28T23:30:00Z"), -60, MADRID)))
        // 04:00 CET on 2026-10-26 minus 240 crosses midnight back to 00:00 CET.
        assertEquals("2026-10-25T23:00:00.000Z", iso(addClockMinutes(at("2026-10-26T03:00:00Z"), -240, MADRID)))
        assertEquals("2026-10-14T08:30:05.250Z", iso(addClockMinutes(at("2026-10-14T08:00:05.250Z"), 30, MADRID)))
        assertEquals("2026-10-14T08:00:00.000Z", iso(addClockMinutes(at("2026-10-14T08:00:00Z"), 0, MADRID)))
    }

    @Test
    fun `the next local day at a clock time spans DST nights correctly`() {
        // A 10:00 to 09:00-next-day range is 22 hours on the spring night and 24 on the autumn night.
        assertEquals(22.0, hours(zonedToUtc(2026, 3, 28, 10, 0, 0, MADRID), zonedToUtc(2026, 3, 29, 9, 0, 0, MADRID)), 0.0)
        assertEquals(24.0, hours(zonedToUtc(2026, 10, 24, 10, 0, 0, MADRID), zonedToUtc(2026, 10, 25, 9, 0, 0, MADRID)), 0.0)
        assertEquals(23.0, hours(zonedToUtc(2026, 10, 13, 10, 0, 0, MADRID), zonedToUtc(2026, 10, 14, 9, 0, 0, MADRID)), 0.0)
        // Overnight splits resolve 04:00 on the next calendar date, which is not start + 24 hours on DST nights.
        for ((day, iso) in listOf("2026-03-29" to "2026-03-29T02:00:00.000Z", "2026-10-25" to "2026-10-25T03:00:00.000Z")) {
            val previous = addDays(day, -1)
            val (y, m, d) = day.split('-').map(String::toInt)
            val split = zonedToUtc(y, m, d, 4, 0, 0, MADRID)
            assertEquals(iso, iso(split))
            assertEquals(day, dateKey(split, "Europe/Madrid"))
            assertEquals(240.0, clockMinutes(split, MADRID), 0.0)
            val (py, pm, pd) = previous.split('-').map(String::toInt)
            val naive = zonedToUtc(py, pm, pd, 4, 0, 0, MADRID).plusMillis(86_400_000)
            assertNotEquals(240.0, clockMinutes(naive, MADRID), 0.0)
        }
    }

    private fun zone(name: String): ZoneId = ZoneId.of(name)

    /** `at` in time.test.ts: `new Date(iso)`. */
    private fun at(iso: String): Instant = Instant.parse(iso)

    /** JavaScript's `toISOString`, with milliseconds. */
    private fun iso(instant: Instant): String = ISO.format(instant)

    /** dateKey in time.ts: the calendar day of [instant] in [zone]. */
    private fun dateKey(instant: Instant, zone: String): String = instant.atZone(zone(zone)).toLocalDate().toString()

    /** clockMinutes in time.ts: minutes after local midnight, with seconds as a fraction. */
    private fun clockMinutes(instant: Instant, zone: ZoneId): Double =
        instant.atZone(zone).let { it.hour * 60 + it.minute + it.second / 60.0 }

    /** addDays in time.ts. */
    private fun addDays(key: String, days: Long): String = LocalDate.parse(key).plusDays(days).toString()

    private fun hours(from: Instant, to: Instant): Double = (to.toEpochMilli() - from.toEpochMilli()) / 3_600_000.0

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}
