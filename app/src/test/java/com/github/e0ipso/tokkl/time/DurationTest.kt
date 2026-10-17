// Cases copied verbatim from ../tokkl/packages/common/duration.test.ts at tokkl commit
// e41ead72c08bbb3e87bbe4146d7fd5d06565c924, for the port of parseDuration in
// ../tokkl/packages/common/duration.ts at tokkl commit ec84de60087b32e8279c74197bdc94e52164579b.
// Diff the TypeScript file against this one to spot drift. Its formatDuration test is not here,
// because this port covers parsing only.
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
}
