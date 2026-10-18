// Pins for parseTimeZone. Whether each name is accepted, and the zone it maps to, was checked
// against Intl.DateTimeFormat in Node 24.21 (ICU 78.3, tz 2026c), the check Tokkl's
// isValidTimeZone runs, except where a case says the port departs on purpose.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class TimeZoneTest {
    private fun assertResolves(cases: List<Pair<String, String>>) {
        for ((name, id) in cases) assertEquals("name \"$name\"", ZoneId.of(id), parseTimeZone(name))
    }

    private fun assertRejects(names: List<String>) {
        for (name in names) assertNull("name \"$name\"", parseTimeZone(name))
    }

    @Test
    fun `canonical names resolve unchanged`() = assertResolves(
        listOf(
            "Europe/Madrid" to "Europe/Madrid",
            "Australia/Lord_Howe" to "Australia/Lord_Howe",
            "Asia/Kolkata" to "Asia/Kolkata",
            "UTC" to "UTC",
            "Etc/GMT+5" to "Etc/GMT+5",
        ),
    )

    @Test
    fun `names match in any ASCII case, as in Intl`() = assertResolves(
        listOf(
            "europe/madrid" to "Europe/Madrid",
            "EUROPE/MADRID" to "Europe/Madrid",
            "eUROPE/mADRID" to "Europe/Madrid",
            "utc" to "UTC",
            "etc/utc" to "Etc/UTC",
            "gmt" to "GMT",
            "us/pacific" to "US/Pacific",
            "etc/gmt-14" to "Etc/GMT-14",
            "asia/calcutta" to "Asia/Calcutta",
        ),
    )

    @Test
    fun `names only ICU knows map to the zone Intl resolves them to`() = assertResolves(
        listOf(
            "ACT" to "Australia/Darwin",
            "AET" to "Australia/Sydney",
            "AGT" to "America/Buenos_Aires",
            "ART" to "Africa/Cairo",
            "AST" to "America/Anchorage",
            "BET" to "America/Sao_Paulo",
            "BST" to "Asia/Dhaka",
            "CAT" to "Africa/Maputo",
            "CNT" to "America/St_Johns",
            "CST" to "America/Chicago",
            "CTT" to "Asia/Shanghai",
            "Canada/East-Saskatchewan" to "America/Regina",
            "EAT" to "Africa/Nairobi",
            "ECT" to "Europe/Paris",
            "EST" to "America/Panama",
            // Intl calls these UTC.
            "GMT+0" to "Etc/GMT+0",
            "GMT-0" to "Etc/GMT-0",
            "HST" to "Pacific/Honolulu",
            "IET" to "America/Indianapolis",
            "IST" to "Asia/Calcutta",
            "JST" to "Asia/Tokyo",
            "MIT" to "Pacific/Apia",
            "MST" to "America/Phoenix",
            "NET" to "Asia/Yerevan",
            "NST" to "Pacific/Auckland",
            "PLT" to "Asia/Karachi",
            "PNT" to "America/Phoenix",
            "PRT" to "America/Puerto_Rico",
            "PST" to "America/Los_Angeles",
            "ROC" to "Asia/Taipei",
            "SST" to "Pacific/Guadalcanal",
            "US/Pacific-New" to "America/Los_Angeles",
            "VST" to "Asia/Saigon",
            "ist" to "Asia/Calcutta",
            "gmt+0" to "Etc/GMT+0",
            "us/pacific-new" to "America/Los_Angeles",
        ),
    )

    @Test
    fun `offsets take a sign, two-digit hours and optional minutes`() {
        val cases = listOf(
            "+05:30" to ZoneOffset.ofHoursMinutes(5, 30),
            "+0530" to ZoneOffset.ofHoursMinutes(5, 30),
            "+05" to ZoneOffset.ofHours(5),
            "-08:00" to ZoneOffset.ofHours(-8),
            "\u221205:00" to ZoneOffset.ofHours(-5),
            "\u22120530" to ZoneOffset.ofHoursMinutes(-5, -30),
            "-00:00" to ZoneOffset.UTC,
            "+00" to ZoneOffset.UTC,
            "+18:00" to ZoneOffset.ofHours(18),
            "-18:00" to ZoneOffset.ofHours(-18),
        )
        for ((name, zone) in cases) assertEquals("name \"$name\"", zone, parseTimeZone(name))
    }

    @Test
    fun `names Intl rejects return null, also where ZoneId of takes them`() = assertRejects(
        listOf(
            "Z", "z", "UT", "GMT+5", "UTC+01:00", "UTC+1",
            "+5", "-5", "+5:30", "+05:3", "+05:30:00", "+053000", "+24:00",
            "Etc/GMT-15", "Etc/GMT+13", "Asia/Riyadh87", "Asia/Hanoi", "Etc/Unknown",
            "Factory", "localtime", "posixrules",
            "", " Europe/Madrid", "Europe/Madrid ", "Europe//Madrid", "Europe/Madrid/",
            // Letters that Unicode case mapping, unlike Intl's ASCII-only one, folds into ASCII:
            // a dotted capital I, a dotless small i, a Kelvin sign and a long s.
            "EUROPE/MADR\u0130D", "europe/madr\u0131d",
            "Europe/\u212Aiev", "America/Lo\u017F_Angeles",
        ),
    )

    @Test
    fun `offsets past 18 hours return null, though Intl takes them up to 23-59`() {
        // The port departs here on purpose: ZoneOffset stops at 18 hours either way.
        assertRejects(listOf("+18:01", "+19:00", "+23:59", "-23:59"))
    }
}
