package com.github.e0ipso.tokkl.time

// Port of parseDuration and formatDuration in ../tokkl/packages/common/duration.ts at tokkl commit
// a4b825d70926f2f1deacbd0d205a7c2620b8adb4. DurationTest replays the cases from duration.test.ts.

import kotlin.math.floor

/** Toggl rejects entries longer than 999 hours. */
const val MAX_DURATION_SECONDS: Long = 999L * 3600

private val UNIT_SECONDS = mapOf(
    "h" to 3600, "hr" to 3600, "hrs" to 3600, "hour" to 3600, "hours" to 3600,
    "m" to 60, "min" to 60, "mins" to 60, "minute" to 60, "minutes" to 60,
    "s" to 1, "sec" to 1, "secs" to 1, "second" to 1, "seconds" to 1,
)

// The TypeScript anchors these with ^ and $; matchEntire does the same here.
private val COLON = Regex("""(\d+):(\d{1,2})(?::(\d{1,2}))?""")
private val BARE_INTEGER = Regex("""\d+""")
private val BARE_DECIMAL = Regex("""\d*\.\d+""")
private val UNIT = Regex("""(\d+(?:\.\d+)?|\.\d+)$JS_WHITESPACE*([a-z]+)""")

/**
 * Parses what a person types into a duration field and returns whole seconds.
 *
 * Accepted: "1:30" (h:mm), "1:30:15" (h:mm:ss), "90" (minutes), "1.5" or "1,5h" (decimal hours
 * when a unit or separator says so), "1h 30m", "1h30m", "45s". Returns null for anything else,
 * and for any duration over [MAX_DURATION_SECONDS].
 *
 * As in JavaScript, the arithmetic runs on doubles and only the first comma becomes a dot, which is
 * what `replace(',', '.')` does there.
 */
fun parseDuration(raw: String): Long? {
    val input = raw.jsTrim().lowercase().replaceFirst(',', '.')
    val colon = COLON.matchEntire(input)
    return when {
        input == "" -> null

        colon != null -> colonDuration(colon)

        // A bare integer means minutes, a bare decimal means hours.
        BARE_INTEGER.matches(input) -> withinLimit(input.toDouble() * SECONDS_PER_MINUTE)

        BARE_DECIMAL.matches(input) -> withinLimit(jsRound(input.toDouble() * SECONDS_PER_HOUR))

        else -> unitDuration(input)
    }
}

/** `h:mm` or `h:mm:ss`, with minutes and seconds under 60. */
private fun colonDuration(colon: MatchResult): Long? {
    val h = colon.groupValues[1].toDouble()
    val m = colon.groupValues[2].toDouble()
    val s = colon.groups[3]?.value?.toDouble() ?: 0.0
    if (m >= MINUTES_PER_HOUR || s >= SECONDS_PER_MINUTE) return null
    return withinLimit(h * SECONDS_PER_HOUR + m * SECONDS_PER_MINUTE + s)
}

/** Numbers with units, such as `1h 30m`, which together make up the whole of [input]. */
private fun unitDuration(input: String): Long? {
    var total = 0.0
    val consumed = StringBuilder()
    for (match in UNIT.findAll(input)) {
        val seconds = UNIT_SECONDS[match.groupValues[2]] ?: return null
        total += match.groupValues[1].toDouble() * seconds
        consumed.append(match.value)
    }
    val whole = consumed.toString().replace(JS_WHITESPACE_RUN, "") == input.replace(JS_WHITESPACE_RUN, "")
    return if (whole) withinLimit(jsRound(total)) else null
}

/**
 * Keeps a parsed total, already whole, only when it fits the 999-hour limit. NaN and Infinity fail
 * the comparison, as in JavaScript.
 */
private fun withinLimit(seconds: Double): Long? =
    if (seconds <= MAX_DURATION_SECONDS.toDouble()) seconds.toLong() else null

/**
 * The account's `duration_format` preference, which decides how [formatDuration] writes a
 * duration. Toggl Track and Tokkl name it in `GET /api/v9/me/preferences`.
 */
enum class DurationFormat(val togglName: String) {
    /** Hours, minutes and seconds, as `1:05:06` or `0:05:06`. */
    IMPROVED("improved"),

    /** `1:05:06` from an hour up, then `5:06 min`, then `6 sec`. */
    CLASSIC("classic"),

    /** Hours to two decimals, as `0.79 h`. */
    DECIMAL("decimal"),
    ;

    companion object {
        /**
         * The format the server calls [name]. A missing name means improved, as in the web app,
         * and so does any name other than classic or decimal, because the TypeScript
         * formatDuration writes every other format the improved way.
         */
        fun fromTogglName(name: String?): DurationFormat = when (name) {
            CLASSIC.togglName -> CLASSIC
            DECIMAL.togglName -> DECIMAL
            else -> IMPROVED
        }
    }
}

/**
 * Writes [totalSeconds] the way the web app shows a duration in [format]. A negative duration
 * shows as zero.
 *
 * The arithmetic runs on doubles, as in JavaScript, so a duration from 2^53 seconds up is first
 * rounded to the nearest double. The decimal format rounds the double's exact value with halves
 * up, as `toFixed(2)` does, so 3618 seconds give `1.00 h`, and the point is a dot in every locale.
 */
fun formatDuration(totalSeconds: Long, format: DurationFormat = DurationFormat.IMPROVED): String {
    // Math.floor in the TypeScript has nothing to do here, since a Long is already whole.
    val seconds = if (totalSeconds < 0) 0.0 else totalSeconds.toDouble()
    if (format == DurationFormat.DECIMAL) return "${jsToFixed(seconds / SECONDS_PER_HOUR, 2)} h"

    val h = floor(seconds / SECONDS_PER_HOUR)
    val m = floor((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
    val s = seconds % SECONDS_PER_MINUTE
    return when {
        format != DurationFormat.CLASSIC || h > 0 -> "${whole(h)}:${pad(m)}:${pad(s)}"
        m > 0 -> "${whole(m)}:${pad(s)} min"
        else -> "${whole(s)} sec"
    }
}

/**
 * [totalSeconds] in [format] without its seconds, for a place too narrow for [formatDuration]:
 * `1:05` for `1:05:06`, and `5 min` for the classic `5:06 min`. The web app has no such text. A
 * classic duration under a minute keeps its seconds, its only unit, and a decimal one has none.
 */
fun formatDurationWithoutSeconds(totalSeconds: Long, format: DurationFormat = DurationFormat.IMPROVED): String {
    val seconds = if (totalSeconds < 0) 0.0 else totalSeconds.toDouble()
    val h = floor(seconds / 3600)
    val m = floor((seconds % 3600) / 60)
    return when {
        format == DurationFormat.DECIMAL -> formatDuration(totalSeconds, format)

        format == DurationFormat.CLASSIC && h == 0.0 -> if (m > 0) {
            "${whole(m)} min"
        } else {
            formatDuration(totalSeconds, format)
        }

        else -> "${whole(h)}:${pad(m)}"
    }
}

/**
 * JavaScript's `String(n)` for a whole [n] below 2^53. The hours of a Long's seconds stay below
 * that, and there both give the plain digits.
 */
private fun whole(n: Double): String = n.toLong().toString()

private fun pad(n: Double): String = whole(n).padStart(2, '0')
