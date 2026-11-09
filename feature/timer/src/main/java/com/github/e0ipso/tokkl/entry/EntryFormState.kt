package com.github.e0ipso.tokkl.entry

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.picker.PickerSource
import java.time.LocalDate

/** A time field of the entry form. */
enum class TimeField { START, END, DURATION }

/**
 * Why the entry form cannot save its times. Each shows as text under its [TimeField]. The no-time
 * ones are the messages of [noTimeDuration], the web app's noTimeDuration.ts.
 */
sealed interface TimeMistake {
    /** The edit screen cannot read the start or the end, as the web app's editor says it. */
    data object TIMES : TimeMistake

    /** The manual entry form cannot read the start or the end, as the web app's manual mode says it. */
    data object MANUAL_TIMES : TimeMistake

    data object DURATION : TimeMistake

    /** The entry would stop before it starts. */
    data object END_BEFORE_START : TimeMistake

    /** The entry would be longer than the 999 hours Tokkl and Toggl store. */
    data object TOO_LONG : TimeMistake

    /** The typed span is too short at the account's time resolution. */
    data object TOO_SHORT : TimeMistake

    /** A no-time entry's duration cannot be read, or is zero on an account without a time resolution. */
    data object NO_TIME_DURATION : TimeMistake

    /** A no-time entry's duration rounds to zero at the account's time resolution of [minutes]. */
    data class NoTimeRoundsToZero(val minutes: Int) : TimeMistake

    /** A no-time entry would last more than a day. */
    data object NO_TIME_TOO_LONG : TimeMistake
}

/** What the entry form shows. */
@Immutable
sealed interface EntryFormState {

    /** No form is open. */
    data object Closed : EntryFormState

    /** The form waits for the cache and the account settings. */
    data object Loading : EntryFormState

    /** The entry is unavailable, or the running segment stopped or changed while editing. */
    data object Missing : EntryFormState

    /** The phone cannot resolve the account's timezone, [timeZone], so the form takes no times. */
    data class NoAccountTime(val timeZone: String) : EntryFormState

    /** The change is on the phone and queued for the server, so the form closes. */
    data object Saved : EntryFormState

    /**
     * The form, for a manual entry or, with [editing], for an existing one.
     *
     * @property workspaceId where the entry is, or for a manual entry the account's default
     *   workspace, which is null until the phone has read it. The fields and Save wait for it.
     * @property original the project, task and tags the entry had, which the picker keeps on offer
     *   when they are archived.
     * @property date the day the start falls on, in the account's timezone, or a no-time entry's day.
     * @property start what the start field holds: a clock time as typed, such as `9`, `930` or `4pm`.
     * @property end as [start], for the end. An end earlier than the start is on the next day.
     * @property duration what the duration field holds, such as `1h 30m`, `90` or `1,5h`.
     * @property endsOn the day the entry ends on, when that is not [date], as far as the fields can be read.
     *   Null for a no-time entry.
     * @property saving the change is being written to the phone.
     * @property confirmDelete Delete is waiting for a yes, on a server that cannot restore.
     * @property deleteFailed the last Delete could not be queued.
     * @property noTimes the entry is a no-time one, with [date] and [duration] and no start or end.
     * @property noTimesOffered the form shows the No times switch: the server stores no-time entries
     *   and the entry does not run.
     */
    data class Open(
        val editing: Boolean,
        val workspaceId: WorkspaceId?,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
        val date: LocalDate,
        val start: String,
        val end: String,
        val duration: String,
        val mistakes: Map<TimeField, TimeMistake>,
        val endsOn: LocalDate?,
        val saving: Boolean,
        val confirmDelete: Boolean = false,
        val deleteFailed: Boolean = false,
        val running: Boolean = false,
        val noTimes: Boolean = false,
        val noTimesOffered: Boolean = false,
    ) : EntryFormState
}

/** What the entry form's screen draws: the [form], what its picker offers, and whether the phone is [offline]. */
@Immutable
data class EntryFormPage(
    val form: EntryFormState = EntryFormState.Closed,
    val picker: PickerSource = PickerSource(),
    val offline: Boolean = false,
)
