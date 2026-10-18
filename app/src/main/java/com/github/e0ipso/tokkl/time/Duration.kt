package com.github.e0ipso.tokkl.time

// Port of parseDuration and formatDuration in ../tokkl/packages/common/duration.ts at tokkl commit
// ec84de60087b32e8279c74197bdc94e52164579b. DurationTest replays the cases from duration.test.ts.

import kotlin.math.floor

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

// 2^63, exactly. Every Double at least this large is out of Long's range.
private const val LONG_LIMIT = 9.223372036854775808E18

/**
 * Parses what a person types into a duration field and returns whole seconds.
 *
 * Accepted: "1:30" (h:mm), "1:30:15" (h:mm:ss), "90" (minutes), "1.5" or "1,5h" (decimal hours
 * when a unit or separator says so), "1h 30m", "1h30m", "45s". Returns null for anything else.
 *
 * As in JavaScript, the arithmetic runs on doubles and only the first comma becomes a dot, which is
 * what `replace(',', '.')` does there. The TypeScript can return three kinds of number a Long
 * cannot hold. It returns NaN for a unit named `constructor`, because its unit lookup finds that
 * key on `Object.prototype`. It returns Infinity once a number of 300-odd digits overflows a
 * double, and integers from 2^63 up. This port returns null for all three.
 */
fun parseDuration(raw: String): Long? {
    val input = raw.jsTrim().lowercase().replaceFirst(',', '.')
    if (input == "") return null

    val colon = COLON.matchEntire(input)
    if (colon != null) {
        val h = colon.groupValues[1].toDouble()
        val m = colon.groupValues[2].toDouble()
        val s = colon.groups[3]?.value?.toDouble() ?: 0.0
        if (m > 59 || s > 59) return null
        return wholeSeconds(h * 3600 + m * 60 + s)
    }

    // A bare integer means minutes, a bare decimal means hours.
    if (BARE_INTEGER.matches(input)) return wholeSeconds(input.toDouble() * 60)
    if (BARE_DECIMAL.matches(input)) return wholeSeconds(jsRound(input.toDouble() * 3600))

    var total = 0.0
    val consumed = StringBuilder()
    for (match in UNIT.findAll(input)) {
        val seconds = UNIT_SECONDS[match.groupValues[2]] ?: return null
        total += match.groupValues[1].toDouble() * seconds
        consumed.append(match.value)
    }
    if (consumed.toString().replace(JS_WHITESPACE_RUN, "") != input.replace(JS_WHITESPACE_RUN, "")) {
        return null
    }
    return wholeSeconds(jsRound(total))
}

/** Returns [seconds], already whole, as a Long, or null when it is not finite or too large. */
private fun wholeSeconds(seconds: Double): Long? =
    if (seconds.isFinite() && seconds < LONG_LIMIT) seconds.toLong() else null

/**
 * The account's `duration_format` preference, which decides how [formatDuration] writes a
 * duration. Toggl Track and Tokkl name it in `GET /api/v9/me/preferences`.
 *
 * @property togglName the name the server sends.
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
    if (format == DurationFormat.DECIMAL) return "${jsToFixed(seconds / 3600, 2)} h"

    val h = floor(seconds / 3600)
    val m = floor((seconds % 3600) / 60)
    val s = seconds % 60
    if (format == DurationFormat.CLASSIC) {
        if (h > 0) return "${whole(h)}:${pad(m)}:${pad(s)}"
        if (m > 0) return "${whole(m)}:${pad(s)} min"
        return "${whole(s)} sec"
    }
    return "${whole(h)}:${pad(m)}:${pad(s)}"
}

/**
 * JavaScript's `String(n)` for a whole [n] below 2^53. The hours of a Long's seconds stay below
 * that, and there both give the plain digits.
 */
private fun whole(n: Double): String = n.toLong().toString()

private fun pad(n: Double): String = whole(n).padStart(2, '0')
