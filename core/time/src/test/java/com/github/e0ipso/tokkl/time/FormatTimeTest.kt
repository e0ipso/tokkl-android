// Cases for the port of formatTime in ../tokkl/packages/web/src/lib/settings.ts at tokkl commit
// ba6c03dc8b20ffdfb4a13c892ea2dc0e0a1d9f0f. settings.test.ts has no formatTime cases, so these
// follow the TypeScript by hand: the 24-hour clock pads the hour, the 12-hour clock does not and
// writes 12 for hour 0 and hour 12.
package com.github.e0ipso.tokkl.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTimeTest {

    @Test
    fun theTwentyFourHourClockPadsTheHour() {
        val cases = listOf(
            "00:00:00" to "00:00",
            "00:05:00" to "00:05",
            "09:05:00" to "09:05",
            "12:00:00" to "12:00",
            "13:07:00" to "13:07",
            "23:59:59.999" to "23:59",
        )
        for ((time, expected) in cases) {
            assertEquals(time, expected, formatTime(utc(time), ZoneOffset.UTC, twelveHour = false))
        }
    }

    @Test
    fun theTwelveHourClockWritesTwelveAtMidnightAndNoonAndNoLeadingZero() {
        val cases = listOf(
            "00:00:00" to "12:00 AM",
            "00:05:00" to "12:05 AM",
            "01:00:00" to "1:00 AM",
            "09:05:00" to "9:05 AM",
            "11:59:00" to "11:59 AM",
            "12:00:00" to "12:00 PM",
            "12:59:00" to "12:59 PM",
            "13:07:00" to "1:07 PM",
            "23:59:59.999" to "11:59 PM",
        )
        for ((time, expected) in cases) {
            assertEquals(time, expected, formatTime(utc(time), ZoneOffset.UTC, twelveHour = true))
        }
    }

    @Test
    fun secondsAreDroppedNotRounded() {
        assertEquals("09:05", formatTime(utc("09:05:59.999"), ZoneOffset.UTC, twelveHour = false))
        assertEquals("9:05 AM", formatTime(utc("09:05:59.999"), ZoneOffset.UTC, twelveHour = true))
    }

    @Test
    fun theClockIsTheZones() {
        // 07:30 UTC is 09:30 in Madrid on summer time and 16:30 in Tokyo. 21:30 UTC is 03:00 the
        // next day in Kolkata, half an hour off the hour.
        val instant = utc("07:30:00")
        assertEquals("09:30", formatTime(instant, ZoneId.of("Europe/Madrid"), twelveHour = false))
        assertEquals("9:30 AM", formatTime(instant, ZoneId.of("Europe/Madrid"), twelveHour = true))
        assertEquals("16:30", formatTime(instant, ZoneId.of("Asia/Tokyo"), twelveHour = false))
        assertEquals("3:00 AM", formatTime(utc("21:30:00"), ZoneId.of("Asia/Kolkata"), twelveHour = true))
    }

    /** The web app tests `timeofday_format` with `/a/i`, and reads a missing one as `H:mm`. */
    @Test
    fun anAInEitherCaseAsksForTheTwelveHourClock() {
        assertTrue(isTwelveHourFormat("h:mm A"))
        assertTrue(isTwelveHourFormat("h:mm a"))
        assertTrue(isTwelveHourFormat("hh:mm a"))
        assertFalse(isTwelveHourFormat("H:mm"))
        assertFalse(isTwelveHourFormat("HH:mm"))
        assertFalse(isTwelveHourFormat(""))
        assertFalse(isTwelveHourFormat(null))
    }

    private fun utc(time: String): Instant = Instant.parse("2026-10-19T${time}Z")
}
