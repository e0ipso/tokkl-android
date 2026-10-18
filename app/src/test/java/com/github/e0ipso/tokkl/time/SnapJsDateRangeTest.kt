// Pins for the end of a JavaScript Date's range in the port of snapSpan in
// ../tokkl/packages/common/snap.ts at tokkl commit 51aed28fbc2769e4868f915230a558c2f2ce989e.
// Unlike SnapTest, these cases are not in snap.test.ts, so a later diff against tokkl leaves them
// alone. A Date holds up to 8.64e15 ms either side of the epoch: +275760-09-13T00:00:00.000Z
// ("the limit") and -271821-04-20T00:00:00.000Z.
//
// Each expected value was checked by running that TypeScript file in Node 24 (`node file.mts`
// strips the types) on the same ISO strings. The TypeScript throws a RangeError or returns an
// invalid Date wherever a case says "rejected"; the port throws IllegalArgumentException, as its
// KDoc says. Zones are UTC and fixed-offset Etc/ ones, so no case depends on tz data for dates
// that far off. Africa/Casablanca at that year differs between ICU and the JDK and is left out.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SnapJsDateRangeTest {
    /** Asserts that the span snaps to [expectedStart] and [expectedStop]. */
    private fun assertSnaps(
        start: String,
        stop: String,
        minutes: Int,
        timeZone: String,
        expectedStart: String,
        expectedStop: String,
    ) {
        val span = snapSpan(Instant.parse(start), Instant.parse(stop), minutes.toDouble(), ZoneId.of(timeZone))
        val label = "$start to $stop at $minutes min in $timeZone"
        assertEquals(label, Instant.parse(expectedStart), span.start)
        assertEquals(label, Instant.parse(expectedStop), span.stop)
    }

    /** Asserts that the span is refused, where the TypeScript throws or returns an invalid Date. */
    private fun assertRejected(start: String, stop: String, minutes: Int, timeZone: String) {
        assertThrows("$start to $stop at $minutes min in $timeZone", IllegalArgumentException::class.java) {
            snapSpan(Instant.parse(start), Instant.parse(stop), minutes.toDouble(), ZoneId.of(timeZone))
        }
    }

    @Test
    fun `a stop exactly at the upper limit is accepted`() {
        assertSnaps("+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.000Z", 60, "UTC", "+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.000Z")
        assertSnaps("+275760-09-12T23:45:00.000Z", "+275760-09-13T00:00:00.000Z", 15, "UTC", "+275760-09-12T23:45:00.000Z", "+275760-09-13T00:00:00.000Z")
        // The wall clock of a zone behind UTC stays inside the range, so it makes no difference.
        assertSnaps("+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.000Z", 60, "Etc/GMT+5", "+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.000Z")
    }

    @Test
    fun `a start one step before the upper limit is accepted, also with no length`() {
        assertSnaps("+275760-09-12T23:45:00.000Z", "+275760-09-12T23:45:00.000Z", 15, "UTC", "+275760-09-12T23:45:00.000Z", "+275760-09-12T23:45:00.000Z")
        assertSnaps("+275760-09-12T23:44:59.999Z", "+275760-09-12T23:44:59.999Z", 15, "UTC", "+275760-09-12T23:45:00.000Z", "+275760-09-12T23:45:00.000Z")
        assertSnaps("+275760-09-12T23:45:00.000Z", "+275760-09-12T23:45:00.000Z", 15, "Etc/GMT+5", "+275760-09-12T23:45:00.000Z", "+275760-09-12T23:45:00.000Z")
    }

    @Test
    fun `a rounded stop that lands exactly on the upper limit is accepted`() {
        // One millisecond of duration rounds up to a whole step, which ends at the limit.
        assertSnaps("+275760-09-12T23:45:00.000Z", "+275760-09-12T23:45:00.001Z", 15, "UTC", "+275760-09-12T23:45:00.000Z", "+275760-09-13T00:00:00.000Z")
        assertSnaps("+275760-09-12T21:00:00.000Z", "+275760-09-12T23:00:00.001Z", 60, "UTC", "+275760-09-12T21:00:00.000Z", "+275760-09-13T00:00:00.000Z")
        assertSnaps("+275760-09-12T22:00:00.001Z", "+275760-09-12T23:00:00.002Z", 60, "UTC", "+275760-09-12T22:00:00.000Z", "+275760-09-13T00:00:00.000Z")
    }

    @Test
    fun `a stop one millisecond past the upper limit is rejected`() {
        // new Date("+275760-09-13T00:00:00.001Z") is an invalid Date, so the elapsed time is NaN.
        assertRejected("+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.001Z", 60, "UTC")
        // The start snaps down to 23:30 and the duration rounds up to 30 minutes, so the snapped
        // stop would be the limit itself. Only the stop's own range check refuses this span.
        assertRejected("+275760-09-12T23:36:00.000Z", "+275760-09-13T00:00:00.001Z", 15, "UTC")
        assertRejected("+275760-09-13T00:00:00.001Z", "+275760-09-13T00:00:00.001Z", 15, "UTC")
        assertRejected("+275760-09-13T00:00:00.001Z", "+275760-09-13T00:00:00.001Z", 15, "Etc/GMT+5")
    }

    @Test
    fun `the limit is judged on whole milliseconds`() {
        // JavaScript drops the digits below a millisecond, so this is exactly the limit.
        assertSnaps("+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.0009999Z", 60, "UTC", "+275760-09-12T23:00:00.000Z", "+275760-09-13T00:00:00.000Z")
        // The start is cut to 22:59:59.999, not rounded to 23:00:00.000, so the duration is one
        // hour and a millisecond, rounds up to two, and the snapped stop is past the limit.
        assertRejected("+275760-09-12T22:59:59.9999999Z", "+275760-09-13T00:00:00.000Z", 60, "UTC")
        // Below the lower limit, the same cut gives an invalid Date.
        assertRejected("-271821-04-19T23:59:59.9999999Z", "-271821-04-20T01:00:00.000Z", 60, "UTC")
    }

    @Test
    fun `a start within a step of the upper limit is rejected`() {
        // Finding the grid point reads the offset one step on, and the TypeScript throws a
        // RangeError there, as the KDoc says.
        assertRejected("+275760-09-13T00:00:00.000Z", "+275760-09-13T00:00:00.000Z", 15, "UTC")
        assertRejected("+275760-09-12T23:45:00.001Z", "+275760-09-12T23:45:00.001Z", 15, "UTC")
        assertRejected("+275760-09-12T23:59:59.999Z", "+275760-09-13T00:00:00.000Z", 15, "UTC")
        assertRejected("+275760-09-12T23:00:00.001Z", "+275760-09-12T23:00:00.002Z", 60, "UTC")
        assertRejected("+275760-09-12T23:30:00.000Z", "+275760-09-13T00:00:00.000Z", 60, "UTC")
        assertRejected("+275760-09-12T23:45:00.001Z", "+275760-09-12T23:45:00.001Z", 15, "Etc/GMT+5")
    }

    @Test
    fun `a snapped stop that would cross the upper limit is rejected`() {
        // The start snaps to 23:45 and the duration rounds up from 16:40 to 30 minutes. The
        // TypeScript returns an invalid Date as the stop; an Instant has no such value.
        assertRejected("+275760-09-12T23:43:20.000Z", "+275760-09-13T00:00:00.000Z", 15, "UTC")
        assertRejected("+275760-09-12T23:43:20.000Z", "+275760-09-13T00:00:00.000Z", 15, "Etc/GMT+5")
    }

    @Test
    fun `a wall clock past the upper limit is rejected`() {
        // At UTC+14 the wall clock reaches the limit 14 hours before the instant does, and Date.UTC
        // gives NaN for it. For a start less than 13 hours before the limit the TypeScript returns
        // two invalid Dates, from 13 hours to just under 15 it throws a RangeError, and from 15 it
        // works.
        assertRejected("+275760-09-12T12:00:00.000Z", "+275760-09-12T13:00:00.000Z", 60, "Etc/GMT-14")
        assertRejected("+275760-09-12T11:00:00.000Z", "+275760-09-12T12:00:00.000Z", 60, "Etc/GMT-14")
        assertRejected("+275760-09-12T10:00:00.000Z", "+275760-09-12T11:00:00.000Z", 60, "Etc/GMT-14")
        assertSnaps("+275760-09-12T09:00:00.000Z", "+275760-09-12T10:00:00.000Z", 60, "Etc/GMT-14", "+275760-09-12T09:00:00.000Z", "+275760-09-12T10:00:00.000Z")
        assertSnaps("+275760-09-12T00:00:00.000Z", "+275760-09-13T00:00:00.000Z", 60, "Etc/GMT-14", "+275760-09-12T00:00:00.000Z", "+275760-09-13T00:00:00.000Z")
    }

    @Test
    fun `a start one step past the lower limit is accepted and one millisecond sooner is rejected`() {
        assertSnaps("-271821-04-20T00:15:00.000Z", "-271821-04-20T00:30:00.000Z", 15, "UTC", "-271821-04-20T00:15:00.000Z", "-271821-04-20T00:30:00.000Z")
        assertSnaps("-271821-04-20T01:00:00.000Z", "-271821-04-20T02:00:00.000Z", 60, "UTC", "-271821-04-20T01:00:00.000Z", "-271821-04-20T02:00:00.000Z")
        assertSnaps("-271821-04-20T01:00:00.000Z", "-271821-04-20T02:00:00.000Z", 60, "Etc/GMT+5", "-271821-04-20T01:00:00.000Z", "-271821-04-20T02:00:00.000Z")
        assertSnaps("-271821-04-20T01:00:00.000Z", "-271821-04-20T02:00:00.000Z", 60, "Etc/GMT-14", "-271821-04-20T01:00:00.000Z", "-271821-04-20T02:00:00.000Z")
        assertRejected("-271821-04-20T00:14:59.999Z", "-271821-04-20T00:30:00.000Z", 15, "UTC")
        assertRejected("-271821-04-20T00:59:59.999Z", "-271821-04-20T02:00:00.000Z", 60, "UTC")
    }

    @Test
    fun `a start at the lower limit is rejected`() {
        assertRejected("-271821-04-20T00:00:00.000Z", "-271821-04-20T01:00:00.000Z", 60, "UTC")
        assertRejected("-271821-04-20T00:00:00.000Z", "-271821-04-20T00:02:00.000Z", 1, "UTC")
        assertRejected("-271821-04-20T00:00:00.000Z", "-271821-04-20T01:00:00.000Z", 60, "Etc/GMT+5")
    }

    @Test
    fun `a start one millisecond before the lower limit is rejected`() {
        assertRejected("-271821-04-19T23:59:59.999Z", "-271821-04-20T01:00:00.000Z", 60, "UTC")
        assertRejected("-271821-04-19T23:59:59.999Z", "-271821-04-19T23:59:59.999Z", 15, "UTC")
    }
}
