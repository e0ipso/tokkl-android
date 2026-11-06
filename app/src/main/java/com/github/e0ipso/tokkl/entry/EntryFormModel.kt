package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.cache.HistoryWindow
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.noTimeDay
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.MAX_ALL_DAY_SECONDS
import com.github.e0ipso.tokkl.time.NoTimeDuration
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.formatDuration
import com.github.e0ipso.tokkl.time.isTimeResolution
import com.github.e0ipso.tokkl.time.noTimeDuration
import com.github.e0ipso.tokkl.time.parseDuration
import com.github.e0ipso.tokkl.time.parseTimeOfDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        val confirmDelete: Boolean = false,
        val deleteFailed: Boolean = false,
        val running: Boolean = false,
        val noTimes: Boolean = false,
        val noTimesOffered: Boolean = false,
    ) : EntryFormState
}

/**
 * The form that edits a running segment, every field of a stopped entry (docs/PRD.md H2), or adds
 * a past entry by hand (M1), as the web app's EntryEditor.tsx and the manual mode of its TimerBar.tsx do: the
 * description, the project, task and tags, billable, the date, the start, and the end or the
 * duration. Times are read with the ports of Tokkl's parsers in the account's timezone (S7), and
 * an end earlier than the start falls on the next day.
 *
 * Leaving the start or the end fills in the duration, and leaving the duration fills in the end,
 * as start plus that many seconds, as in the web app's editor. On the edit screen a time field
 * left as it opened keeps the entry's own instant, with its seconds, and the duration counts only
 * once it differs from what the form last derived.
 *
 * The form opens on the cache's copy of an entry, or on the copy the history holds of an entry from
 * the older days the server sent, which the phone does not keep. The cache holds an older entry
 * edited on the phone only until the send and the refresh after it, so a form opened on that copy
 * saves as on the older days' copy once the cache has dropped it.
 *
 * With No times on, the entry is a no-time one, as in the web app's EntryEditor.tsx: a date and a
 * duration. The server stores it from midnight on that date in the account's timezone, with the
 * duration rounded to the time resolution. A no-time entry opens with No times on, and
 * [newAllDay] opens a new one for the calendar's All day row.
 *
 * Save checks the times. A field that cannot be read, or a span the server would refuse, gets a
 * [TimeMistake] and nothing is queued. Otherwise the change goes to [changes], the change queue,
 * with every field, so it is on the phone at once, works offline, and the last write to reach the
 * server wins (O3). Call everything on the main thread.
 *
 * Like ../tokkl/packages/web/src/components/EntryEditor.tsx, closing or replacing the editor
 * discards unsaved fields without asking or keeping a draft. Save queues the draft; Delete
 * queues the current stored entry and ignores unsaved fields.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param clock where now comes from, which a start without AM or PM is guessed against.
 * @param allDay whether the server stores no-time entries, as `Capabilities.allDay` says, which
 *   decides whether the form offers the No times switch.
 */
class EntryFormModel(
    private val entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    allDay: Flow<Boolean> = flowOf(false),
) {

    private val phase = MutableStateFlow<Phase>(Phase.Closed)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    val state: StateFlow<EntryFormState> = combine(phase, catalog, accountSettings, allDay) { current, names, settings, offered ->
        current.shown(names, settings, offered)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryFormState.Closed)

    /** Whether a form is open, or opening. False after [close], and in a new process. */
    val isOpen: Boolean get() = phase.value != Phase.Closed

    /**
     * Opens the form on the entry [entryId]: the cache's copy, or [older] when the cache does not
     * hold it, such as an entry from the older days the history holds in memory. A save of [older]
     * goes to the queue as [EntryChanges.updateOlder], with every field.
     */
    fun edit(entryId: Long, older: CachedTimeEntry? = null) = open(Opening.Edit(entryId, older?.takeIf { it.id == entryId }))

    /** Opens only the captured running segment; it can never fall back to an older-entry PUT. */
    fun editRunning(identity: RunningEntryIdentity) = open(Opening.Running(identity))

    /** Opens the form for a manual entry on [date], or on today's date in the account's timezone. */
    fun new(date: LocalDate? = null) = open(Opening.New(date))

    /** Opens the form for a no-time entry on [day], as the calendar's All day row adds one. */
    fun newAllDay(day: LocalDate) = open(Opening.New(day, noTimes = true))

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

    fun setDuration(text: String) = change { it.copy(duration = text, durationSeconds = null, mistakes = it.mistakes - TimeField.DURATION) }

    /**
     * Turns No times on or off, never for a running entry. On, the date stays and the duration
     * keeps what the form shows, read from the start and the end when only those were typed. Off,
     * the start and the end come back as they were, and the end follows a duration changed
     * meanwhile. An entry that was a no-time one gets midnight on the date as its start and the
     * end its duration later, for the user to change.
     */
    fun setNoTimes(on: Boolean) = changeWithTime { form, settings, time ->
        val unmarked = form.mistakes - TimeField.START - TimeField.END - TimeField.DURATION
        when {
            form.target is Target.Running || form.noTimes == on -> form
            on -> {
                // Times typed and not left yet give the duration, as leaving them would.
                val shown = if (form.timesTyped() && !form.durationChanged()) form.followTimes(settings, time) else form
                shown.copy(noTimes = true, mistakes = unmarked)
            }
            form.target is Target.Edit && form.target.snapshot.allDay -> form.timesFromMidnight(settings, time).copy(mistakes = unmarked)
            else -> form.followDuration(settings, time).copy(noTimes = false, mistakes = unmarked)
        }
    }

    /** The start or the end lost focus: the duration follows them when both can be read and one changed. */
    fun timesDone() = changeWithTime { form, settings, time ->
        if (form.target is Target.Running || form.noTimes) form else form.followTimes(settings, time)
    }

    /**
     * The duration lost focus: once it differs from what the form derived, the end follows it. A
     * no-time entry's duration shows what the server will store, once it is one the server takes.
     */
    fun durationDone() = changeWithTime { form, settings, time ->
        when {
            form.noTimes -> form.withStoredNoTimeDuration(settings)
            form.target is Target.Running -> form
            else -> form.followDuration(settings, time)
        }
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
            is Checked.Valid -> checked
        }
        val saving = form.copy(mistakes = emptyMap(), saving = true)
        phase.value = Phase.Open(saving)
        scope.launch {
            val saved = when (val target = form.target) {
                Target.New -> {
                    changes.create(workspaceId, form.fields, span.start, requireNotNull(span.stop), span.noTimeDay)
                    true
                }
                is Target.Running -> changes.updateRunning(target.identity, form.fields, span.start)
                is Target.Edit -> when {
                    target.onPhone && changes.update(target.entryId, form.fields, span.start, span.stop, span.noTimeDay) -> true
                    // An older entry goes whole, also one whose copy the refresh after its send dropped.
                    !target.onPhone || target.start < HistoryWindow.endingToday(time).start -> {
                        changes.updateOlder(target.entryId, target.workspaceId, form.fields, span.start, requireNotNull(span.stop), span.noTimeDay)
                        true
                    }
                    else -> false
                }
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

    /** Delete uses the entry as opened, never an unsaved field, and skips time validation. */
    fun delete(canRestore: Boolean, onDeleted: (CachedTimeEntry, Boolean) -> Unit) {
        val form = (phase.value as? Phase.Open)?.form?.takeIf { !it.saving } ?: return
        if (form.target !is Target.Edit) return
        if (canRestore) queueDelete(form, true, onDeleted)
        else phase.value = Phase.Open(form.copy(confirmDelete = true, deleteFailed = false))
    }

    fun cancelDelete() = change { it.copy(confirmDelete = false) }

    fun confirmDelete(onDeleted: (CachedTimeEntry, Boolean) -> Unit) {
        val form = (phase.value as? Phase.Open)?.form?.takeIf { it.confirmDelete && !it.saving } ?: return
        queueDelete(form, false, onDeleted)
    }

    private fun queueDelete(form: Form, undo: Boolean, onDeleted: (CachedTimeEntry, Boolean) -> Unit) {
        val target = form.target as? Target.Edit ?: return
        val deleting = form.copy(saving = true, confirmDelete = false, deleteFailed = false)
        phase.value = Phase.Open(deleting)
        scope.launch {
            val deleted = try {
                changes.delete(target.snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (deleted != null) onDeleted(deleted, undo)
            phase.update { current ->
                if (current != Phase.Open(deleting)) current
                else if (deleted != null) Phase.Saved
                else Phase.Open(form.copy(saving = false, confirmDelete = false, deleteFailed = true))
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
                    is Opening.New -> Phase.Open(newForm(time, opening.date, opening.noTimes))
                    is Opening.Running -> {
                        val entry = changes.runningEntry(opening.identity)
                        entry?.let { Phase.Open(runningForm(it, opening.identity, settings, time)) } ?: Phase.Missing
                    }
                    is Opening.Edit -> {
                        val cached = entries.first().firstOrNull { it.entry.id == opening.entryId }
                        val form = when {
                            cached != null -> editForm(cached, settings, time, onPhone = true)
                            opening.older != null -> editForm(EntryWithSendState(opening.older, unsent = false), settings, time, onPhone = false)
                            else -> null
                        }
                        form?.let { Phase.Open(it) } ?: Phase.Missing
                    }
                }
            }
            phase.update { if (it == waiting) next else it }
        }
    }

    private fun newForm(time: AccountTime, date: LocalDate?, noTimes: Boolean): Form =
        Form(
            target = Target.New,
            fields = EntryFields(),
            date = date ?: time.today(),
            start = "",
            end = "",
            duration = "",
            syncedDuration = "",
            noTimes = noTimes,
        )

    private fun runningForm(entry: CachedTimeEntry, identity: RunningEntryIdentity, settings: AccountSettings, time: AccountTime): Form {
        val date = time.local(entry.start).toLocalDate()
        val start = settings.formatTime(entry.start, time.zone)
        val fields = entry.fields()
        // Keep the resolved cache's seconds, including the server's normalization of a local start.
        val target = Target.Running(identity.copy(start = entry.start), entry.start, date, start, fields.picked)
        return Form(target, fields, date, start, end = "", duration = "", syncedDuration = "")
    }

    /**
     * The form on [cached], or null while it runs. An entry with a change the server lacks shows
     * the times the server will store, as the history does. [onPhone] says the cache holds it.
     */
    private fun editForm(cached: EntryWithSendState, settings: AccountSettings, time: AccountTime, onPhone: Boolean): Form? {
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
        val target = Target.Edit(entry.id, entry.workspaceId, entry.start, stop, date, start, end, fields.picked, onPhone, entry)
        return Form(target, fields, date, start, end, duration, syncedDuration = duration)
    }

    /**
     * The form on the no-time [entry]: its day, and its duration rounded to the time resolution as
     * the server rounds it. The server keeps its start at midnight, so it has no times to show and
     * no span to predict. Its times open as empty texts, so times the user gives it count as typed.
     */
    private fun noTimeForm(entry: CachedTimeEntry, stop: Instant, time: AccountTime, onPhone: Boolean): Form {
        val date = entry.noTimeDay(time.zone)
        val duration = formatDuration(time.snapDuration(stop.epochSecond - entry.start.epochSecond))
        val fields = entry.fields()
        val target = Target.Edit(entry.id, entry.workspaceId, entry.start, stop, date, startText = "", endText = "", fields.picked, onPhone, entry)
        return Form(target, fields, date, start = "", end = "", duration, syncedDuration = duration, noTimes = true)
    }

    /**
     * The span to save, or the mistakes that keep [form] from saving. The start must be read. A
     * duration that differs from the derived one sets the stop, and otherwise the end does, read
     * after the start. A field left as it opened keeps the entry's instant.
     */
    private fun check(form: Form, settings: AccountSettings, time: AccountTime): Checked {
        if (form.noTimes) return checkNoTimes(form, settings, time)
        val unreadable = if (form.target == Target.New) TimeMistake.MANUAL_TIMES else TimeMistake.TIMES
        val start = form.startInstant(settings, time) ?: return Checked.Invalid(TimeField.START, unreadable)
        if (form.target is Target.Running) return Checked.Valid(start, stop = null)
        val durationChanged = form.durationChanged()
        val stop = when {
            durationChanged -> (form.durationSeconds ?: parseDuration(form.duration))?.let { start.plusSecondsOrNull(it) }
                ?: return Checked.Invalid(TimeField.DURATION, TimeMistake.DURATION)
            form.target is Target.Edit && !form.endChanged() -> form.target.stop
            else -> resolveRange(form.date, form.start, form.end, time, settings.twelveHour, nowMinutes(time))?.stop
                ?: return Checked.Invalid(TimeField.END, unreadable)
        }
        val spanField = if (durationChanged) TimeField.DURATION else TimeField.END
        if (stop.epochSecond < start.epochSecond) return Checked.Invalid(spanField, TimeMistake.END_BEFORE_START)
        val predicted = time.snapUnsent(start, stop)
        // Tokkl permits an unchanged-time edit of a stored legacy short/zero-length entry.
        val target = form.target as? Target.Edit
        val unchanged = target != null && target.entryId > 0 && start == target.start && stop == target.stop
        if (predicted == null && !unchanged) return Checked.Invalid(spanField, TimeMistake.TOO_SHORT)
        val seconds = predicted?.let { it.stop.epochSecond - it.start.epochSecond } ?: (stop.epochSecond - start.epochSecond)
        return if (seconds > MAX_ENTRY_SECONDS) Checked.Invalid(spanField, TimeMistake.TOO_LONG)
        else Checked.Valid(start, stop)
    }

    /**
     * The span a no-time [form] saves: midnight on its date in the account's timezone, and the
     * duration the server will store, as the web app's noTimeDuration.ts reads it. Only the
     * duration can be wrong.
     */
    private fun checkNoTimes(form: Form, settings: AccountSettings, time: AccountTime): Checked {
        // A no-time entry saved with its day and duration as they opened keeps its stored span,
        // as the web app's noTimeEdit.ts does, so renaming one off the grid never rounds it.
        val target = form.target as? Target.Edit
        if (target != null && target.snapshot.allDay && form.date == target.date && !form.durationChanged()) {
            return Checked.Valid(target.start, target.stop, noTimeDay = form.date)
        }
        val read = noTimeDuration(form.duration, settings.timeResolutionMinutes)
        val seconds = read.seconds
        if (read.error != null || seconds == null) return Checked.Invalid(TimeField.DURATION, read.mistake(settings.timeResolutionMinutes))
        val span = time.noTimeSpan(form.date, seconds)
        return Checked.Valid(span.start, span.stop, noTimeDay = form.date)
    }

    /** The start [Form.start] means: the entry's own while the date and the start are as they opened. */
    private fun Form.startInstant(settings: AccountSettings, time: AccountTime): Instant? {
        val minutes = parseTimeOfDay(start, twelveHour = settings.twelveHour, reference = nowMinutes(time)) ?: return null
        return when {
            target is Target.Edit && !startChanged() -> target.start
            target is Target.Running && !startChanged() -> target.start
            else -> time.instantAt(date, minutes)
        }
    }

    private fun nowMinutes(time: AccountTime): Int = time.minutesOfDay(clock.instant())

    /** The duration of the span the start and the end give, when both can be read and one changed. */
    private fun Form.followTimes(settings: AccountSettings, time: AccountTime): Form {
        val range = resolveRange(date, start, end, time, settings.twelveHour, nowMinutes(time))
        if (range == null || !(startChanged() || endChanged())) return this
        val text = formatDuration(snappedSeconds(range.start, range.stop, time))
        return copy(duration = text, syncedDuration = text, durationSeconds = null)
    }

    /** The end the start and a changed duration give, with the duration the server will store. */
    private fun Form.followDuration(settings: AccountSettings, time: AccountTime): Form {
        if (!durationChanged()) return this
        val seconds = durationSeconds ?: parseDuration(duration) ?: return this
        val start = startInstant(settings, time) ?: return this
        val stop = start.plusSecondsOrNull(seconds) ?: return this
        return copy(end = settings.formatTime(stop, time.zone), duration = formatDuration(snappedSeconds(start, stop, time)), durationSeconds = seconds)
    }

    /** The no-time duration as the server will store it, when the server takes it. */
    private fun Form.withStoredNoTimeDuration(settings: AccountSettings): Form {
        val read = noTimeDuration(duration, settings.timeResolutionMinutes)
        val seconds = read.seconds?.takeIf { read.error == null } ?: return this
        return copy(duration = formatDuration(seconds))
    }

    /**
     * Times for a no-time entry: midnight on its date, and an end its duration later. Where that
     * end reads as another span, as midnight again after 24 hours, the duration sets the stop,
     * as after [durationDone].
     */
    private fun Form.timesFromMidnight(settings: AccountSettings, time: AccountTime): Form {
        val start = time.instantAt(date, 0)
        val startText = settings.formatTime(start, time.zone)
        val seconds = parseDuration(duration)
        val stop = seconds?.let { start.plusSecondsOrNull(it) } ?: return copy(noTimes = false, start = startText, end = "")
        val endText = settings.formatTime(stop, time.zone)
        val text = formatDuration(snappedSeconds(start, stop, time))
        val endSaysIt = resolveRange(date, startText, endText, time, settings.twelveHour, nowMinutes(time))?.stop == stop
        return copy(
            noTimes = false,
            start = startText,
            end = endText,
            duration = text,
            syncedDuration = text,
            durationSeconds = seconds.takeUnless { endSaysIt },
        )
    }

    private fun change(edit: (Form) -> Form) {
        phase.update { current -> if (current is Phase.Open && !current.form.saving) Phase.Open(edit(current.form)) else current }
    }

    private fun changeWithTime(edit: (Form, AccountSettings, AccountTime) -> Form) {
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        change { edit(it, settings, time) }
    }

    private fun Phase.shown(names: Catalog, settings: AccountSettings?, allDay: Boolean): EntryFormState = when (this) {
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
                form.shown(names, settings, time, allDay)
            }
        }
    }

    private fun Form.shown(names: Catalog, settings: AccountSettings, time: AccountTime, allDay: Boolean): EntryFormState.Open {
        val stop = if (noTimes) null else (check(this, settings, time) as? Checked.Valid)?.stop
        val endsOn = stop?.let { time.local(it).toLocalDate() }?.takeIf { it != date }
        return EntryFormState.Open(
            editing = target != Target.New,
            running = target is Target.Running,
            workspaceId = workspaceId(settings),
            fields = fields,
            labels = entryLabels(fields, names.projects, names.tasks),
            original = when (target) {
                Target.New -> Picked()
                is Target.Edit -> target.original
                is Target.Running -> target.original
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
            noTimesOffered = allDay && target !is Target.Running,
        )
    }

    private fun Form.workspaceId(settings: AccountSettings): Long? = when (target) {
        Target.New -> settings.defaultWorkspaceId
        is Target.Edit -> target.workspaceId
        is Target.Running -> target.identity.workspaceId
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
            Target.New -> true
            is Target.Edit -> date != target.date || start != target.startText
            is Target.Running -> date != target.date || start != target.startText
        }

        fun endChanged(): Boolean = target !is Target.Edit || date != target.date || end != target.endText

        fun durationChanged(): Boolean = durationSeconds != null || duration != syncedDuration

        /** The start or the end holds other text than the form opened with; a new date alone types neither. */
        fun timesTyped(): Boolean = when (target) {
            Target.New -> start.isNotEmpty() || end.isNotEmpty()
            is Target.Edit -> start != target.startText || end != target.endText
            is Target.Running -> false
        }
    }

    private sealed interface Target {
        /** The active segment only. A later Start or Resume must have a different identity. */
        data class Running(
            val identity: RunningEntryIdentity,
            val start: Instant,
            val date: LocalDate,
            val startText: String,
            val original: Picked,
        ) : Target

        /** A manual entry, in the account's default workspace. */
        data object New : Target

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
        ) : Target
    }

    private sealed interface Opening {
        data class Running(val identity: RunningEntryIdentity) : Opening

        data class New(val date: LocalDate?, val noTimes: Boolean = false) : Opening

        data class Edit(val entryId: Long, val older: CachedTimeEntry?) : Opening
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
        /** [noTimeDay] is the day of a no-time entry, and null for a timed one. */
        data class Valid(val start: Instant, val stop: Instant?, val noTimeDay: LocalDate? = null) : Checked

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

/**
 * The mistake for a duration [noTimeDuration] refused, by its rules: one it cannot read, one over a
 * day, or one that rounds to zero at the time resolution [minutes]. Without a resolution a zero
 * gets the message for a duration it cannot read, as in the web app.
 */
private fun NoTimeDuration.mistake(minutes: Double?): TimeMistake {
    val resolution = minutes?.takeIf(::isTimeResolution)
    return when {
        seconds == null -> TimeMistake.NO_TIME_DURATION
        seconds > MAX_ALL_DAY_SECONDS -> TimeMistake.NO_TIME_TOO_LONG
        resolution != null -> TimeMistake.NoTimeRoundsToZero(resolution.toInt())
        else -> TimeMistake.NO_TIME_DURATION
    }
}
