package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.fields
import com.github.e0ipso.tokkl.cache.noTimeDay
import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.entryfields.picked
import com.github.e0ipso.tokkl.entryfields.snappedSeconds
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.formatDuration
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** What the form holds, apart from the names of its project and task. */
internal data class EntryDraft(
    val target: DraftTarget,
    val fields: EntryFields,
    val date: LocalDate,
    val start: String,
    val end: String,
    val duration: String,
    /** The duration the form last derived from the start and the end. */
    val syncedDuration: String,
    /** The typed elapsed duration, retained when the display shows its snapped prediction. */
    val durationSeconds: Long? = null,
    val mistakes: Map<TimeField, TimeMistake> = emptyMap(),
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
    val deleteFailed: Boolean = false,
    /** The entry is a no-time one; [start] and [end] stay, hidden, for when it gets times again. */
    val noTimes: Boolean = false,
) {
    fun startChanged(): Boolean = when (target) {
        DraftTarget.New -> true
        is DraftTarget.Edit -> date != target.date || start != target.startText
        is DraftTarget.Running -> date != target.date || start != target.startText
    }

    fun endChanged(): Boolean = target !is DraftTarget.Edit || date != target.date || end != target.endText

    fun durationChanged(): Boolean = durationSeconds != null || duration != syncedDuration

    /** The start or the end holds other text than the form opened with; a new date alone types neither. */
    fun timesTyped(): Boolean = when (target) {
        DraftTarget.New -> start.isNotEmpty() || end.isNotEmpty()
        is DraftTarget.Edit -> start != target.startText || end != target.endText
        is DraftTarget.Running -> false
    }
}

/** What the draft saves to. */
internal sealed interface DraftTarget {
    /** The active segment only. A later Start or Resume must have a different identity. */
    data class Running(
        val identity: RunningEntryIdentity,
        val start: Instant,
        val date: LocalDate,
        val startText: String,
        val original: Picked,
    ) : DraftTarget

    /** A manual entry, in the account's default workspace. */
    data object New : DraftTarget

    /**
     * The entry [entryId], which spans [start] to [stop] and opened on [date] with [startText]
     * and [endText]. [onPhone] when the cache held it, and false for an entry from the older
     * days, which the queue edits with [EntryChanges.updateOlder]. [snapshot] is that opened
     * entry, which Delete uses when the cache no longer holds it.
     */
    data class Edit(
        val entryId: Long,
        val workspaceId: Long,
        val start: Instant,
        val stop: Instant,
        val date: LocalDate,
        val startText: String,
        val endText: String,
        val original: Picked,
        val onPhone: Boolean,
        val snapshot: CachedTimeEntry,
    ) : DraftTarget
}

/** The span a draft saves, or the mistakes that keep it from saving. */
internal sealed interface CheckedTimes {
    /** [noTimeDay] is the day of a no-time entry, and null for a timed one. */
    data class Valid(val start: Instant, val stop: Instant?, val noTimeDay: LocalDate? = null) : CheckedTimes

    data class Invalid(val mistakes: Map<TimeField, TimeMistake>) : CheckedTimes {
        constructor(field: TimeField, mistake: TimeMistake) : this(mapOf(field to mistake))
    }
}

/** The draft of a manual entry on [date], or today, with No times on when [noTimes]. */
internal fun newForm(time: AccountTime, date: LocalDate?, noTimes: Boolean): EntryDraft =
    EntryDraft(
        target = DraftTarget.New,
        fields = EntryFields(),
        date = date ?: time.today(),
        start = "",
        end = "",
        duration = "",
        syncedDuration = "",
        noTimes = noTimes,
    )

/** The draft of the running segment [entry], captured as [identity]. */
internal fun runningForm(entry: CachedTimeEntry, identity: RunningEntryIdentity, settings: AccountSettings, time: AccountTime): EntryDraft {
    val date = time.local(entry.start).toLocalDate()
    val start = settings.formatTime(entry.start, time.zone)
    val fields = entry.fields()
    // Keep the resolved cache's seconds, including the server's normalization of a local start.
    val target = DraftTarget.Running(identity.copy(start = entry.start), entry.start, date, start, fields.picked)
    return EntryDraft(target, fields, date, start, end = "", duration = "", syncedDuration = "")
}

/**
 * The form on [cached], or null while it runs. An entry with a change the server lacks shows
 * the times the server will store, as the history does. [onPhone] says the cache holds it.
 */
internal fun editForm(cached: EntryWithSendState, settings: AccountSettings, time: AccountTime, onPhone: Boolean): EntryDraft? {
    val entry = cached.entry
    val stop = entry.stop ?: return null
    if (entry.allDay) return noTimeForm(entry, stop, time, onPhone)
    // Keep rejected/legacy entries editable at their actual times, still marked unsent by the
    // history. A null prediction is never a replacement stored span.
    val predicted = if (cached.unsent && !stop.isBefore(entry.start)) time.snapUnsent(entry.start, stop) else null
    val shown = predicted ?: SnappedSpan(entry.start, stop)
    val date = time.local(shown.start).toLocalDate()
    val start = settings.formatTime(shown.start, time.zone)
    val end = settings.formatTime(shown.stop, time.zone)
    val duration = formatDuration(snappedSeconds(shown.start, shown.stop, time))
    val fields = entry.fields()
    val target = DraftTarget.Edit(entry.id, entry.workspaceId, entry.start, stop, date, start, end, fields.picked, onPhone, entry)
    return EntryDraft(target, fields, date, start, end, duration, syncedDuration = duration)
}

/**
 * The form on the no-time [entry]: its day, and its duration rounded to the time resolution as
 * the server rounds it. The server keeps its start at midnight, so it has no times to show and
 * no span to predict. Its times open as empty texts, so times the user gives it count as typed.
 */
internal fun noTimeForm(entry: CachedTimeEntry, stop: Instant, time: AccountTime, onPhone: Boolean): EntryDraft {
    val date = entry.noTimeDay(time.zone)
    val duration = formatDuration(time.snapDuration(stop.epochSecond - entry.start.epochSecond))
    val fields = entry.fields()
    val target = DraftTarget.Edit(entry.id, entry.workspaceId, entry.start, stop, date, startText = "", endText = "", fields.picked, onPhone, entry)
    return EntryDraft(target, fields, date, start = "", end = "", duration, syncedDuration = duration, noTimes = true)
}

/**
 * What the form shows for this draft, with the names from [projects] and [tasks]. [allDay] says
 * whether the server stores no-time entries.
 */
internal fun EntryDraft.shown(
    projects: List<CachedProject>,
    tasks: List<CachedTask>,
    settings: AccountSettings,
    time: AccountTime,
    allDay: Boolean,
    clock: Clock,
): EntryFormState.Open {
    val stop = if (noTimes) null else (check(this, settings, time, clock) as? CheckedTimes.Valid)?.stop
    val endsOn = stop?.let { time.local(it).toLocalDate() }?.takeIf { it != date }
    return EntryFormState.Open(
        editing = target != DraftTarget.New,
        running = target is DraftTarget.Running,
        workspaceId = workspaceId(settings),
        fields = fields,
        labels = entryLabels(fields, projects, tasks),
        original = when (target) {
            DraftTarget.New -> Picked()
            is DraftTarget.Edit -> target.original
            is DraftTarget.Running -> target.original
        },
        date = date,
        start = start,
        end = end,
        duration = duration,
        mistakes = mistakes,
        endsOn = endsOn,
        saving = saving,
        confirmDelete = confirmDelete,
        deleteFailed = deleteFailed,
        noTimes = noTimes,
        noTimesOffered = allDay && target !is DraftTarget.Running,
    )
}

/** Where the draft saves: its entry's workspace, or the account's default for a manual entry. */
internal fun EntryDraft.workspaceId(settings: AccountSettings): Long? = when (target) {
    DraftTarget.New -> settings.defaultWorkspaceId
    is DraftTarget.Edit -> target.workspaceId
    is DraftTarget.Running -> target.identity.workspaceId
}
