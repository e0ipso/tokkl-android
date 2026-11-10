package com.github.e0ipso.tokkl.pomodoro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.cache.PendingEdit
import com.github.e0ipso.tokkl.cache.afterWrite
import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.PomodoroSession
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The server owns the session: this view model applies no phase rule of its own, and a refused
 * Next phase or End, as after another device changed the timer, says so.
 *
 * All public methods are called on the main thread, as for the other screens' view models. The
 * start form's draft and the typed fields outlive a rotation, until the account signs out, and the
 * route's saved state keeps the start form's fields and typed lengths for a new process that
 * restores the page.
 *
 * @param held the account's held timer state, null before the first one.
 * @param controls what the shared timer controls show: the cache's running entry, with queued edits.
 * @param exactAlarms whether exact alarms are refused, until the page reports the grants itself.
 * @param picker the cached projects, clients, tasks and tags, which the page's picker offers and
 *   its project and task are named from.
 * @param saved the route's saved state, which keeps the start form ([SavedPomodoroForm]).
 */
class PomodoroViewModel(
    held: Flow<HeldTimerState?>,
    controls: Flow<TimerControls>,
    private val session: PomodoroSessionEdits,
    private val defaults: StateFlow<PomodoroDefaults>,
    settings: StateFlow<AccountSettings?>,
    unsent: Flow<Int>,
    refused: Flow<List<HeldChange>>,
    scope: CoroutineScope,
    private val exactAlarms: ExactAlarmAccess,
    picker: Flow<PickerSource>,
    private val offline: StateFlow<Boolean>,
    private val saved: SavedStateHandle,
) : ViewModel(scope) {
    private val modelScope = CoroutineScope(
        viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]),
    )

    private val restored = saved.get<String>(SAVED_FORM)?.let(SavedPomodoroForm::decode)

    private val mutableState = MutableStateFlow(
        PomodoroPageState(
            lengths = restored?.lengths() ?: PomodoroLengths.from(defaults.value),
            account = settings.value,
        ),
    )

    private val pickerShown: StateFlow<PickerSource> = picker.stateIn(viewModelScope, whileShown, PickerSource())

    /** The page's state, before the picker and the network join it in [page]. */
    internal val state: StateFlow<PomodoroPageState> = mutableState.asStateFlow()

    /** [state] with the picker's rows, which name its fields' project and task, and the network. */
    val page: StateFlow<PomodoroPageState> = combine(state, pickerShown, offline) { shown, source, isOffline ->
        shown.copy(
            labels = entryLabels(shown.fields, source.projects, source.tasks),
            picker = source,
            offline = isOffline,
        )
    }.stateIn(viewModelScope, whileShown, state.value)

    /** Commands to the server run one at a time, in tap order. */
    private val commands = Mutex()
    private var lengthsEdited = restored?.lengths() != null
    private var exactAlarmAllowed: Boolean? = null

    private var heldNow: HeldTimerState? = null
    private var heldRead = false
    private var controlsRead = false

    /** The cache's running entry, which carries queued edits the server does not have yet. */
    private val cached = MutableStateFlow<TimeEntry?>(null)

    /** The start form's fields. They follow the open pomodoro, so the form keeps them after End. */
    private var draft = restored?.fields() ?: EntryFields()
    private var edit: PendingEdit<EntryId>? = null

    init {
        modelScope.launch { held.collect { value -> accept(value) } }
        modelScope.launch {
            controls.collect { value ->
                cached.value = (value as? TimerControls.Running)?.entry
                controlsRead = true
                publish()
            }
        }
        modelScope.launch {
            defaults.collect { value ->
                if (!lengthsEdited) mutableState.update { it.copy(lengths = PomodoroLengths.from(value)) }
            }
        }
        modelScope.launch { settings.collect { account -> mutableState.update { it.copy(account = account) } } }
        modelScope.launch { unsent.collect { count -> mutableState.update { it.copy(unsentCount = count) } } }
        modelScope.launch { refused.collect { list -> mutableState.update { it.copy(refused = list) } } }
    }

    /** Refreshed on resume after returning from full-screen special app access settings. */
    fun fullScreenPermissionChanged(allowed: Boolean) {
        mutableState.update { it.copy(fullScreenDenied = !allowed) }
    }

    /** Permission changes update notices only; a late grant never restarts a session. */
    internal fun permissionsChanged(grants: PomodoroPermissions) {
        exactAlarmAllowed = grants.exactAlarm
        mutableState.update {
            it.copy(
                notificationsDenied = !grants.notifications,
                alarmDenied = !grants.exactAlarm,
                fullScreenDenied = !grants.fullScreen,
            )
        }
    }

    private fun alarmDenied(): Boolean = exactAlarmAllowed?.not() ?: exactAlarms.denied()

    private fun accept(value: HeldTimerState?) {
        val ended = heldNow?.pomodoro != null && value?.pomodoro == null
        heldNow = value
        heldRead = true
        if (ended) {
            // The next session starts from the account's defaults again, and an edit that was
            // still on its way belongs to the pomodoro that ended.
            lengthsEdited = false
            edit = null
            mutableState.update { it.copy(lengths = PomodoroLengths.from(defaults.value)) }
        }
        publish()
    }

    /** Puts the held state and the fields to show into [state], once both inputs have been read. */
    private fun publish() {
        val pomodoro = heldNow?.pomodoro
        val shown = if (pomodoro == null) draft else openFields(pomodoro) ?: draft
        if (pomodoro != null) draft = shown
        val loading = !(heldRead && controlsRead)
        mutableState.update {
            it.copy(
                loading = loading,
                held = heldNow,
                fields = shown,
                alarmDenied = if (it.loading && !loading) alarmDenied() else it.alarmDenied,
            )
        }
        keepForm()
    }

    private fun keepForm() {
        val lengths = state.value.lengths.takeIf { lengthsEdited }
        saved[SAVED_FORM] = SavedPomodoroForm.of(draft, lengths).encode()
    }

    /** The open pomodoro's fields: a typed edit, else the cache's running entry, else the server's. */
    private fun openFields(pomodoro: PomodoroSession): EntryFields? {
        edit?.takeIf { it.target == pomodoro.entryId }?.let { return it.fields }
        return openEntry(pomodoro)?.fields()
    }

    /** The open pomodoro's entry as the cache holds it, else as the server's state does. */
    private fun openEntry(pomodoro: PomodoroSession): TimeEntry? = cached.value?.takeIf { it.id == pomodoro.entryId }
        ?: heldNow?.runningEntry?.takeIf { it.id == pomodoro.entryId }

    fun editLengths(lengths: PomodoroLengths) {
        if (state.value.loading || state.value.busy || state.value.pomodoro != null) return
        lengthsEdited = true
        mutableState.update { it.copy(lengths = lengths) }
        keepForm()
    }

    /**
     * Changes the start form's fields, or the open pomodoro's. [entryId] is the pomodoro the page
     * drew its fields for, or null for the start form: a callback from a page drawn before the
     * session started, ended or moved to its next pomodoro changes nothing. With [send] false, as
     * for each letter of the description, the page shows the fields until [fieldsDone]; otherwise
     * they go to the queue now. Returns the write, or null when nothing was queued.
     */
    fun editFields(fields: EntryFields, entryId: EntryId? = state.value.pomodoro?.entryId, send: Boolean = true): Job? {
        val current = state.value
        if (current.loading || current.busy || current.workspaceId == null) return null
        val pomodoro = current.pomodoro
        if (entryId != pomodoro?.entryId) return null
        if (pomodoro == null) {
            draft = fields
            publish()
            return null
        }
        val change = PendingEdit(pomodoro.entryId, fields, sent = false)
        edit = change
        publish()
        return if (send) write(change) else null
    }

    /** Picking a billable project makes the fields billable. */
    fun pick(fields: EntryFields, picked: Picked, entryId: EntryId?): Job? =
        editFields(fields.withPicked(picked, pickerShown.value.projects), entryId)

    /** Sends a description typed for the open pomodoro when it differs from the cache's. */
    fun fieldsDone(entryId: EntryId? = state.value.pomodoro?.entryId): Job? {
        val pomodoro = state.value.pomodoro ?: return null
        val typed =
            edit?.takeIf { it.target == entryId && it.target == pomodoro.entryId && !it.sent } ?: return null
        if (typed.fields == openEntry(pomodoro)?.fields()) {
            edit = null
            publish()
            return null
        }
        return write(typed)
    }

    /**
     * Queues [change] for the open pomodoro, and shows it until the cache does. A write the phone's
     * storage refuses keeps the typed fields on screen, unsent, so the next [fieldsDone] sends them
     * again, as the entry form gives a refused save back as typed.
     */
    private fun write(change: PendingEdit<EntryId>): Job? {
        val pomodoro = state.value.pomodoro
        val entry = pomodoro?.let(::openEntry)?.takeIf { it.id == change.target }
        if (entry == null) {
            edit = null
            publish()
            return null
        }
        val sent = change.copy(sent = true)
        edit = sent
        publish()
        return modelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var refused = false
            try {
                refused = session.editOpenPomodoro(entry, change.fields, cached)
            } finally {
                edit = edit.afterWrite(sent, refused)
                publish()
            }
        }
    }

    /**
     * Nothing starts on the phone: the server answers with the session, which the page then shows.
     * [onStarted] runs only once the server has started it, to ask for the notification permission.
     */
    fun start(onStarted: () -> Unit = {}): Job? {
        val before = state.value
        if (!before.canStart) return null
        val lengths = before.lengths.sessionLengths() ?: return null
        val workspace = before.workspaceId ?: return null
        return command(PomodoroAction.START) {
            session.start(workspace, before.fields, lengths).also { if (it == TimerActionResult.Done) onStarted() }
        }
    }

    /**
     * Confirms the phase [shown], which the page drew, and starts the next one. The user's own
     * edits reach the server first; a phase that moved meanwhile ends in a refusal.
     */
    fun next(shown: PomodoroShown? = state.value.shown): Job? = finish(PomodoroAction.NEXT, shown, session::next)

    /** Ends the session [shown], which the page drew, after the user's own edits. */
    fun end(shown: PomodoroShown? = state.value.shown): Job? = finish(PomodoroAction.END, shown, session::end)

    private fun finish(
        action: PomodoroAction,
        shown: PomodoroShown?,
        send: suspend (PomodoroShown) -> TimerActionResult,
    ): Job? {
        val current = state.value
        if (shown == null || current.loading || current.busy) return null
        return command(action) { send(shown) }
    }

    private fun command(action: PomodoroAction, send: suspend () -> TimerActionResult): Job {
        // Synchronous guard prevents a second tap while the first is waiting for the server.
        mutableState.update { it.copy(busy = true, problem = null) }
        return modelScope.launch {
            commands.withLock {
                try {
                    val result = send()
                    mutableState.update {
                        it.copy(
                            problem = if (result == TimerActionResult.Done) null else PomodoroProblem(action, result),
                        )
                    }
                } finally {
                    mutableState.update { it.copy(busy = false, alarmDenied = alarmDenied()) }
                }
            }
        }
    }

    private companion object {
        const val SAVED_FORM = "pomodoro-form"
    }
}
