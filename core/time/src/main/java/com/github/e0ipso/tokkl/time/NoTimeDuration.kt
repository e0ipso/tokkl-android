package com.github.e0ipso.tokkl.time

// Port of noTimeDuration and noTimeSeconds in ../tokkl/packages/web/src/lib/noTimeDuration.ts, and
// of MAX_ALL_DAY_SECONDS in ../tokkl/packages/common/duration.ts. NoTimeDurationTest holds the
// app's own cases.

/** The longest no-time entry: one day's worth. */
const val MAX_ALL_DAY_SECONDS: Long = 24L * 3600

private const val NOT_A_DURATION = "Enter a duration like 1:30 or 90m"
private const val TOO_LONG = "No-time entries can be at most 24 hours"

/**
 * What a no-time entry would store for typed text, and what to fix when it cannot be added.
 *
 * @property seconds the duration snapped to the time resolution, or null when the text is
 * unreadable.
 * @property error the message to show, or null when the entry can be added.
 */
data class NoTimeDuration(val seconds: Long?, val error: String?)

/**
 * The duration a no-time entry would store for [text]: snapped to the time resolution [minutes],
 * the way the server does. A [minutes] of null means the account has none, so the typed value is
 * checked as it stands.
 */
fun noTimeDuration(text: String, minutes: Double?): NoTimeDuration {
    val parsed = parseDuration(text) ?: return NoTimeDuration(null, NOT_A_DURATION)
    return noTimeSeconds(parsed, minutes)
}

/**
 * The same check for a duration already in seconds, as a duration field commits it. A [minutes]
 * that is not a time resolution counts as none, as in [AccountTime.snapDuration].
 *
 * With no resolution a zero duration is refused with the format message: the zero-at-resolution
 * message needs a resolution to name.
 */
fun noTimeSeconds(parsed: Long, minutes: Double?): NoTimeDuration {
    val resolution = minutes?.takeIf(::isTimeResolution)
    val seconds = snapDuration(parsed, resolution)
    if (seconds <= 0) {
        val error = if (resolution == null) {
            NOT_A_DURATION
        } else {
            "The duration rounds to zero at the ${resolution.toInt()}-minute time resolution"
        }
        return NoTimeDuration(seconds, error)
    }
    if (seconds > MAX_ALL_DAY_SECONDS) return NoTimeDuration(seconds, TOO_LONG)
    return NoTimeDuration(seconds, null)
}
