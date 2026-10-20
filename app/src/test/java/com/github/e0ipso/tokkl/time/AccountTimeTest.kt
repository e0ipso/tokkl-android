package com.github.e0ipso.tokkl.time

import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The phone sits in Kiritimati (UTC+14) and the account in Los Angeles or Kolkata. Every time
 * comes out in the account's zone.
 */
class AccountTimeTest {

    private val deviceZone = TimeZone.getDefault()

    @Before
    fun putThePhoneInAnotherZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DEVICE_ZONE))
    }

    @After
    fun restoreTheZone() {
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun showsTimesInTheAccountZoneAndNotTheDevicesZone() {
        assertEquals("the test's premise", ZoneId.of(DEVICE_ZONE), ZoneId.systemDefault())
        // 04:00 on Sunday 18 October in Los Angeles, and already 01:00 on Monday the 19th in
        // Kiritimati. The clock carries the device's zone, which the account time ignores.
        val instant = Instant.parse("2026-10-18T11:00:00Z")
        val settings = AccountSettings("america/los_angeles", DayOfWeek.MONDAY, timeResolutionMinutes = null)

        val time = settings.time(Clock.fixed(instant, ZoneId.of(DEVICE_ZONE)))!!

        assertEquals(ZoneId.of("America/Los_Angeles"), time.zone)
        assertEquals(LocalDateTime.of(2026, 10, 18, 4, 0), time.local(instant).toLocalDateTime())
        assertEquals("04:00", DateTimeFormatter.ofPattern("HH:mm").format(time.local(instant)))
        assertEquals(LocalDateTime.of(2026, 10, 18, 4, 0), time.now().toLocalDateTime())
        assertEquals(LocalDate.of(2026, 10, 18), time.today())
        assertEquals(LocalDate.of(2026, 10, 18), LocalDate.now(time.clock))
        // The week that holds Sunday the 18th starts on Monday the 12th, not on the device's Monday.
        assertEquals(LocalDate.of(2026, 10, 12), time.startOfWeek(time.today()))
        assertEquals(ZoneId.of("America/Los_Angeles"), settings.time()!!.clock.zone)
    }

    @Test
    fun snapsUnsentEntriesOnTheAccountZonesGrid() {
        // An hourly grid in Kolkata (UTC+05:30) falls on the half hour in UTC and in Kiritimati
        // on the hour. 10:10Z is 15:40 in Kolkata, so the start goes to 16:00 there, 10:30Z.
        val start = Instant.parse("2026-10-18T10:10:00Z")
        val stop = Instant.parse("2026-10-18T10:50:00Z")
        val time = AccountSettings("Asia/Kolkata", DayOfWeek.MONDAY, timeResolutionMinutes = 60.0).time()!!

        val snapped = time.snapUnsent(start, stop)

        assertEquals(SnappedSpan(Instant.parse("2026-10-18T10:30:00Z"), Instant.parse("2026-10-18T11:30:00Z")), snapped)
    }

    @Test
    fun stopsOnTheAccountZonesGrid() {
        // 10:10Z is 15:40 in Kolkata, so the start goes to 16:00 there, 10:30Z, and the stop at
        // 10:50Z, 16:20 there, to 16:00 too. A Stop takes one step, and a new timer none.
        val start = Instant.parse("2026-10-18T10:10:00Z")
        val at = Instant.parse("2026-10-18T10:50:00Z")
        val time = AccountSettings("Asia/Kolkata", DayOfWeek.MONDAY, timeResolutionMinutes = 60.0).time()!!

        assertEquals(SnappedSpan(Instant.parse("2026-10-18T10:30:00Z"), Instant.parse("2026-10-18T11:30:00Z")), time.snapStop(start, at, oneStep = true))
        assertEquals(SnappedSpan(Instant.parse("2026-10-18T10:30:00Z"), Instant.parse("2026-10-18T10:30:00Z")), time.snapStop(start, at, oneStep = false))
    }

    @Test
    fun withoutATimeResolutionAStopKeepsTheTapTime() {
        val start = Instant.parse("2026-10-18T10:10:07.250Z")
        val at = Instant.parse("2026-10-18T10:52:41.900Z")
        val time = AccountSettings("Asia/Kolkata", DayOfWeek.MONDAY, timeResolutionMinutes = null).time()!!

        assertEquals(SnappedSpan(start, at), time.snapStop(start, at, oneStep = true))
    }

    @Test
    fun withoutATimeResolutionUnsentEntriesKeepTheirTimes() {
        val start = Instant.parse("2026-10-18T10:10:07.250Z")
        val stop = Instant.parse("2026-10-18T10:52:41.900Z")
        val time = AccountSettings("Asia/Kolkata", DayOfWeek.MONDAY, timeResolutionMinutes = null).time()!!

        assertEquals(SnappedSpan(start, stop), time.snapUnsent(start, stop))
    }

    @Test
    fun aTimezoneThePhoneCannotResolveGivesNoTimesAndNoFallback() {
        assertNull(AccountSettings("Mars/Olympus_Mons", DayOfWeek.MONDAY, null).time())
        assertNull(AccountSettings("GMT+5", DayOfWeek.MONDAY, null).time())
    }

    private companion object {
        const val DEVICE_ZONE = "Pacific/Kiritimati"
    }
}
