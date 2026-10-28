package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.toCached
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.SessionActions
import com.github.e0ipso.tokkl.timerstate.SessionLengths
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Session-only inputs. Editing these never changes the account's defaults. */
data class PomodoroLengths(
    val work: String,
    val shortRest: String,
    val longRest: String,
    val every: String,
) {
    /**
     * The four lengths a session starts with, or null while one is not a whole number in the
     * range Tokkl accepts. Tokkl answers a length outside its range with 400, so the form checks first.
     */
    fun sessionLengths(): SessionLengths? {
        val work = work.toIntOrNull()?.takeIf { it in PomodoroDefaults.WORK_MINUTES } ?: return null
        val short = shortRest.toIntOrNull()?.takeIf { it in PomodoroDefaults.SHORT_REST_MINUTES } ?: return null
        val long = longRest.toIntOrNull()?.takeIf { it in PomodoroDefaults.LONG_REST_MINUTES } ?: return null
        val every = every.toIntOrNull()?.takeIf { it in PomodoroDefaults.LONG_REST_EVERY } ?: return null
        return SessionLengths(work, short, long, every)
    }

    companion object {
        fun from(defaults: PomodoroDefaults) = PomodoroLengths(
            defaults.workMinutes.toString(), defaults.shortRestMinutes.toString(),
            defaults.longRestMinutes.toString(), defaults.longRestEvery.toString(),
        )
    }
}

enum class PomodoroAction { START, NEXT, END }

/** The action that did not go through, and how it ended. [result] is never [TimerActionResult.Done]. */
data class PomodoroProblem(val action: PomodoroAction, val result: TimerActionResult)

/**
 * What the page drew its controls from: the server revision, the phase it showed and the open
 * pomodoro's entry. Next phase and End send [revision], and a field edit goes to [entryId], so a
 * tap on a page drawn before another device changed the timer gets the server's refusal and
 * cannot act on a phase or an entry the user never saw.
 */
data class PomodoroShown(val revision: Long, val phaseStart: Instant, val entryId: Long)

data class PomodoroPageState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val lengths: PomodoroLengths = PomodoroLengths.from(PomodoroDefaults.DEFAULT),
    /** The start form's entry fields while idle, and the open pomodoro's while a session runs. */
    val fields: EntryFields = EntryFields(),
    /** The server's timer state with the time the phone got it. */
    val held: HeldTimerState? = null,
    val account: AccountSettings? = null,
    val alarmDenied: Boolean = false,
    val fullScreenDenied: Boolean = false,
    val notificationsDenied: Boolean = false,
    val unsentCount: Int? = null,
    val refused: List<PendingChange> = emptyList(),
    val problem: PomodoroProblem? = null,
) {
    /** The server's session, or null without one. */
    val pomodoro: TimerPomodoro? get() = held?.state?.pomodoro

    val shown: PomodoroShown?
        get() {
            val state = held?.state ?: return null
            val session = state.pomodoro ?: return null
            return PomodoroShown(state.revision, session.phase.start, session.entryId)
        }

    /** The session's workspace while it runs, and the account's default for a new one. */
    val workspaceId: Long?
        get() = held?.state?.takeIf { it.pomodoro != null }?.runningEntry?.workspaceId ?: account?.defaultWorkspaceId

    val canStart: Boolean get() = !loading && !busy && pomodoro == null && workspaceId != null && lengths.sessionLengths() != null
}

/**
 * The Pomodoro page's view and controller of the server's session. The server owns the session:
 * this model shows the held timer state and sends Start, Next phase and End straight to it
 * through [actions], and applies no phase rule of its own. Field edits of the open pomodoro go
 * to the running entry through [changes], the queue every running-entry edit uses, and the
 * server carries them to the later pomodoros.
 *
 * Next phase and End send the revision the page showed. When the user's own edits are still on
 * their way, they first go to the server and the model reads the revision they produced, because
 * every edit of the running entry moves it. It sends the new revision only while the phase is the
 * one the page showed, and otherwise tells the user another device changed the timer.
 *
 * All public methods are called on the main thread, as for the other screen models.
 *
 * @param held the account's held timer state, null before the first one.
 * @param controls what the shared timer controls show: the cache's running entry, with queued edits.
 * @param sendQueued sends the change queue now and waits for it.
 * @param alarmProbe whether exact alarms are refused, until the page reports the grants itself.
 */
class PomodoroScreenModel(
    held: Flow<HeldTimerState?>,
    controls: Flow<TimerControls>,
    private val actions: SessionActions,
    private val changes: EntryChanges,
    private val defaults: StateFlow<PomodoroDefaults>,
    settings: StateFlow<AccountSettings?>,
    unsent: Flow<Int>,
    refused: Flow<List<PendingChange>>,
    scope: CoroutineScope,
    private val sendQueued: suspend () -> Unit = {},
    private val alarmProbe: () -> Boolean = { false },
) {
    private val modelScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val mutableState = MutableStateFlow(
        PomodoroPageState(lengths = PomodoroLengths.from(defaults.value), account = settings.value),
    )
    val state: StateFlow<PomodoroPageState> = mutableState.asStateFlow()

    /** Commands to the server run one at a time, in tap order. */
    private val commands = Mutex()
    private var lengthsEdited = false
    private var exactAlarmAllowed: Boolean? = null

    private var heldNow: HeldTimerState? = null
    private var heldRead = false
    private var controlsRead = false

    /** The cache's running entry, which carries queued edits the server does not have yet. */
    private val cached = MutableStateFlow<CachedTimeEntry?>(null)

    /** The start form's fields. They follow the open pomodoro, so the form keeps them after End. */
    private var draft = EntryFields()
    private var edit: Edit? = null
    private val fieldWrites = mutableSetOf<Job>()

    /** Whether an edit reached the queue that the server may not have yet. */
    private var unflushed = false

    init {
        modelScope.launch { held.collect { value -> accept(value) } }
        modelScope.launch {
            controls.collect { value ->
                cached.value = (value as? TimerControls.Running)?.entry
                controlsRead = true
                publish()
            }
        }
        modelScope.launch { defaults.collect { value ->
            if (!lengthsEdited) mutableState.update { it.copy(lengths = PomodoroLengths.from(value)) }
        } }
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
            it.copy(notificationsDenied = !grants.notifications, alarmDenied = !grants.exactAlarm, fullScreenDenied = !grants.fullScreen)
        }
    }

    private fun alarmDenied(): Boolean = exactAlarmAllowed?.not() ?: alarmProbe()

    private fun accept(value: HeldTimerState?) {
        val ended = heldNow?.state?.pomodoro != null && value?.state?.pomodoro == null
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
        val pomodoro = heldNow?.state?.pomodoro
        val shown = if (pomodoro == null) draft else openFields(pomodoro) ?: draft
        if (pomodoro != null) draft = shown
        val loading = !(heldRead && controlsRead)
        mutableState.update {
            it.copy(
                loading = loading, held = heldNow, fields = shown,
                alarmDenied = if (it.loading && !loading) alarmDenied() else it.alarmDenied,
            )
        }
    }

    /** The open pomodoro's fields: a typed edit, else the cache's running entry, else the server's. */
    private fun openFields(pomodoro: TimerPomodoro): EntryFields? {
        edit?.takeIf { it.entryId == pomodoro.entryId }?.let { return it.fields }
        return openEntry(pomodoro)?.fields()
    }

    /** The open pomodoro's entry as the cache holds it, else as the server's state does. */
    private fun openEntry(pomodoro: TimerPomodoro): CachedTimeEntry? =
        cached.value?.takeIf { it.id == pomodoro.entryId }
            ?: heldNow?.state?.runningEntry?.takeIf { it.id == pomodoro.entryId }?.toCached()

    fun editLengths(lengths: PomodoroLengths) {
        if (state.value.loading || state.value.busy || state.value.pomodoro != null) return
        lengthsEdited = true
        mutableState.update { it.copy(lengths = lengths) }
    }

    /**
     * Changes the start form's fields, or the open pomodoro's. [entryId] is the pomodoro the page
     * drew its fields for, or null for the start form: a callback from a page drawn before the
     * session started, ended or moved to its next pomodoro changes nothing. With [send] false, as
     * for each letter of the description, the page shows the fields until [fieldsDone]; otherwise
     * they go to the queue now. Returns the write, or null when nothing was queued.
     */
    fun editFields(
        fields: EntryFields,
        entryId: Long? = state.value.pomodoro?.entryId,
        send: Boolean = true,
    ): Job? {
        val current = state.value
        if (current.loading || current.busy || current.workspaceId == null) return null
        val pomodoro = current.pomodoro
        if (entryId != pomodoro?.entryId) return null
        if (pomodoro == null) {
            draft = fields
            publish()
            return null
        }
        val change = Edit(pomodoro.entryId, fields, sent = false)
        edit = change
        publish()
        return if (send) write(change) else null
    }

    /** Sends a description typed for the open pomodoro when it differs from the cache's. */
    fun fieldsDone(entryId: Long? = state.value.pomodoro?.entryId): Job? {
        val pomodoro = state.value.pomodoro ?: return null
        val typed = edit?.takeIf { it.entryId == entryId && it.entryId == pomodoro.entryId && !it.sent } ?: return null
        if (typed.fields == openEntry(pomodoro)?.fields()) {
            edit = null
            publish()
            return null
        }
        return write(typed)
    }

    /** Queues [change] for the running entry with the identity the cache holds it under. */
    private fun write(change: Edit): Job? {
        val pomodoro = state.value.pomodoro
        val entry = pomodoro?.let(::openEntry)?.takeIf { it.id == change.entryId }
        if (entry == null) {
            edit = null
            publish()
            return null
        }
        val before = entry.fields()
        val sent = change.copy(sent = true)
        edit = sent
        publish()
        val write = modelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val identity = RunningEntryIdentity(entry.id, entry.workspaceId, entry.start)
                if (changes.updateRunning(identity, change.fields, entry.start)) {
                    unflushed = true
                    // Keeps the typed fields on screen until the cache shows them.
                    withTimeoutOrNull(SETTLE_MS) {
                        cached.first { row ->
                            row == null || row.id != entry.id || row.fields().let { it == change.fields || it != before }
                        }
                    }
                }
            } finally {
                if (edit == sent) {
                    edit = null
                    publish()
                }
            }
        }
        fieldWrites += write
        write.invokeOnCompletion { fieldWrites -= write }
        return write
    }

    /**
     * Starts a session with the form's fields and lengths. Nothing starts on the phone: the
     * server answers with the session, which the page then shows. [onStarted] runs only once the
     * server has started it, to ask for the notification permission.
     */
    fun start(onStarted: () -> Unit = {}): Job? {
        val before = state.value
        if (!before.canStart) return null
        val lengths = before.lengths.sessionLengths() ?: return null
        val workspace = before.workspaceId ?: return null
        return command(PomodoroAction.START) {
            actions.startSession(workspace, before.fields, lengths).also { if (it == TimerActionResult.Done) onStarted() }
        }
    }

    fun next(shown: PomodoroShown? = state.value.shown): Job? = finish(PomodoroAction.NEXT, shown, actions::nextPhase)

    fun end(shown: PomodoroShown? = state.value.shown): Job? = finish(PomodoroAction.END, shown, actions::endSession)

    private fun finish(action: PomodoroAction, shown: PomodoroShown?, send: suspend (Long) -> TimerActionResult): Job? {
        val current = state.value
        if (shown == null || current.loading || current.busy) return null
        return command(action) {
            val revision = revisionToSend(shown)
            if (revision == null) TimerActionResult.Refused else send(revision)
        }
    }

    /**
     * The revision Next phase or End sends: the one the page showed, or after the user's own edits
     * the one the server gave them, which they moved. Null when the session left the phase the
     * page showed meanwhile. Sending the edits first keeps them on the pomodoro this action ends,
     * and lets Next phase carry them to the next.
     */
    private suspend fun revisionToSend(shown: PomodoroShown): Long? {
        fieldWrites.toList().joinAll()
        if (!unflushed) return shown.revision
        unflushed = false
        sendQueued()
        actions.read()
        val moved = withTimeoutOrNull(REFRESH_MS) {
            state.first { it.shown.let { now -> now == null || now.revision > shown.revision } }
        }
        val latest = (moved ?: state.value).shown ?: return null
        return if (latest.phaseStart == shown.phaseStart) latest.revision else null
    }

    private fun command(action: PomodoroAction, send: suspend () -> TimerActionResult): Job {
        // Synchronous guard prevents a second tap while the first is waiting for the server.
        mutableState.update { it.copy(busy = true, problem = null) }
        return modelScope.launch {
            commands.withLock {
                try {
                    val result = send()
                    mutableState.update { it.copy(problem = if (result == TimerActionResult.Done) null else PomodoroProblem(action, result)) }
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    mutableState.update { it.copy(busy = false, alarmDenied = alarmDenied()) }
                }
            }
        }
    }

    /** Fields typed for the open pomodoro [entryId], shown until the cache has them. */
    private data class Edit(val entryId: Long, val fields: EntryFields, val sent: Boolean)

    private companion object {
        /** How long typed fields stay on screen waiting for the cache to show them. */
        const val SETTLE_MS = 5_000L

        /** How long Next phase waits for the revision the user's own edits produced. */
        const val REFRESH_MS = 3_000L
    }
}
