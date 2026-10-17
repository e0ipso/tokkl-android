package com.github.e0ipso.tokkl.time

// Port of parseDuration in ../tokkl/packages/common/duration.ts at tokkl commit
// ec84de60087b32e8279c74197bdc94e52164579b. DurationTest replays the cases from duration.test.ts.

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
