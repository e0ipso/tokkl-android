package com.github.e0ipso.tokkl.time

import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset

// Tokkl stores any timezone name that Intl.DateTimeFormat accepts (isValidTimeZone in
// ../tokkl/packages/common/time.ts) and returns it from GET /api/v9/me as sent. parseTimeZone
// accepts the same names. The rules here were checked against Node 24.21 (ICU 78.3, tz 2026c).

// Intl's offset syntax: an ASCII or U+2212 minus sign, hours 00 to 23, then optional minutes,
// with or without a colon.
private val OFFSET = Regex("""([+\-\u2212])([01][0-9]|2[0-3])(?::?([0-5][0-9]))?""")

private const val MAX_OFFSET_SECONDS = 18 * 3600

// The names ICU accepts that java.time does not list: the three-letter IDs it keeps from old Java,
// and links tzdb has dropped. Each maps to the zone Intl resolves it to, which for EST, MST and
// HST is a region rather than the fixed offset of ZoneId.SHORT_IDS.
private val INTL_ONLY_NAMES = mapOf(
    "ACT" to "Australia/Darwin",
    "AET" to "Australia/Sydney",
    "AGT" to "America/Buenos_Aires",
    "ART" to "Africa/Cairo",
    "AST" to "America/Anchorage",
    "BET" to "America/Sao_Paulo",
    "BST" to "Asia/Dhaka",
    "CAT" to "Africa/Maputo",
    "CNT" to "America/St_Johns",
    "CST" to "America/Chicago",
    "CTT" to "Asia/Shanghai",
    "Canada/East-Saskatchewan" to "America/Regina",
    "EAT" to "Africa/Nairobi",
    "ECT" to "Europe/Paris",
    "EST" to "America/Panama",
    "GMT+0" to "Etc/GMT+0",
    "GMT-0" to "Etc/GMT-0",
    "HST" to "Pacific/Honolulu",
    "IET" to "America/Indianapolis",
    "IST" to "Asia/Calcutta",
    "JST" to "Asia/Tokyo",
    "MIT" to "Pacific/Apia",
    "MST" to "America/Phoenix",
    "NET" to "Asia/Yerevan",
    "NST" to "Pacific/Auckland",
    "PLT" to "Asia/Karachi",
    "PNT" to "America/Phoenix",
    "PRT" to "America/Puerto_Rico",
    "PST" to "America/Los_Angeles",
    "ROC" to "Asia/Taipei",
    "SST" to "Pacific/Guadalcanal",
    "US/Pacific-New" to "America/Los_Angeles",
    "VST" to "Asia/Saigon",
)

// Every accepted name, lowercased, to the ID ZoneId.of takes. The table above wins over the
// runtime's own list, so those names resolve the same on every tz database. Sorting first keeps
// the result fixed should two IDs ever differ only in case.
private val ZONE_IDS: Map<String, String> by lazy {
    buildMap {
        for ((name, id) in INTL_ONLY_NAMES) put(name.asciiLowercase(), id)
        for (id in ZoneId.getAvailableZoneIds().sorted()) putIfAbsent(id.asciiLowercase(), id)
    }
}

/**
 * The zone for a timezone name from `GET /api/v9/me`, or null when there is none. Never falls back
 * to the device's zone.
 *
 * Accepts what Intl accepts, which is more than [ZoneId.of]: names in any ASCII case (`utc`,
 * `europe/madrid`), ICU's three-letter IDs such as `IST`, and offsets such as `+05`, `+0530` or
 * `-05:00`, also with a U+2212 minus sign. Returns null for what Intl rejects, even where
 * [ZoneId.of] accepts it, such as `Z`, `UT`, `GMT+5` or `UTC+01:00`.
 *
 * Two cases depend on the runtime. A name resolves only if the device's tz database lists it or
 * INTL_ONLY_NAMES maps it. Offsets beyond ±18:00 return null, because [ZoneOffset] stops there
 * while Intl goes to ±23:59.
 */
fun parseTimeZone(name: String): ZoneId? {
    val offset = OFFSET.matchEntire(name)
    if (offset != null) return parseOffset(offset)
    val id = ZONE_IDS[name.asciiLowercase()] ?: return null
    return try {
        ZoneId.of(id)
    } catch (e: DateTimeException) {
        // A tz database without one of the targets in INTL_ONLY_NAMES.
        null
    }
}

private fun parseOffset(match: MatchResult): ZoneOffset? {
    val sign = if (match.groupValues[1] == "+") 1 else -1
    val hours = match.groupValues[2].toInt()
    val minutes = match.groups[3]?.value?.toInt() ?: 0
    val seconds = hours * 3600 + minutes * 60
    if (seconds > MAX_OFFSET_SECONDS) return null
    return ZoneOffset.ofTotalSeconds(sign * seconds)
}

/** Lowercases A to Z only, the case folding Intl uses for timezone names. */
private fun String.asciiLowercase(): String =
    buildString(length) { for (c in this@asciiLowercase) append(if (c in 'A'..'Z') c + 32 else c) }
