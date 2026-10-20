// Cases copied verbatim from ../tokkl/packages/common/duration.test.ts at tokkl commit
// e41ead72c08bbb3e87bbe4146d7fd5d06565c924, for the ports of parseDuration and formatDuration in
// ../tokkl/packages/common/duration.ts at tokkl commit ec84de60087b32e8279c74197bdc94e52164579b.
// Diff the TypeScript file against this one to spot drift.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {
    @Test
    fun `parseDuration accepts the formats people type`() {
        val cases: List<Pair<String, Long?>> = listOf(
            "1:30" to 5400,
            "0:05:30" to 330,
            "90" to 5400,
            "1.5" to 5400,
            "1,5h" to 5400,
            "1h 30m" to 5400,
            "1h30m" to 5400,
            "2 hours 15 min" to 8100,
            "45s" to 45,
            "" to null,
            "abc" to null,
            "1:75" to null,
            "1x" to null,
            "1h foo" to null,
        )
        for ((input, expected) in cases) {
            assertEquals("input \"$input\"", expected, parseDuration(input))
        }
    }

    @Test
    fun `formatDuration renders each Toggl duration format`() {
        assertEquals("1:05:06", formatDuration(3906, "improved"))
        assertEquals("0:05:06", formatDuration(306, "improved"))
        assertEquals("5:06 min", formatDuration(306, "classic"))
        assertEquals("6 sec", formatDuration(6, "classic"))
        assertEquals("1:05:06", formatDuration(3906, "classic"))
        assertEquals("0.79 h", formatDuration(2826, "decimal"))
        assertEquals("0:00:00", formatDuration(-5, "improved"))
    }

    // Not in duration.test.ts: the web app has no duration without seconds. The week strip uses it.
    @Test
    fun `formatDurationWithoutSeconds drops the seconds in each duration format`() {
        assertEquals("1:05", formatDurationWithoutSeconds(3906, DurationFormat.IMPROVED))
        assertEquals("0:05", formatDurationWithoutSeconds(306, DurationFormat.IMPROVED))
        assertEquals("0:00", formatDurationWithoutSeconds(59, DurationFormat.IMPROVED))
        assertEquals("100:00", formatDurationWithoutSeconds(360_000, DurationFormat.IMPROVED))
        assertEquals("0:00", formatDurationWithoutSeconds(-5, DurationFormat.IMPROVED))
        assertEquals("1:05", formatDurationWithoutSeconds(3906, DurationFormat.CLASSIC))
        assertEquals("5 min", formatDurationWithoutSeconds(306, DurationFormat.CLASSIC))
        assertEquals("1 min", formatDurationWithoutSeconds(60, DurationFormat.CLASSIC))
        assertEquals("under a minute, seconds are its only unit", "6 sec", formatDurationWithoutSeconds(6, DurationFormat.CLASSIC))
        assertEquals("0 sec", formatDurationWithoutSeconds(-5, DurationFormat.CLASSIC))
        assertEquals("0.79 h", formatDurationWithoutSeconds(2826, DurationFormat.DECIMAL))
        assertEquals("1.50 h", formatDurationWithoutSeconds(5400, DurationFormat.DECIMAL))
        assertEquals("improved is the default", "1:05", formatDurationWithoutSeconds(3906))
    }

    /** Takes the format by the name the TypeScript cases use, and fails on a name it lacks. */
    private fun formatDuration(totalSeconds: Long, format: String): String =
        formatDuration(totalSeconds, DurationFormat.entries.single { it.togglName == format })
}
