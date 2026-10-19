package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.parseDuration
import com.github.e0ipso.tokkl.time.parseTimeOfDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A time field of the entry form. */
enum class TimeField { START, END, DURATION }

/** Why the entry form cannot save its times. Each shows as text under its [TimeField]. */
enum class TimeMistake {
    /** The edit screen cannot read the start or the end, as the web app's editor says it. */
    TIMES,

    /** The manual entry form cannot read the start or the end, as the web app's manual mode says it. */
    MANUAL_TIMES,

    DURATION,

    /** The entry would stop before it starts. */
    END_BEFORE_START,

    /** The entry would be longer than the 999 hours Tokkl and Toggl store. */
    TOO_LONG,
}

/** What the entry form shows. */
sealed interface EntryFormState {

    /** No form is open. */
    data object Closed : EntryFormState

    /** The form waits for the cache and the account settings. */
    data object Loading : EntryFormState

    /** The entry to edit is not on the phone any more, or runs, which the timer edits instead. */
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
     * @property date the day the start falls on, in the account's timezone.
     * @property start what the start field holds: a clock time as typed, such as `9`, `930` or `4pm`.
     * @property end as [start], for the end. An end earlier than the start is on the next day.
     * @property duration what the duration field holds, such as `1h 30m`, `90` or `1,5h`.
     * @property endsOn the day the entry ends on, when that is not [date], as far as the fields can be read.
     * @property saving the change is being written to the phone.
     */
    data class Open(
        val editing: Boolean,
        val workspaceId: Long?,
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
    ) : EntryFormState
}

/**
 * The form that edits every field of a stopped entry (docs/PRD.md H2) or adds a past entry by
 * hand (M1), as the web app's EntryEditor.tsx and the manual mode of its TimerBar.tsx do: the
 * description, the project, task and tags, billable, the date, the start, and the end or the
 * duration. Times are read with the ports of Tokkl's parsers in the account's timezone (S7), and
 * an end earlier than the start falls on the next day.
 *
 * Leaving the start or the end fills in the duration, and leaving the duration fills in the end,
 * as start plus that many seconds, as in the web app's editor. On the edit screen a time field
 * left as it opened keeps the entry's own instant, with its seconds, and the duration counts only
 * once it differs from what the form last derived.
 *
 * Save checks the times. A field that cannot be read, or a span the server would refuse, gets a
 * [TimeMistake] and nothing is queued. Otherwise the change goes to [changes], the change queue,
 * with every field, so it is on the phone at once, works offline, and the last write to reach the
 * server wins (O3). Call everything on the main thread.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param clock where now comes from, which a start without AM or PM is guessed against.
 */
class EntryFormModel(
    private val entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val phase = MutableStateFlow<Phase>(Phase.Closed)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    val state: StateFlow<EntryFormState> = combine(phase, catalog, accountSettings) { current, names, settings ->
        current.shown(names, settings)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryFormState.Closed)

    /** Whether a form is open, or opening. False after [close], and in a new process. */
    val isOpen: Boolean get() = phase.value != Phase.Closed

    /** Opens the form on the cached entry [entryId]. */
    fun edit(entryId: Long) = open(Opening.Edit(entryId))

    /** Opens the form for a manual entry, on today's date in the account's timezone. */
    fun new() = open(Opening.New)

    fun close() {
        phase.value = Phase.Closed
    }

    fun setDescription(description: String) = change { it.copy(fields = it.fields.copy(description = description)) }

    /** Sets the project, task and tags, as [withPicked] says, so a billable project makes the entry billable. */
    fun pick(picked: Picked) = change { it.copy(fields = it.fields.withPicked(picked, catalog.value.projects)) }

    fun setBillable(billable: Boolean) = change { it.copy(fields = it.fields.copy(billable = billable)) }

    fun setDate(date: LocalDate) = change { it.copy(date = date, mistakes = it.mistakes - TimeField.START - TimeField.END) }

    fun setStart(text: String) = change { it.copy(start = text, mistakes = it.mistakes - TimeField.START) }

    fun setEnd(text: String) = change { it.copy(end = text, mistakes = it.mistakes - TimeField.END) }

    fun setDuration(text: String) = change { it.copy(duration = text, mistakes = it.mistakes - TimeField.DURATION) }

    /** The start or the end lost focus: the duration follows them when both can be read and one changed. */
    fun timesDone() = changeWithTime { form, settings, time ->
        val range = resolveRange(form.date, form.start, form.end, time, settings.twelveHour, nowMinutes(time))
        if (range == null || !(form.startChanged() || form.endChanged())) return@changeWithTime form
        val text = formatDuration(snappedSeconds(range.start, range.stop, time))
        form.copy(duration = text, syncedDuration = text)
    }

    /** The duration lost focus: once it differs from what the form derived, the end follows it. */
    fun durationDone() = changeWithTime { form, settings, time ->
        if (!form.durationChanged()) return@changeWithTime form
        val seconds = parseDuration(form.duration) ?: return@changeWithTime form
        val start = form.startInstant(settings, time) ?: return@changeWithTime form
        val stop = start.plusSecondsOrNull(seconds) ?: return@changeWithTime form
        form.copy(end = settings.formatTime(stop, time.zone), duration = formatDuration(snappedSeconds(start, stop, time)))
    }

    /** Checks the times and queues the entry, or marks what keeps it from saving. */
    fun save() {
        val form = (phase.value as? Phase.Open)?.form?.takeIf { !it.saving } ?: return
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        val workspaceId = form.workspaceId(settings) ?: return
        val span = when (val checked = check(form, settings, time)) {
            is Checked.Invalid -> {
                phase.value = Phase.Open(form.copy(mistakes = checked.mistakes))
                return
            }
            is Checked.Valid -> checked.span
        }
        val saving = form.copy(mistakes = emptyMap(), saving = true)
        phase.value = Phase.Open(saving)
        scope.launch {
            val saved = when (val target = form.target) {
                Target.New -> {
                    changes.create(workspaceId, form.fields, span.start, span.stop)
                    true
                }
                is Target.Edit -> changes.update(target.entryId, form.fields, span.start, span.stop)
            }
            phase.update { current ->
                when {
                    current != Phase.Open(saving) -> current
                    saved -> Phase.Saved
                    else -> Phase.Missing
                }
            }
        }
    }

    private fun open(opening: Opening) {
        val waiting = Phase.Waiting(opening)
        phase.value = waiting
        scope.launch {
            val settings = accountSettings.filterNotNull().first()
            val next = when (val time = settings.time(clock)) {
                null -> Phase.NoAccountTime
                else -> when (opening) {
                    Opening.New -> Phase.Open(newForm(time))
                    is Opening.Edit -> entries.first().firstOrNull { it.entry.id == opening.entryId }
                        ?.let { editForm(it, settings, time) }
                        ?.let { Phase.Open(it) }
                        ?: Phase.Missing
                }
            }
            phase.update { if (it == waiting) next else it }
        }
    }

    private fun newForm(time: AccountTime): Form =
        Form(target = Target.New, fields = EntryFields(), date = time.today(), start = "", end = "", duration = "", syncedDuration = "")

    /**
     * The form on [cached], or null while it runs. An entry with a change the server lacks shows
     * the times the server will store, as the history does.
     */
    private fun editForm(cached: EntryWithSendState, settings: AccountSettings, time: AccountTime): Form? {
        val entry = cached.entry
        val stop = entry.stop ?: return null
        val shown = if (cached.unsent && !stop.isBefore(entry.start)) time.snapUnsent(entry.start, stop) else SnappedSpan(entry.start, stop)
        val date = time.local(shown.start).toLocalDate()
        val start = settings.formatTime(shown.start, time.zone)
        val end = settings.formatTime(shown.stop, time.zone)
        val duration = formatDuration(snappedSeconds(shown.start, shown.stop, time))
        val fields = entry.fields()
        val target = Target.Edit(entry.id, entry.workspaceId, entry.start, stop, date, start, end, fields.picked)
        return Form(target, fields, date, start, end, duration, syncedDuration = duration)
    }

    /**
     * The span to save, or the mistakes that keep [form] from saving. The start must be read. A
     * duration that differs from the derived one sets the stop, and otherwise the end does, read
     * after the start. A field left as it opened keeps the entry's instant.
     */
    private fun check(form: Form, settings: AccountSettings, time: AccountTime): Checked {
        val unreadable = if (form.target == Target.New) TimeMistake.MANUAL_TIMES else TimeMistake.TIMES
        val start = form.startInstant(settings, time) ?: return Checked.Invalid(TimeField.START, unreadable)
        val durationChanged = form.durationChanged()
        val stop = when {
            durationChanged -> parseDuration(form.duration)?.let { start.plusSecondsOrNull(it) }
                ?: return Checked.Invalid(TimeField.DURATION, TimeMistake.DURATION)
            form.target is Target.Edit && !form.endChanged() -> form.target.stop
            else -> resolveRange(form.date, form.start, form.end, time, settings.twelveHour, nowMinutes(time))?.stop
                ?: return Checked.Invalid(TimeField.END, unreadable)
        }
        val spanField = if (durationChanged) TimeField.DURATION else TimeField.END
        return when {
            stop.epochSecond < start.epochSecond -> Checked.Invalid(spanField, TimeMistake.END_BEFORE_START)
            snappedSeconds(start, stop, time) > MAX_ENTRY_SECONDS -> Checked.Invalid(spanField, TimeMistake.TOO_LONG)
            else -> Checked.Valid(TimeRange(start, stop))
        }
    }

    /** The start [Form.start] means: the entry's own while the date and the start are as they opened. */
    private fun Form.startInstant(settings: AccountSettings, time: AccountTime): Instant? {
        val minutes = parseTimeOfDay(start, twelveHour = settings.twelveHour, reference = nowMinutes(time)) ?: return null
        return if (target is Target.Edit && !startChanged()) target.start else time.instantAt(date, minutes)
    }

    private fun nowMinutes(time: AccountTime): Int = time.minutesOfDay(clock.instant())

    private fun change(edit: (Form) -> Form) {
        phase.update { current -> if (current is Phase.Open && !current.form.saving) Phase.Open(edit(current.form)) else current }
    }

    private fun changeWithTime(edit: (Form, AccountSettings, AccountTime) -> Form) {
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        change { edit(it, settings, time) }
    }

    private fun Phase.shown(names: Catalog, settings: AccountSettings?): EntryFormState = when (this) {
        Phase.Closed -> EntryFormState.Closed
        is Phase.Waiting -> EntryFormState.Loading
        Phase.Missing -> EntryFormState.Missing
        Phase.Saved -> EntryFormState.Saved
        Phase.NoAccountTime -> settings?.let { EntryFormState.NoAccountTime(it.timeZone) } ?: EntryFormState.Loading
        is Phase.Open -> {
            val time = settings?.time(clock)
            if (settings == null || time == null) {
                settings?.let { EntryFormState.NoAccountTime(it.timeZone) } ?: EntryFormState.Loading
            } else {
                form.shown(names, settings, time)
            }
        }
    }

    private fun Form.shown(names: Catalog, settings: AccountSettings, time: AccountTime): EntryFormState.Open {
        val stop = (check(this, settings, time) as? Checked.Valid)?.span?.stop
        val endsOn = stop?.let { time.local(it).toLocalDate() }?.takeIf { it != date }
        return EntryFormState.Open(
            editing = target is Target.Edit,
            workspaceId = workspaceId(settings),
            fields = fields,
            labels = entryLabels(fields, names.projects, names.tasks),
            original = (target as? Target.Edit)?.original ?: Picked(),
            date = date,
            start = start,
            end = end,
            duration = duration,
            mistakes = mistakes,
            endsOn = endsOn,
            saving = saving,
        )
    }

    private fun Form.workspaceId(settings: AccountSettings): Long? = when (target) {
        Target.New -> settings.defaultWorkspaceId
        is Target.Edit -> target.workspaceId
    }

    private fun Instant.plusSecondsOrNull(seconds: Long): Instant? =
        try {
            plusSeconds(seconds)
        } catch (_: ArithmeticException) {
            null
        } catch (_: java.time.DateTimeException) {
            null
        }

    /** What the form holds, apart from the names of its project and task. */
    private data class Form(
        val target: Target,
        val fields: EntryFields,
        val date: LocalDate,
        val start: String,
        val end: String,
        val duration: String,
        /** The duration the form last derived from the start and the end. */
        val syncedDuration: String,
        val mistakes: Map<TimeField, TimeMistake> = emptyMap(),
        val saving: Boolean = false,
    ) {
        fun startChanged(): Boolean = target !is Target.Edit || date != target.date || start != target.startText

        fun endChanged(): Boolean = target !is Target.Edit || date != target.date || end != target.endText

        fun durationChanged(): Boolean = duration != syncedDuration
    }

    private sealed interface Target {
        /** A manual entry, in the account's default workspace. */
        data object New : Target

        /**
         * The cached entry [entryId], which spans [start] to [stop] and opened on [date] with
         * [startText] and [endText].
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
        ) : Target
    }

    private sealed interface Opening {
        data object New : Opening

        data class Edit(val entryId: Long) : Opening
    }

    private sealed interface Phase {
        data object Closed : Phase

        /** The form opens once the account settings and the cache are read. */
        data class Waiting(val opening: Opening) : Phase

        data object Missing : Phase

        data object NoAccountTime : Phase

        data object Saved : Phase

        data class Open(val form: Form) : Phase
    }

    private sealed interface Checked {
        data class Valid(val span: TimeRange) : Checked

        data class Invalid(val mistakes: Map<TimeField, TimeMistake>) : Checked {
            constructor(field: TimeField, mistake: TimeMistake) : this(mapOf(field to mistake))
        }
    }

    private data class Catalog(val projects: List<CachedProject> = emptyList(), val tasks: List<CachedTask> = emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** The longest entry Tokkl and Toggl store: 999 hours, MAX_DURATION_SECONDS in ../tokkl/packages/common/duration.ts. */
        const val MAX_ENTRY_SECONDS = 999L * 3600
    }
}
