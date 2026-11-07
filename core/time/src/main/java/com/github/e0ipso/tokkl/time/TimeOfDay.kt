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
fun parseTimeOfDay(raw: String, twelveHour: Boolean = false, reference: Int? = null, after: Int? = null): Int? {
    val match = TIME_OF_DAY.matchEntire(raw.jsTrim().lowercase().replace(JS_WHITESPACE_RUN, ""))
        ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groups[2]?.value?.toInt() ?: 0
    val suffix = match.groups[3]?.value
    return when {
        minute >= MINUTES_PER_HOUR -> null
        suffix != null -> withSuffix(hour, minute, suffix)
        hour >= HOURS_PER_DAY -> null
        twelveHour && hour in 1 until CLOCK_FACE_HOURS -> guessHalfOfDay(hour, minute, reference, after)
        else -> hour * MINUTES_PER_HOUR + minute
    }
}

/** A time with an explicit `a`, `am`, `p` or `pm` [suffix], or null for an hour off the clock face. */
private fun withSuffix(hour: Int, minute: Int, suffix: String): Int? {
    if (hour !in 1..CLOCK_FACE_HOURS) return null
    val hourOfDay = if (suffix.startsWith('a')) {
        if (hour == CLOCK_FACE_HOURS) 0 else hour
    } else {
        if (hour == CLOCK_FACE_HOURS) CLOCK_FACE_HOURS else hour + CLOCK_FACE_HOURS
    }
    return hourOfDay * MINUTES_PER_HOUR + minute
}

/** The morning or the afternoon [hour], as [parseTimeOfDay] guesses it from [after] or [reference]. */
private fun guessHalfOfDay(hour: Int, minute: Int, reference: Int?, after: Int?): Int {
    val am = hour * MINUTES_PER_HOUR + minute
    val pm = am + 12 * 60
    // Long arithmetic, so any Int reference or after gives JavaScript's answer without overflow.
    return when {
        after != null -> {
            fun wait(candidate: Int) = (candidate.toLong() - after + DAY_MINUTES) % DAY_MINUTES
            if (wait(pm) < wait(am)) pm else am
        }

        reference != null -> if (abs(pm.toLong() - reference) < abs(am.toLong() - reference)) pm else am

        else -> am
    }
}
