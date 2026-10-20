// The cases of ../tokkl/packages/common/fixtures/timeOfDay.json at tokkl commit
// a5cadd1fc706bd8cefbe97ab8d573f84e2d06913, in its order, for the port of parseTimeOfDay in
// ../tokkl/packages/common/timeOfDay.ts, which that commit left as it was. tokkl's
// timeOfDay.test.ts runs every case in that file, so compare the file with this one to spot drift.
package com.github.e0ipso.tokkl.time

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeOfDayTest {
    /** The TypeScript `options` argument. */
    private data class Options(
        val twelveHour: Boolean? = null,
        val reference: Int? = null,
        val after: Int? = null,
    )

    private class Case(val raw: String, val options: Options, val expected: Int?)

    private fun case(raw: String, expected: Int?) = Case(raw, Options(), expected)

    private fun case(raw: String, options: Options, expected: Int?) = Case(raw, options, expected)

    private fun replay(vararg cases: Case) {
        for (c in cases) {
            val actual = parseTimeOfDay(
                c.raw,
                twelveHour = c.options.twelveHour ?: false,
                reference = c.options.reference,
                after = c.options.after,
            )
            assertEquals("input \"${c.raw}\" with ${c.options}", c.expected, actual)
        }
    }

    @Test
    fun `parseTimeOfDay handles 24-hour input`() = replay(
        case("9", 540),
        case("09:30", 570),
        case("930", 570),
        case("1745", 1065),
        case("17.45", 1065),
        case("23:59:59", 1439),
        case("24", null),
        case("9:75", null),
        case("abc", null),
    )

    @Test
    fun `parseTimeOfDay handles suffixes and 12-hour guessing`() = replay(
        case("8a", 480),
        case("4pm", 960),
        case("12am", 0),
        case("12pm", 720),
        case("13pm", null),
        // At 15:00, "11" means 11 AM and "4" means 4 PM.
        case("11", Options(twelveHour = true, reference = 900), 660),
        case("4", Options(twelveHour = true, reference = 900), 960),
    )

    @Test
    fun `an explicit suffix on an end time is never guessed`() = replay(
        // 10am to 9am: the end stays 9 AM, before the start, so it lands on the next day (23 hours).
        case("10am", Options(twelveHour = true, reference = 600), 600),
        case("9am", Options(twelveHour = true, after = 600), 540),
        case("9pm", Options(twelveHour = true, after = 1320), 1260),
        case("12am", Options(twelveHour = true, after = 600), 0),
        case("12pm", Options(twelveHour = true, after = 780), 720),
    )

    @Test
    fun `an unsuffixed 12-hour end is the first occurrence after the start`() = replay(
        // 11pm to "1" is 1 AM, two hours later across midnight.
        case("1", Options(twelveHour = true, after = 23 * 60), 60),
        // 10am to "9" is 9 PM, not 9 AM the next day.
        case("9", Options(twelveHour = true, after = 600), 21 * 60),
        // 8pm to "9:30" is 9:30 PM; 1pm to "11" is 11 PM; 9pm to "8" is 8 AM the next day.
        case("9:30", Options(twelveHour = true, after = 20 * 60), 21 * 60 + 30),
        case("11", Options(twelveHour = true, after = 13 * 60), 23 * 60),
        case("8", Options(twelveHour = true, after = 21 * 60), 8 * 60),
        // 9am to "10" is 10 AM; "12" and 0/13-23 are not ambiguous.
        case("10", Options(twelveHour = true, after = 540), 600),
        case("12", Options(twelveHour = true, after = 600), 720),
        case("15", Options(twelveHour = true, after = 1000), 900),
        case("0:30", Options(twelveHour = true, after = 1000), 30),
    )

    @Test
    fun `24-hour input ignores the end-time guess`() = replay(
        case("1", Options(after = 23 * 60), 60),
        case("9", Options(after = 600), 540),
        case("9", Options(twelveHour = false, after = 600), 540),
        case("21:00", Options(after = 600), 1260),
    )
}
