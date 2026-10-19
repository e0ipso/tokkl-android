package com.github.e0ipso.tokkl.time

import java.time.Instant
import java.time.ZoneId

// Port of formatTime in ../tokkl/packages/web/src/lib/settings.ts at tokkl commit
// ba6c03dc8b20ffdfb4a13c892ea2dc0e0a1d9f0f. settings.test.ts has no formatTime cases.

/**
 * [instant] on the clock in [zone], as the web app writes clock times. The 24-hour clock gives
 * `09:05`, `00:00` at midnight and `12:00` at noon. With [twelveHour] the same times are
 * `9:05 AM`, `12:00 AM` and `12:00 PM`, with the hour unpadded. Seconds are dropped, never
 * rounded, as Intl's minute is.
 */
fun formatTime(instant: Instant, zone: ZoneId, twelveHour: Boolean): String {
    val local = instant.atZone(zone)
    val hour = local.hour
    val minute = local.minute.toString().padStart(2, '0')
    if (!twelveHour) return "${hour.toString().padStart(2, '0')}:$minute"
    val clockHour = if (hour % 12 == 0) 12 else hour % 12
    return "$clockHour:$minute ${if (hour < 12) "AM" else "PM"}"
}

/**
 * Whether the preferences' `timeofday_format`, [format], asks for the 12-hour clock: the web app
 * tests it with `/a/i`, so any `a` or `A` does, as in `h:mm A`. Null reads as the web app's
 * default, `H:mm`, the 24-hour clock.
 */
fun isTwelveHourFormat(format: String?): Boolean = (format ?: "H:mm").any { it == 'a' || it == 'A' }
