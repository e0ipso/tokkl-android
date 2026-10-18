// Pins for the places where JavaScript and Kotlin disagree, for the ports of parseDuration and
// formatDuration in ../tokkl/packages/common/duration.ts at tokkl commit
// ec84de60087b32e8279c74197bdc94e52164579b.
// Unlike DurationTest, these cases are not in duration.test.ts, so a later diff against tokkl
// leaves them alone. Each expected value was checked by running that TypeScript file in Node 24
// (`node file.mts` strips the types), except where a case says the port departs on purpose.
package com.github.e0ipso.tokkl.time

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationJsSemanticsTest {
    private fun assertParses(cases: List<Pair<String, Long?>>) {
        for ((input, expected) in cases) {
            assertEquals("input ${escaped(input)}", expected, parseDuration(input))
        }
    }

    // Shows white space and invisible characters as \uXXXX, so a failure message can be read.
    private fun escaped(input: String): String = buildString {
        append('"')
        for (c in if (input.length > 24) input.take(24) else input) {
            if (c.code in 0x21..0x7E) append(c) else append("\\u%04X".format(c.code))
        }
        if (input.length > 24) append("...(${input.length} chars)")
        append('"')
    }

    @Test
    fun `only the first comma becomes a dot`() {
        // JavaScript's replace(',', '.') changes one comma. The second stays, so nothing matches.
        assertParses(
            listOf(
                "1,5h 0,5h" to null,
                "1,5,5h" to null,
                "1,5h 0.5h" to 7200,
            ),
        )
    }

    @Test
    fun `white space is what JavaScript counts as white space`() {
        assertParses(
            listOf(
                // \s and trim() take no-break space, ideographic space and the byte order mark.
                "1 h" to 3600,
                "1　h" to 3600,
                "1﻿h" to 3600,
                "1h 30m" to 5400,
                " 90 " to 5400,
                "  90 " to 5400,
                // The next control character and the Mongolian vowel separator are not white space.
                "1\u0085h" to null,
                "1᠎h" to null,
                "\u008590" to null,
                "᠎90" to null,
                "​90" to null,
            ),
        )
    }

    @Test
    fun `a unit named constructor is null where the TypeScript returns NaN`() {
        // The TypeScript looks the unit up on a plain object, so `constructor` finds
        // Object.prototype.constructor and the sum becomes NaN. The port returns null, as its KDoc
        // says. No other all-lowercase letters name a key of Object.prototype.
        assertParses(
            listOf(
                "1 constructor" to null,
                "1constructor" to null,
                "1 CONSTRUCTOR" to null,
                "2 constructor" to null,
                "1h 1 constructor" to null,
                "1 constructor 1h" to null,
            ),
        )
    }

    @Test
    fun `a number that overflows a double is null where the TypeScript returns Infinity`() {
        val nines = "9".repeat(400)
        assertParses(
            listOf(
                nines to null,
                "9".repeat(309) to null,
                // 1e308 is still a double, but 60 times it is not.
                "9".repeat(308) to null,
                "${nines}h" to null,
                "$nines:00" to null,
                "1h ${nines}s" to null,
            ),
        )
    }

    @Test
    fun `a result of 2 to the power 63 or more is null`() {
        // The TypeScript returns these as ordinary doubles, which a Long cannot hold. The largest
        // double below 2^63 is 9223372036854773760, and both languages produce it exactly.
        assertParses(
            listOf(
                "9".repeat(300) to null,
                "9".repeat(19) to null,
                "9".repeat(18) to null,
                "9".repeat(17) to 6_000_000_000_000_000_000,
                "153722867280912900" to 9_223_372_036_854_773_760,
                "153722867280912930" to null,
                "2562047788015215h" to 9_223_372_036_854_773_760,
                "2562047788015216h" to null,
                "2562047788015215:00" to 9_223_372_036_854_773_760,
            ),
        )
    }

    @Test
    fun `halves round up as Math round does`() {
        // kotlin.math.round would give 0, 2, 2, 4 and 1800 here, because it rounds halves to even.
        assertParses(
            listOf(
                "0.5s" to 1,
                "1.5s" to 2,
                "2.5s" to 3,
                "3.5s" to 4,
                "0.5h 0.5s" to 1801,
            ),
        )
    }

    private fun assertFormats(format: DurationFormat, cases: List<Pair<Long, String>>) {
        for ((seconds, expected) in cases) {
            assertEquals("$seconds seconds, ${format.togglName}", expected, formatDuration(seconds, format))
        }
    }

    @Test
    fun `decimal rounds the double's exact value as toFixed does`() {
        // String.format("%.2f") would give 1.01, 0.02, 0.05, 0.08, 1.00 and 2.01 for the first six,
        // whose quotient is stored just below a half. The last four are stored just above one.
        assertFormats(
            DurationFormat.DECIMAL,
            listOf(
                3618L to "1.00 h",
                54L to "0.01 h",
                162L to "0.04 h",
                270L to "0.07 h",
                3582L to "0.99 h",
                7218L to "2.00 h",
                18L to "0.01 h",
                126L to "0.04 h",
                342L to "0.10 h",
                36018L to "10.01 h",
            ),
        )
    }

    @Test
    fun `decimal writes a dot in a locale that writes a comma`() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertFormats(DurationFormat.DECIMAL, listOf(2826L to "0.79 h", 3618L to "1.00 h"))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `a format other than classic or decimal is improved`() {
        // The TypeScript gives 0:05:06 for each of these names, and for null and the number 5.
        for (name in listOf("foo", "Classic", "DECIMAL", "", null)) {
            val format = DurationFormat.fromTogglName(name)
            assertEquals("name $name", DurationFormat.IMPROVED, format)
            assertEquals("name $name", "0:05:06", formatDuration(306, format))
        }
    }

    @Test
    fun `a duration from 2 to the power 53 up is rounded to a double first`() {
        // Long.MAX_VALUE becomes 2^63, so the seconds end in 08 where Long arithmetic gives 07.
        for (format in listOf(DurationFormat.IMPROVED, DurationFormat.CLASSIC)) {
            assertFormats(
                format,
                listOf(
                    Long.MAX_VALUE to "2562047788015215:30:08",
                    9007199254740993L to "2501999792983:36:32",
                    Long.MIN_VALUE to if (format == DurationFormat.CLASSIC) "0 sec" else "0:00:00",
                ),
            )
        }
        assertFormats(
            DurationFormat.DECIMAL,
            listOf(
                Long.MAX_VALUE to "2562047788015215.50 h",
                9007199254740993L to "2501999792983.61 h",
                Long.MIN_VALUE to "0.00 h",
            ),
        )
    }
}
