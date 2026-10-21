// Existing JS Date boundary inputs replayed against ../tokkl/packages/common/snap.ts at
// tokkl commit e3f15ff628d88a476b77e51ef77c9172903d6db4 in Node 24. Endpoint snapping now
// checks the stop's grid too; short spans return null before either grid lookup.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SnapJsDateRangeTest {
    @Test
    fun dateRangeCase1() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:00:00.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase2() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:45:00.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 15.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase3() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:00:00.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT+5")) }
    }

    @Test
    fun dateRangeCase4() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:45:00.000Z"), Instant.parse("+275760-09-12T23:45:00.000Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase5() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:44:59.999Z"), Instant.parse("+275760-09-12T23:44:59.999Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase6() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:45:00.000Z"), Instant.parse("+275760-09-12T23:45:00.000Z"), 15.0, ZoneId.of("Etc/GMT+5")))
    }

    @Test
    fun dateRangeCase7() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:45:00.000Z"), Instant.parse("+275760-09-12T23:45:00.001Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase8() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T21:00:00.000Z"), Instant.parse("+275760-09-12T23:00:00.001Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase9() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T22:00:00.001Z"), Instant.parse("+275760-09-12T23:00:00.002Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase10() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:00:00.000Z"), Instant.parse("+275760-09-13T00:00:00.001Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase11() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:36:00.000Z"), Instant.parse("+275760-09-13T00:00:00.001Z"), 15.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase12() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-13T00:00:00.001Z"), Instant.parse("+275760-09-13T00:00:00.001Z"), 15.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase13() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-13T00:00:00.001Z"), Instant.parse("+275760-09-13T00:00:00.001Z"), 15.0, ZoneId.of("Etc/GMT+5")) }
    }

    @Test
    fun dateRangeCase14() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:00:00.000Z"), Instant.parse("+275760-09-13T00:00:00.0009999Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase15() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T22:59:59.9999999Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase16() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-19T23:59:59.9999999Z"), Instant.parse("-271821-04-20T01:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase17() {
        assertNull(snapSpan(Instant.parse("+275760-09-13T00:00:00.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase18() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:45:00.001Z"), Instant.parse("+275760-09-12T23:45:00.001Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase19() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:59:59.999Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase20() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:00:00.001Z"), Instant.parse("+275760-09-12T23:00:00.002Z"), 60.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase21() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:30:00.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase22() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T23:45:00.001Z"), Instant.parse("+275760-09-12T23:45:00.001Z"), 15.0, ZoneId.of("Etc/GMT+5")))
    }

    @Test
    fun dateRangeCase23() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:43:20.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 15.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase24() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T23:43:20.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 15.0, ZoneId.of("Etc/GMT+5")) }
    }

    @Test
    fun dateRangeCase25() {
        assertNull(snapSpan(Instant.parse("+275760-09-12T12:00:00.000Z"), Instant.parse("+275760-09-12T13:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT-14")))
    }

    @Test
    fun dateRangeCase26() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T11:00:00.000Z"), Instant.parse("+275760-09-12T12:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT-14")) }
    }

    @Test
    fun dateRangeCase27() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T10:00:00.000Z"), Instant.parse("+275760-09-12T11:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT-14")) }
    }

    @Test
    fun dateRangeCase28() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T09:00:00.000Z"), Instant.parse("+275760-09-12T10:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT-14")) }
    }

    @Test
    fun dateRangeCase29() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("+275760-09-12T00:00:00.000Z"), Instant.parse("+275760-09-13T00:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT-14")) }
    }

    @Test
    fun dateRangeCase30() {
        assertEquals(SnappedSpan(Instant.parse("-271821-04-20T00:15:00.000Z"), Instant.parse("-271821-04-20T00:30:00.000Z")), snapSpan(Instant.parse("-271821-04-20T00:15:00.000Z"), Instant.parse("-271821-04-20T00:30:00.000Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase31() {
        assertEquals(SnappedSpan(Instant.parse("-271821-04-20T01:00:00.000Z"), Instant.parse("-271821-04-20T02:00:00.000Z")), snapSpan(Instant.parse("-271821-04-20T01:00:00.000Z"), Instant.parse("-271821-04-20T02:00:00.000Z"), 60.0, ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeCase32() {
        assertEquals(SnappedSpan(Instant.parse("-271821-04-20T01:00:00.000Z"), Instant.parse("-271821-04-20T02:00:00.000Z")), snapSpan(Instant.parse("-271821-04-20T01:00:00.000Z"), Instant.parse("-271821-04-20T02:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT+5")))
    }

    @Test
    fun dateRangeCase33() {
        assertEquals(SnappedSpan(Instant.parse("-271821-04-20T01:00:00.000Z"), Instant.parse("-271821-04-20T02:00:00.000Z")), snapSpan(Instant.parse("-271821-04-20T01:00:00.000Z"), Instant.parse("-271821-04-20T02:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT-14")))
    }

    @Test
    fun dateRangeCase34() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-20T00:14:59.999Z"), Instant.parse("-271821-04-20T00:30:00.000Z"), 15.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase35() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-20T00:59:59.999Z"), Instant.parse("-271821-04-20T02:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase36() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-20T00:00:00.000Z"), Instant.parse("-271821-04-20T01:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase37() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-20T00:00:00.000Z"), Instant.parse("-271821-04-20T00:02:00.000Z"), 1.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase38() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-20T00:00:00.000Z"), Instant.parse("-271821-04-20T01:00:00.000Z"), 60.0, ZoneId.of("Etc/GMT+5")) }
    }

    @Test
    fun dateRangeCase39() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-19T23:59:59.999Z"), Instant.parse("-271821-04-20T01:00:00.000Z"), 60.0, ZoneId.of("UTC")) }
    }

    @Test
    fun dateRangeCase40() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("-271821-04-19T23:59:59.999Z"), Instant.parse("-271821-04-19T23:59:59.999Z"), 15.0, ZoneId.of("UTC")) }
    }

}
