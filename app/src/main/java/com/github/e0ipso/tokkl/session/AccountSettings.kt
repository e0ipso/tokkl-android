package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ServerClient
import com.github.e0ipso.tokkl.api.ServerProject
import com.github.e0ipso.tokkl.api.ServerTag
import com.github.e0ipso.tokkl.api.ServerTask
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.formatTime
import com.github.e0ipso.tokkl.time.isTwelveHourFormat
import com.github.e0ipso.tokkl.time.parseTimeZone
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The settings that decide how the app shows and reads times: those from `GET /api/v9/me`
 * (docs/PRD.md S7), and the duration and clock formats from `GET /api/v9/me/preferences`. With them goes
 * the account's default workspace, where new entries go. They hold no credential.
 *
 * @property timeZone the account's timezone name, exactly as the server sent it.
 * @property timeResolutionMinutes Tokkl's `time_resolution_minutes`, which the server snaps
 *   stopped entries to. Null when `/me` leaves it out. Toggl Track and older Tokkl servers leave
 *   it out, and they do not snap.
 * @property durationFormat the preferences' `duration_format`. Improved until a refresh reads
 *   the preferences, and when they leave it out, as in the web app.
 * @property defaultWorkspaceId the account's `default_workspace_id` from `/me`, the workspace
 *   that the timer and manual entry pass to `ChangeQueue.start` and `ChangeQueue.create`. It is
 *   stored, so a timer started offline has one. A refresh whose `/me` leaves it out keeps the
 *   stored one. Null until a `/me` sends it, so settings that an older app version stored have
 *   none until the next refresh.
 * @property twelveHour the preferences' `timeofday_format` asks for the 12-hour clock, as `h:mm A`
 *   does. False, the 24-hour clock, until a refresh reads the preferences, and when they leave it
 *   out, as in the web app.
 * @property collapseSimilar the preferences' `collapseTimeEntries`: the history groups similar
 *   entries of a day, as the web app's timer list does. True until a refresh reads the
 *   preferences, and when they leave it out, as in the web app.
 */
data class AccountSettings(
    val timeZone: String,
    val beginningOfWeek: DayOfWeek,
    val timeResolutionMinutes: Double?,
    val durationFormat: DurationFormat = DurationFormat.IMPROVED,
    val defaultWorkspaceId: Long? = null,
    val twelveHour: Boolean = false,
    val collapseSimilar: Boolean = true,
) {

    /**
     * [totalSeconds] in the account's [durationFormat], the text the web app's `duration()`
     * helper gives. Screens show every duration this way. It needs no zone, so it works when
     * [time] is null.
     */
    fun formatDuration(totalSeconds: Long): String = formatDuration(totalSeconds, durationFormat)

    /**
     * [instant] on the account's clock in [zone], the account's zone from [time], in the
     * account's clock format: `14:00`, or `2:00 PM` with [twelveHour]. Screens show every clock
     * time this way.
     */
    fun formatTime(instant: Instant, zone: ZoneId): String = formatTime(instant, zone, twelveHour)

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
 * `timezone` and `beginning_of_week`, so a body without them is not a `/me` user. They send
 * `default_workspace_id` too, but a body without it still signs in, and a refresh then keeps the
 * stored workspace. `time_resolution_minutes` and `role` are Tokkl's own and may be missing.
 * `role` marks a Tokkl server. `toString` leaves out the token.
 *
 * [clients], [projects], [tags] and [tasks] come only with `with_related_data=true`, and are null
 * without it. Tokkl sends every one of them, archived projects and done tasks included.
 */
@Serializable
class Me(
    val id: Long,
    @SerialName("api_token") val apiToken: String? = null,
    val timezone: String,
    @SerialName("beginning_of_week") val beginningOfWeek: Int,
    @SerialName("time_resolution_minutes") val timeResolutionMinutes: Double? = null,
    @SerialName("default_workspace_id") val defaultWorkspaceId: Long? = null,
    val role: String? = null,
    val clients: List<ServerClient>? = null,
    val projects: List<ServerProject>? = null,
    val tags: List<ServerTag>? = null,
    val tasks: List<ServerTask>? = null,
) {

    /** The account settings, or null when `beginning_of_week` is not a day from 0 to 6. */
    fun accountSettings(): AccountSettings? =
        dayOfWeek(beginningOfWeek)?.let {
            AccountSettings(timezone, it, timeResolutionMinutes, defaultWorkspaceId = defaultWorkspaceId)
        }

    override fun toString(): String = "Me(id=$id)"
}

/**
 * One `GET /api/v9/me/preferences` answer. A refresh asks for it once and hands it to each
 * reader: the account settings take [durationFormat] and [twelveHour], the cache the favorites.
 * Each reader decodes only its own key from [body], so a malformed key fails that reader alone.
 */
class MePreferences(val body: JsonObject) {

    /**
     * The web app shows durations as improved when `duration_format` is missing or null, and
     * when it names any format other than classic or decimal. A value that is not a string reads
     * as improved too.
     */
    val durationFormat: DurationFormat
        get() {
            val name = (body["duration_format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return DurationFormat.fromTogglName(name)
        }

    /**
     * The web app writes clock times on the 12-hour clock when `timeofday_format` holds an `a` in
     * either case, as `h:mm A` does, and on the 24-hour clock otherwise. A missing or null value
     * reads as the web app's default, `H:mm`, and so does a value that is not a string.
     */
    val twelveHour: Boolean
        get() {
            val format = (body["timeofday_format"] as? JsonPrimitive)?.takeIf { it.isString } ?: return false
            return isTwelveHourFormat(format.content)
        }

    /**
     * `collapseTimeEntries`, read as the web app's `prefs.collapseTimeEntries ?? true` and its
     * truthiness: missing or null groups, and so does any value JavaScript takes as true, such as
     * a non-empty string or a non-zero number. False, 0 and an empty string do not.
     */
    val collapseSimilar: Boolean
        get() {
            val value = body["collapseTimeEntries"] ?: return true
            if (value is JsonNull) return true
            val primitive = value as? JsonPrimitive ?: return true
            return when {
                primitive.isString -> primitive.content.isNotEmpty()
                primitive.booleanOrNull != null -> primitive.boolean
                else -> primitive.doubleOrNull?.let { it != 0.0 } ?: true
            }
        }
}

/** The day Toggl numbers [number], from 0 for Sunday to 6 for Saturday, or null outside that. */
internal fun dayOfWeek(number: Int): DayOfWeek? =
    if (number in 0..6) DayOfWeek.SUNDAY.plus(number.toLong()) else null

/** Toggl's number for this day, from 0 for Sunday to 6 for Saturday. */
internal val DayOfWeek.togglNumber: Int
    get() = value % 7
