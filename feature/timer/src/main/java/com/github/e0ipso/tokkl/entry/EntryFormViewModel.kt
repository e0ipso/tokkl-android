package com.github.e0ipso.tokkl.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.LocalWrite
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.cache.localWrite
import com.github.e0ipso.tokkl.entries.SessionEntries
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.time.AccountTime
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
import java.time.Clock
import java.time.LocalDate

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
 * The form keeps in [saved] what a new process needs to open it again after the old one died
 * with it on screen ([SavedEntryForm]): the copy it opened on of an entry the cache lacked, and
 * the fields typed. The first opening after that takes them: it reads the entry from the cache
 * again, or a missing one is missing, and puts what was typed over it.
 *
 * @param entries the cached entries with their send state, as AccountCache.entries gives them.
 * @param changes the change queue, which a new or edited stopped entry goes to.
 * @param running the change queue's edits of the running segment, which the form opens and saves.
 * @param pastEdits the session's entries, which save an edit whether the cache holds the entry or
 *   an older page or a server week does.
 * @param clock where now comes from, which a start without AM or PM is guessed against.
 * @param allDay whether the server stores no-time entries, as `Capabilities.allDay` says, which
 *   decides whether the form offers the No times switch.
 * @param picker the cached projects, clients, tasks and tags, which the form's picker offers.
 * @param offline whether the phone is offline, which the picker says.
 * @param saved the form's saved state, which outlives the process.
 */
class EntryFormViewModel(
    private val entries: Flow<List<EntryWithSendState>>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    private val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val running: RunningEntryChanges,
    private val pastEdits: SessionEntries,
    scope: CoroutineScope,
    private val clock: Clock,
    allDay: Flow<Boolean>,
    picker: Flow<PickerSource>,
    private val offline: StateFlow<Boolean>,
    private val saved: SavedStateHandle,
) : ViewModel(scope) {

    private val phase = MutableStateFlow<Phase>(Phase.Closed)

    /** What the process before kept of the form, until the first opening takes it. */
    private var restoring: SavedEntryForm? = saved.get<String>(SAVED_FORM)?.let(SavedEntryForm::decode)

    /** The form as it opened, which what was typed is told from, and the copy it opened on. */
    private var opened: EntryDraft? = null
    private var openedOn: TimeEntry? = null

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    /** The form, before the picker and the network join it in [page]. */
    internal val state: StateFlow<EntryFormState> = combine(
        phase,
        catalog,
        accountSettings,
        allDay,
    ) { current, names, settings, offered ->
        current.shown(names, settings, offered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryFormState.Closed)

    /** What the form's screen draws: the form, what its picker offers, and whether the phone is offline. */
    val page: StateFlow<EntryFormPage> = combine(
        state,
        picker.stateIn(viewModelScope, whileShown, PickerSource()),
        offline,
        ::EntryFormPage,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryFormPage())

    /** Whether a form is open, or opening. False after [close], and before the first opening. */
    val isOpen: Boolean get() = phase.value != Phase.Closed

    /**
     * Opens the form on the entry [entryId]: the cache's copy, or [older] when the cache does not
     * hold it, such as an entry from the older days the history holds in memory. A save of [older]
     * goes to the queue as [EntryChanges.updateOlder], with every field.
     */
    fun edit(entryId: EntryId, older: TimeEntry? = null) = open(
        Opening.Edit(entryId, older?.takeIf { it.id == entryId }),
    )

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

    fun setDate(date: LocalDate) = change {
        it.copy(
            date = date,
            mistakes = it.mistakes - TimeField.START - TimeField.END,
        )
    }

    fun setStart(text: String) = change { it.copy(start = text, mistakes = it.mistakes - TimeField.START) }

    fun setEnd(text: String) = change { it.copy(end = text, mistakes = it.mistakes - TimeField.END) }

    fun setDuration(text: String) = change {
        it.copy(
            duration = text,
            durationSeconds = null,
            mistakes = it.mistakes - TimeField.DURATION,
        )
    }

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
            form.target is DraftTarget.Running || form.noTimes == on -> form

            on -> {
                // Times typed and not left yet give the duration, as leaving them would.
                val shown = if (form.timesTyped() && !form.durationChanged()) {
                    form.followTimes(
                        settings,
                        time,
                        clock,
                    )
                } else {
                    form
                }
                shown.copy(noTimes = true, mistakes = unmarked)
            }

            form.target is DraftTarget.Edit && form.target.noTime -> form.timesFromMidnight(
                settings,
                time,
                clock,
            ).copy(mistakes = unmarked)

            else -> form.followDuration(settings, time, clock).copy(noTimes = false, mistakes = unmarked)
        }
    }

    /** The start or the end lost focus: the duration follows them when both can be read and one changed. */
    fun timesDone() = changeWithTime { form, settings, time ->
        if (form.target is DraftTarget.Running || form.noTimes) form else form.followTimes(settings, time, clock)
    }

    /**
     * The duration lost focus: once it differs from what the form derived, the end follows it. A
     * no-time entry's duration shows what the server will store, once it is one the server takes.
     */
    fun durationDone() = changeWithTime { form, settings, time ->
        when {
            form.noTimes -> form.withStoredNoTimeDuration(settings)
            form.target is DraftTarget.Running -> form
            else -> form.followDuration(settings, time, clock)
        }
    }

    /** Checks the times and queues the entry, or marks what keeps it from saving. */
    fun save() {
        val form = (phase.value as? Phase.Open)?.form?.takeIf { !it.saving } ?: return
        val settings = accountSettings.value ?: return
        val time = settings.time(clock) ?: return
        val workspaceId = form.workspaceId(settings) ?: return
        val timing = when (val checked = check(form, settings, time, clock)) {
            is CheckedTimes.Invalid -> {
                phase.value = Phase.Open(form.copy(mistakes = checked.mistakes))
                return
            }

            is CheckedTimes.Valid -> checked.timing
        }
        val saving = form.copy(mistakes = emptyMap(), saving = true)
        phase.value = Phase.Open(saving)
        viewModelScope.launch {
            var outcome: LocalWrite<Boolean>? = null
            try {
                outcome = localWrite { write(form, timing, workspaceId) }
            } finally {
                // A write the storage refused, or one that threw, gives the form back to the user.
                phase.update { current ->
                    when {
                        current != Phase.Open(saving) -> current
                        outcome == LocalWrite.Written(true) -> Phase.Saved
                        outcome == LocalWrite.Written(false) -> Phase.Missing
                        else -> Phase.Open(saving.copy(saving = false))
                    }
                }
            }
        }
    }

    /**
     * Queues [form] with its checked [timing]: a new entry in [workspaceId], the running segment, or
     * an edit. Returns false when the entry it edits has gone.
     */
    private suspend fun write(form: EntryDraft, timing: EntryTiming, workspaceId: WorkspaceId) =
        when (val target = form.target) {
            DraftTarget.New -> {
                changes.create(workspaceId, form.fields, timing.ended())
                true
            }

            is DraftTarget.Running -> running.updateRunning(target.identity, form.fields, timing.start)

            is DraftTarget.Edit -> pastEdits.saveEdit(
                target.entryId,
                target.workspaceId,
                target.start,
                target.onPhone,
                form.fields,
                timing.ended(),
            )
        }

    /** [check] gives a new entry's and an edit's timing an end: only the running segment's runs. */
    private fun EntryTiming.ended(): EntryTiming.Ended =
        checkNotNull(this as? EntryTiming.Ended) { "A new entry or an edit that runs" }

    /** Delete uses the entry as opened, never an unsaved field, and skips time validation. */
    fun delete(canRestore: Boolean, onDeleted: (TimeEntry, Boolean) -> Unit) {
        val form = (phase.value as? Phase.Open)?.form?.takeIf { !it.saving } ?: return
        if (form.target !is DraftTarget.Edit) return
        if (canRestore) {
            queueDelete(form, true, onDeleted)
        } else {
            phase.value = Phase.Open(form.copy(confirmDelete = true, deleteFailed = false))
        }
    }

    fun cancelDelete() = change { it.copy(confirmDelete = false) }

    fun confirmDelete(onDeleted: (TimeEntry, Boolean) -> Unit) {
        val form = (phase.value as? Phase.Open)?.form?.takeIf { it.confirmDelete && !it.saving } ?: return
        queueDelete(form, false, onDeleted)
    }

    private fun queueDelete(form: EntryDraft, undo: Boolean, onDeleted: (TimeEntry, Boolean) -> Unit) {
        val target = form.target as? DraftTarget.Edit ?: return
        val deleting = form.copy(saving = true, confirmDelete = false, deleteFailed = false)
        phase.value = Phase.Open(deleting)
        viewModelScope.launch {
            var outcome: LocalWrite<TimeEntry?>? = null
            try {
                outcome = localWrite { changes.delete(target.snapshot) }
                (outcome as? LocalWrite.Written)?.value?.let { onDeleted(it, undo) }
            } finally {
                val deleted = (outcome as? LocalWrite.Written)?.value != null
                val back = form.copy(saving = false, confirmDelete = false)
                // Nothing queued and a delete the storage refused both fail; one that threw gives the form back.
                phase.update { current ->
                    when {
                        current != Phase.Open(deleting) -> current
                        deleted -> Phase.Saved
                        outcome != null -> Phase.Open(back.copy(deleteFailed = true))
                        else -> Phase.Open(back)
                    }
                }
            }
        }
    }

    private fun open(requested: Opening) {
        val restored = restoring
        restoring = null
        // A route restored in a new process has no copy of an entry the cache lacks but the one the form saved.
        val opening = if (requested is Opening.Edit && requested.older == null) {
            requested.copy(older = restored?.older?.entry()?.takeIf { it.id == requested.entryId })
        } else {
            requested
        }
        val waiting = Phase.Waiting(opening, restored?.typed)
        phase.value = waiting
        viewModelScope.launch {
            val settings = accountSettings.filterNotNull().first()
            val next = when (val time = settings.time(clock)) {
                null -> Phase.NoAccountTime

                else -> when (opening) {
                    is Opening.New -> Phase.Open(newForm(time, opening.date, opening.noTimes))

                    is Opening.Running -> {
                        val entry = running.runningEntry(opening.identity)
                        entry?.let { Phase.Open(runningForm(it, opening.identity, settings, time)) } ?: Phase.Missing
                    }

                    is Opening.Edit -> {
                        val cached = entries.first().firstOrNull { it.entry.id == opening.entryId }
                        val form = when {
                            cached != null -> editForm(cached, settings, time, onPhone = true)

                            opening.older != null -> editForm(
                                EntryWithSendState(opening.older, unsent = false),
                                settings,
                                time,
                                onPhone = false,
                            )

                            else -> null
                        }
                        form?.let { Phase.Open(it) } ?: Phase.Missing
                    }
                }
            }
            if (phase.value != waiting) return@launch
            opened = (next as? Phase.Open)?.form
            openedOn = (opening as? Opening.Edit)?.older
            val typed = waiting.typed
            phase.value = if (next is Phase.Open && typed != null) Phase.Open(typed.over(next.form)) else next
        }
    }

    /** What the form keeps for a new process while it is [phase]. */
    private fun keptFor(phase: Phase): SavedEntryForm? = when (phase) {
        is Phase.Waiting -> SavedEntryForm((phase.opening as? Opening.Edit)?.older?.let(::SavedEntry), phase.typed)

        is Phase.Open -> SavedEntryForm(
            openedOn?.let(::SavedEntry),
            opened?.let { TypedDraft.of(it, phase.form) },
        )

        else -> null
    }

    private fun change(edit: (EntryDraft) -> EntryDraft) {
        phase.update { current ->
            if (current is Phase.Open && !current.form.saving) {
                Phase.Open(
                    edit(current.form),
                )
            } else {
                current
            }
        }
    }

    private fun changeWithTime(edit: (EntryDraft, AccountSettings, AccountTime) -> EntryDraft) {
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
                form.shown(names.projects, names.tasks, settings, time, allDay, clock)
            }
        }
    }

    private sealed interface Opening {
        data class Running(val identity: RunningEntryIdentity) : Opening

        data class New(val date: LocalDate?, val noTimes: Boolean = false) : Opening

        data class Edit(val entryId: EntryId, val older: TimeEntry?) : Opening
    }

    private sealed interface Phase {
        data object Closed : Phase

        /**
         * The form opens once the account settings and the cache are read, with what was [typed]
         * before the process died.
         */
        data class Waiting(val opening: Opening, val typed: TypedDraft?) : Phase

        data object Missing : Phase

        data object NoAccountTime : Phase

        data object Saved : Phase

        data class Open(val form: EntryDraft) : Phase
    }

    private data class Catalog(val projects: List<Project> = emptyList(), val tasks: List<Task> = emptyList())

    init {
        // Kept for a new process at each change, as the form's screen shows it.
        viewModelScope.launch { phase.collect { saved[SAVED_FORM] = keptFor(it)?.encode() } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** The key of the form's [SavedEntryForm] in its saved state. */
        const val SAVED_FORM = "entry-form"
    }
}
