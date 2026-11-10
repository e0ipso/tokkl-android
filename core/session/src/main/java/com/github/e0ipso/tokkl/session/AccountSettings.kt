package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.DAYS_PER_WEEK
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.formatDurationWithoutSeconds
import com.github.e0ipso.tokkl.time.formatTime
import com.github.e0ipso.tokkl.time.isTwelveHourFormat
import com.github.e0ipso.tokkl.time.parseTimeZone
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * The settings that decide how the app shows and reads times: those from `GET /api/v9/me`
 * (docs/PRD.md S7), and the duration and clock formats from `GET /api/v9/me/preferences`. With them goes
 * the account's default workspace, where new entries go. They hold no credential.
 *
 * @property timeZone the account's timezone name, exactly as the server sent it.
 * @property timeResolutionMinutes Tokkl's `time_resolution_minutes`, which the server snaps
 *   stopped entries to. Null when `/me` leaves it out. Toggl Track and older Tokkl servers leave
 *   it out, and they do not snap.
 * @property durationFormat the preferences' `duration_format`; improved until a refresh reads it.
 * @property defaultWorkspaceId the account's `default_workspace_id` from `/me`. It is stored, so a
 *   timer started offline has one. A refresh whose `/me` leaves it out keeps the stored one. Null
 *   until a `/me` sends it.
 * @property twelveHour the preferences' `timeofday_format` asks for the 12-hour clock. False until
 *   a refresh reads it.
 * @property collapseSimilar the preferences' `collapseTimeEntries`: the history groups similar
 *   entries of a day, as the web app's timer list does. True until a refresh reads it.
 * @property userId the account's `id` from `/me`, so signing in again after a refused token
 *   (docs/PRD.md O6) can tell the same account from another one. Null until a `/me` sends it.
 */
data class AccountSettings(
    val timeZone: String,
    val beginningOfWeek: DayOfWeek,
    val timeResolutionMinutes: Double?,
    val durationFormat: DurationFormat = DurationFormat.IMPROVED,
    val defaultWorkspaceId: WorkspaceId? = null,
    val twelveHour: Boolean = false,
    val collapseSimilar: Boolean = true,
    val userId: Long? = null,
) {

    /**
     * [totalSeconds] in the account's [durationFormat], the text the web app's `duration()`
     * helper gives. Screens show every duration this way. It needs no zone, so it works when
     * [time] is null.
     */
    fun formatDuration(totalSeconds: Long): String = formatDuration(totalSeconds, durationFormat)

    /** [totalSeconds] as [formatDuration] writes it, without the seconds, where that does not fit. */
    fun formatDurationWithoutSeconds(totalSeconds: Long): String =
        formatDurationWithoutSeconds(totalSeconds, durationFormat)

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

/** The account settings, or null when `beginning_of_week` is not a day from 0 to 6. */
fun Me.accountSettings(): AccountSettings? = dayOfWeek(beginningOfWeek)?.let {
    AccountSettings(
        timezone,
        it,
        timeResolutionMinutes,
        defaultWorkspaceId = defaultWorkspaceId?.let(::WorkspaceId),
        userId = id,
    )
}

/**
 * The web app shows durations as improved when `duration_format` is missing or null, and when it
 * names any format other than classic or decimal. A value that is not a string reads as improved
 * too.
 */
val MePreferences.durationFormat: DurationFormat
    get() = DurationFormat.fromTogglName(string("duration_format"))

/**
 * The web app writes clock times on the 12-hour clock when `timeofday_format` holds an `a` in
 * either case, as `h:mm A` does, and on the 24-hour clock otherwise. A missing or null value reads
 * as the web app's default, `H:mm`, and so does a value that is not a string.
 */
val MePreferences.twelveHour: Boolean
    get() = string("timeofday_format")?.let(::isTwelveHourFormat) ?: false

/**
 * `collapseTimeEntries`, read as the web app's `prefs.collapseTimeEntries ?? true` and its
 * truthiness: missing or null groups, and so does any value JavaScript takes as true, such as a
 * non-empty string or a non-zero number. False, 0 and an empty string do not.
 */
val MePreferences.collapseSimilar: Boolean
    get() = truthy("collapseTimeEntries") ?: true

/** The day Toggl numbers [number], from 0 for Sunday to 6 for Saturday, or null outside that. */
internal fun dayOfWeek(number: Int): DayOfWeek? = if (number in 0 until DAYS_PER_WEEK) {
    DayOfWeek.SUNDAY.plus(number.toLong())
} else {
    null
}

/** Toggl's number for this day, from 0 for Sunday to 6 for Saturday. */
internal val DayOfWeek.togglNumber: Int
    get() = value % 7
