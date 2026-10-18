package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.parseTimeZone
import java.time.Clock
import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The settings from `GET /api/v9/me` that decide how the app shows and reads times (docs/PRD.md
 * S7). They hold no credential.
 *
 * @property timeZone the account's timezone name, exactly as the server sent it.
 * @property timeResolutionMinutes Tokkl's `time_resolution_minutes`, which the server snaps
 *   stopped entries to. Null when `/me` leaves it out. Toggl Track and older Tokkl servers leave
 *   it out, and they do not snap.
 */
data class AccountSettings(
    val timeZone: String,
    val beginningOfWeek: DayOfWeek,
    val timeResolutionMinutes: Double?,
) {

    /**
     * Times in the account's zone, read from [clock], or null when [parseTimeZone] cannot
     * resolve [timeZone]. That happens when the server's Intl knows a zone that the phone's tz
     * database lacks, such as a zone added after the phone's last tz update.
     *
     * The app never falls back to the device's zone or to UTC. A screen without an
     * [AccountTime] shows no clock times or dates and takes no time input. Instead it says that
     * the phone does not know the account's timezone, and names [timeZone]. Durations, the
     * running clock, Start and Stop need no zone and keep working.
     */
    fun time(clock: Clock = Clock.systemUTC()): AccountTime? =
        parseTimeZone(timeZone)?.let { zone -> AccountTime(zone, beginningOfWeek, timeResolutionMinutes, clock) }
}

/**
 * The fields the app reads from `GET /api/v9/me`. Toggl Track and Tokkl always send `id`,
 * `timezone` and `beginning_of_week`, so a body without them is not a `/me` user.
 * `time_resolution_minutes` is Tokkl's own and may be missing. `toString` leaves out the token.
 */
@Serializable
class Me(
    val id: Long,
    @SerialName("api_token") val apiToken: String? = null,
    val timezone: String,
    @SerialName("beginning_of_week") val beginningOfWeek: Int,
    @SerialName("time_resolution_minutes") val timeResolutionMinutes: Double? = null,
) {

    /** The account settings, or null when `beginning_of_week` is not a day from 0 to 6. */
    fun accountSettings(): AccountSettings? =
        dayOfWeek(beginningOfWeek)?.let { AccountSettings(timezone, it, timeResolutionMinutes) }

    override fun toString(): String = "Me(id=$id)"
}

/** The day Toggl numbers [number], from 0 for Sunday to 6 for Saturday, or null outside that. */
internal fun dayOfWeek(number: Int): DayOfWeek? =
    if (number in 0..6) DayOfWeek.SUNDAY.plus(number.toLong()) else null

/** Toggl's number for this day, from 0 for Sunday to 6 for Saturday. */
internal val DayOfWeek.togglNumber: Int
    get() = value % 7
