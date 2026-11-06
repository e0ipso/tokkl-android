package com.github.e0ipso.tokkl.time

import kotlin.math.abs

// Port of parseTimeOfDay in ../tokkl/packages/common/timeOfDay.ts at tokkl commit
// c8dd0fd9bf5f9f67ccbf59079dacbf43c94c480f, where the parser moved from packages/web/src/lib
// unchanged. TimeOfDayTest replays the cases in ../tokkl/packages/common/fixtures/timeOfDay.json at
// tokkl commit a5cadd1fc706bd8cefbe97ab8d573f84e2d06913, which leaves the parser as it was.

// The TypeScript anchors this with ^ and $; matchEntire does the same here.
private val TIME_OF_DAY = Regex("""(\d{1,2})(?:[:.]?(\d{2}))?(?::\d{2})?(a|am|p|pm)?""")

private const val DAY_MINUTES = 24 * 60

/**
 * Parses a typed clock time into minutes after midnight.
 *
 * Accepted: "9", "930", "0930", "9:30", "9.30", "9:30:15" (seconds dropped), "9a", "9pm", "12am".
 * Without a suffix, 24-hour input is taken literally. For 12-hour users, AM or PM for 1-11 is
 * guessed like Toggl does: with [after] (an end time), the first of the two that follows [after],
 * wrapping past midnight; otherwise whichever is closer to [reference] (a start time, guessed
 * against now). [reference], [after] and the result are minutes after midnight. An explicit suffix
 * is never second-guessed.
 */
fun parseTimeOfDay(
    raw: String,
    twelveHour: Boolean = false,
    reference: Int? = null,
    after: Int? = null,
): Int? {
    val match = TIME_OF_DAY.matchEntire(raw.jsTrim().lowercase().replace(JS_WHITESPACE_RUN, ""))
        ?: return null
    var hour = match.groupValues[1].toInt()
    val minute = match.groups[2]?.value?.toInt() ?: 0
    val suffix = match.groups[3]?.value
    if (minute > 59) return null

    if (suffix != null) {
        if (hour < 1 || hour > 12) return null
        hour = if (suffix.startsWith('a')) {
            if (hour == 12) 0 else hour
        } else {
            if (hour == 12) 12 else hour + 12
        }
        return hour * 60 + minute
    }
    if (hour > 23) return null
    if (twelveHour && hour >= 1 && hour <= 11) {
        val am = hour * 60 + minute
        val pm = am + 12 * 60
        // Long arithmetic, so any Int reference or after gives JavaScript's answer without overflow.
        if (after != null) {
            fun wait(candidate: Int) = (candidate.toLong() - after + DAY_MINUTES) % DAY_MINUTES
            return if (wait(pm) < wait(am)) pm else am
        }
        if (reference != null) {
            return if (abs(pm.toLong() - reference) < abs(am.toLong() - reference)) pm else am
        }
    }
    return hour * 60 + minute
}
