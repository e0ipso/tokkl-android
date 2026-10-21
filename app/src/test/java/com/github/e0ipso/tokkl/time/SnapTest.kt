// Cases copied verbatim, in order, from ../tokkl/packages/common/fixtures/snap.json and
// snap.test.ts at tokkl commit e3f15ff628d88a476b77e51ef77c9172903d6db4.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SnapTest {
    @Test
    // multiple of the resolution keeps its length
    fun goldenCase1() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:45:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:45:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // zero-length span is too short
    fun goldenCase2() {
        assertNull(snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:00:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // one second is too short
    fun goldenCase3() {
        assertNull(snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:00:01Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // under half a step is too short even across a half-step mark
    fun goldenCase4() {
        assertNull(snapSpan(Instant.parse("2026-10-14T09:02:20Z"), Instant.parse("2026-10-14T09:02:50Z"), 5.0, ZoneId.of("UTC")))
    }

    @Test
    // exactly half a step is kept and its stop ties up
    fun goldenCase5() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:05:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:02:30Z"), 5.0, ZoneId.of("UTC")))
    }

    @Test
    // ends that snap to the same point are too short
    fun goldenCase6() {
        assertNull(snapSpan(Instant.parse("2026-10-14T09:02:31Z"), Instant.parse("2026-10-14T09:07:29Z"), 5.0, ZoneId.of("UTC")))
    }

    @Test
    // off-grid start moves and keeps a 30 minute duration
    fun goldenCase7() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:30:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:02:00Z"), Instant.parse("2026-10-14T09:32:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // stop just past a grid point snaps down
    fun goldenCase8() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:15:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:15:01Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // stop just before a grid point snaps up
    fun goldenCase9() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:30:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:29:59Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // stop a millisecond before a grid point snaps up
    fun goldenCase10() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:05:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:04:59.999Z"), 5.0, ZoneId.of("UTC")))
    }

    @Test
    // start just under the midpoint snaps down
    fun goldenCase11() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:15:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:07:29Z"), Instant.parse("2026-10-14T09:20:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // start just over the midpoint snaps up
    fun goldenCase12() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:15:00.000Z"), Instant.parse("2026-10-14T09:30:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:07:31Z"), Instant.parse("2026-10-14T09:25:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // start on the midpoint tie goes up
    fun goldenCase13() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:15:00.000Z"), Instant.parse("2026-10-14T09:30:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:07:30Z"), Instant.parse("2026-10-14T09:25:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // resolution 1 rounds the start down at 29 seconds
    fun goldenCase14() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T09:10:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:29Z"), Instant.parse("2026-10-14T09:10:00Z"), 1.0, ZoneId.of("UTC")))
    }

    @Test
    // resolution 1 rounds the start up at 30 seconds and the stop down at 10
    fun goldenCase15() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:01:00.000Z"), Instant.parse("2026-10-14T09:10:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:00:30Z"), Instant.parse("2026-10-14T09:10:10Z"), 1.0, ZoneId.of("UTC")))
    }

    @Test
    // resolution 60 snaps both ends down to the hour
    fun goldenCase16() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T09:00:00.000Z"), Instant.parse("2026-10-14T10:00:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:20:00Z"), Instant.parse("2026-10-14T10:25:00Z"), 60.0, ZoneId.of("UTC")))
    }

    @Test
    // resolution 60 snaps both ends up to the hour
    fun goldenCase17() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T10:00:00.000Z"), Instant.parse("2026-10-14T11:00:00.000Z")), snapSpan(Instant.parse("2026-10-14T09:31:00Z"), Instant.parse("2026-10-14T10:40:00Z"), 60.0, ZoneId.of("UTC")))
    }

    @Test
    // Asia/Kolkata (UTC+5:30) hours fall at half past UTC
    fun goldenCase18() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T04:30:00.000Z"), Instant.parse("2026-10-14T05:30:00.000Z")), snapSpan(Instant.parse("2026-10-14T04:40:00Z"), Instant.parse("2026-10-14T05:20:00Z"), 60.0, ZoneId.of("Asia/Kolkata")))
    }

    @Test
    // Asia/Kathmandu (UTC+5:45) hours fall at quarter past UTC
    fun goldenCase19() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T04:15:00.000Z"), Instant.parse("2026-10-14T05:15:00.000Z")), snapSpan(Instant.parse("2026-10-14T04:20:00Z"), Instant.parse("2026-10-14T05:00:00Z"), 60.0, ZoneId.of("Asia/Kathmandu")))
    }

    @Test
    // Asia/Kathmandu half hours fall at quarter to UTC
    fun goldenCase20() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T04:45:00.000Z"), Instant.parse("2026-10-14T05:15:00.000Z")), snapSpan(Instant.parse("2026-10-14T04:40:00Z"), Instant.parse("2026-10-14T05:00:00Z"), 30.0, ZoneId.of("Asia/Kathmandu")))
    }

    @Test
    // span across midnight snaps start down
    fun goldenCase21() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-14T23:45:00.000Z"), Instant.parse("2026-10-15T00:15:00.000Z")), snapSpan(Instant.parse("2026-10-14T23:52:00Z"), Instant.parse("2026-10-15T00:20:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // span across midnight snaps start up
    fun goldenCase22() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-15T00:00:00.000Z"), Instant.parse("2026-10-15T00:15:00.000Z")), snapSpan(Instant.parse("2026-10-14T23:53:00Z"), Instant.parse("2026-10-15T00:20:00Z"), 15.0, ZoneId.of("UTC")))
    }

    @Test
    // Europe/Madrid fall-back keeps 120 minutes elapsed
    fun goldenCase23() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-25T00:00:00.000Z"), Instant.parse("2026-10-25T02:00:00.000Z")), snapSpan(Instant.parse("2026-10-25T00:20:00Z"), Instant.parse("2026-10-25T01:50:00Z"), 60.0, ZoneId.of("Europe/Madrid")))
    }

    @Test
    // Europe/Madrid spring-forward keeps 60 minutes elapsed
    fun goldenCase24() {
        assertEquals(SnappedSpan(Instant.parse("2026-03-29T01:00:00.000Z"), Instant.parse("2026-03-29T02:00:00.000Z")), snapSpan(Instant.parse("2026-03-29T00:50:00Z"), Instant.parse("2026-03-29T01:50:00Z"), 30.0, ZoneId.of("Europe/Madrid")))
    }

    @Test
    // Lord Howe spring-forward: start after the gap lands on local 03:00
    fun goldenCase25() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-03T16:00:00.000Z"), Instant.parse("2026-10-03T17:00:00.000Z")), snapSpan(Instant.parse("2026-10-03T15:20:00Z"), Instant.parse("2026-10-03T17:10:00Z"), 60.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe spring-forward: start before the gap lands on local 01:00 and the span keeps 90 minutes
    fun goldenCase26() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-03T14:30:00.000Z"), Instant.parse("2026-10-03T16:00:00.000Z")), snapSpan(Instant.parse("2026-10-03T15:10:00Z"), Instant.parse("2026-10-03T16:10:00Z"), 60.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe spring-forward: local 02:30 exists at resolution 30
    fun goldenCase27() {
        assertEquals(SnappedSpan(Instant.parse("2026-10-03T15:30:00.000Z"), Instant.parse("2026-10-03T16:00:00.000Z")), snapSpan(Instant.parse("2026-10-03T15:20:00Z"), Instant.parse("2026-10-03T16:05:00Z"), 30.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe fall-back: start snaps to local 01:00
    fun goldenCase28() {
        assertEquals(SnappedSpan(Instant.parse("2026-04-04T14:00:00.000Z"), Instant.parse("2026-04-04T16:30:00.000Z")), snapSpan(Instant.parse("2026-04-04T14:40:00Z"), Instant.parse("2026-04-04T16:40:00Z"), 60.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe fall-back: start snaps to local 02:00
    fun goldenCase29() {
        assertEquals(SnappedSpan(Instant.parse("2026-04-04T15:30:00.000Z"), Instant.parse("2026-04-04T16:30:00.000Z")), snapSpan(Instant.parse("2026-04-04T15:10:00Z"), Instant.parse("2026-04-04T16:40:00Z"), 60.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe fall-back: tie between local 01:00 and 02:00 goes to the later
    fun goldenCase30() {
        assertEquals(SnappedSpan(Instant.parse("2026-04-04T15:30:00.000Z"), Instant.parse("2026-04-04T16:30:00.000Z")), snapSpan(Instant.parse("2026-04-04T14:45:00Z"), Instant.parse("2026-04-04T16:40:00Z"), 60.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe fall-back: second local 01:30 is on the grid at 30
    fun goldenCase31() {
        assertEquals(SnappedSpan(Instant.parse("2026-04-04T15:00:00.000Z"), Instant.parse("2026-04-04T16:00:00.000Z")), snapSpan(Instant.parse("2026-04-04T14:50:00Z"), Instant.parse("2026-04-04T15:50:00Z"), 30.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // Lord Howe fall-back: first local 01:30 is on the grid at 30
    fun goldenCase32() {
        assertEquals(SnappedSpan(Instant.parse("2026-04-04T14:30:00.000Z"), Instant.parse("2026-04-04T15:30:00.000Z")), snapSpan(Instant.parse("2026-04-04T14:35:00Z"), Instant.parse("2026-04-04T15:20:00Z"), 30.0, ZoneId.of("Australia/Lord_Howe")))
    }

    @Test
    // resolution that does not divide 60 is refused
    fun goldenCase33() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T10:00:00Z"), 7.0, ZoneId.of("UTC")) }
    }

    @Test
    // stop before start is refused
    fun goldenCase34() {
        assertThrows(IllegalArgumentException::class.java) { snapSpan(Instant.parse("2026-10-14T10:00:00Z"), Instant.parse("2026-10-14T09:00:00Z"), 5.0, ZoneId.of("UTC")) }
    }

    @Test
    // snapInstant moves an instant to the nearest grid point, the later one on a tie
    fun goldenCase35() {
        val at = { iso: String -> snapInstant(Instant.parse(iso), 5.0, ZoneId.of("UTC")) }
        assertEquals(Instant.parse("2026-10-14T09:00:00.000Z"), at("2026-10-14T09:02:29Z"))
        assertEquals(Instant.parse("2026-10-14T09:05:00.000Z"), at("2026-10-14T09:02:30Z"))
        assertThrows(IllegalArgumentException::class.java) { snapInstant(Instant.EPOCH, 7.0, ZoneId.of("UTC")) }
    }

    @Test
    // only divisors of 60 are resolutions
    fun goldenCase36() {
        for (minutes in listOf(1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30, 60)) assertEquals(minutes.toString(), true, isTimeResolution(minutes.toDouble()))
        for (minutes in listOf(0.0, -5.0, 7.0, 45.0, 90.0, 120.0, 2.5, Double.NaN)) assertEquals(minutes.toString(), false, isTimeResolution(minutes))
    }
}
