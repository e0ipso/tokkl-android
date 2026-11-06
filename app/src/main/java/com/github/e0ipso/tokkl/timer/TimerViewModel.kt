package com.github.e0ipso.tokkl.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.api.DiscardedPomodoro
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.entry.withPicked
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.target
import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The timer on the Timer page (docs/PRD.md T1): Start, Pause, Resume, Stop and the running entry's
 * fields. Start, Stop of a running entry, Continue, favorites and edits of the running entry go to
 * [changes], the change queue, which changes the cache on the phone and queues the change for the
 * server, so they never wait for the network (docs/PRD.md principle 2). The timer shows what the
 * [controls] say, and from such a tap until the cache shows it, what the tap did. Taps in between
 * are ignored, so a double tap starts one entry.
 *
 * Pause, Resume and Stop while paused belong to the server's pause chain and go to [pauseChain]
 * at once. They wait for the server's answer, and until it shows, the timer keeps what it showed
 * and ignores other taps. Resume and Stop while paused send the server revision the paused timer
 * was shown at, so a stale tap gets a refusal. A refusal or no answer leaves the timer as the
 * server's state says and becomes a [notice]. Without [pauseChain] they do nothing.
 *
 * Start starts the draft that the idle timer holds, and [continueEntry] starts a timer from a past
 * entry. The cache keeps one running entry, and a start on the phone stops a cached running entry
 * first, as the server does, so one entry runs at a time, offline too. Any start other than
 * Resume ends the server's chain once the server has it.
 *
 * A pick of project, task or tags, and billable, goes to the queue at once. A description goes
 * once typing ends, with [descriptionDone], Pause or Stop, so the queue does not get a change for
 * every letter. Until the cache has an edit, the timer shows it over the cache's entry, so a
 * refresh cannot take back what is being typed. A paused timer's fields are read-only, as the
 * server and the web app keep them.
 *
 * Call the tap handlers on the main thread. The running clock is the screen's: it counts from
 * [TimerState.Running.start] on the phone and never asks the server (principle 3).
 *
 * @param controls the running entry or paused chain, as the app's shared timer controls give them.
 * @param accountSettings for the default workspace, where Start starts, and for the Today and
 *   This week totals.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 * @param trace marks each Start and Stop tap until the screen reports with [shown] that it drew
 *   what the tap did.
 * @param pauseChain the server's Pause, Resume and Discard, or null where the app sends none.
 * @param sendQueued sends the queue now and waits for it, so a description typed before Pause
 *   reaches the server before the Pause does.
 * @param discardNotices the too-short pomodoro whose notice no screen has shown, from the
 *   account's timer state.
 * @param takeDiscardNotice records that a screen shows that notice, and returns it, or null when
 *   none is left to show.
 * @param timerApi whether the server offers the timer API, which decides whether a timer that
 *   the cache does not show yet offers Pause.
 */
class TimerViewModel(
    controls: Flow<TimerControls>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val trace: TapTrace = TapTrace.None,
    private val pauseChain: PauseActions? = null,
    private val sendQueued: suspend () -> Unit = {},
    discardNotices: Flow<DiscardedPomodoro?> = flowOf(null),
    takeDiscardNotice: suspend () -> DiscardedPomodoro? = { null },
    timerApi: Flow<Boolean> = flowOf(true),
) : ViewModel(scope) {

    /** The trace section of the last Start or Stop tap, until the screen shows what it did. */
    private var openTap: OpenTap? = null

    private val fieldWrites = mutableSetOf<Job>()
    private var openingRunning = false

    private val local = MutableStateFlow(TimerTaps())

    private val cached: StateFlow<CachedControls> = controls
        .map<TimerControls, CachedControls> { entry ->
            val editId = local.value.edit?.entryId
            CachedControls.Read(entry, editId, editId?.let { changes.currentId(it) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CachedControls.Unread)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    private val queuedStarts = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One item for each timer start made here, Start, Resume, Continue or a favorite, once the
     * queue or the server has it, for the notification permission's question (docs/PRD.md N5).
     * The start never waits for a collector. The latest start waits for a screen collector after
     * a configuration change.
     */
    val starts: SharedFlow<Unit> = queuedStarts.asSharedFlow()

    private val pauseOffered: StateFlow<Boolean> = timerApi.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val state: StateFlow<TimerState> = combine(cached, catalog, accountSettings, local, pauseOffered) { running, names, settings, taps, offered ->
        timerState(running, settings?.defaultWorkspaceId, taps, offered).labelled(names.projects, names.tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerState.Loading)

    private val notices = TimerNoticeBoard(takeDiscardNotice)

    /**
     * What the timer's screens should say now, or null: a claimed notice until it has shown, then
     * the latest failed pause chain action, then a too-short pomodoro no screen has shown yet. A
     * screen [claim]s a notice before it shows it and calls [noticeShown] after.
     */
    val notice: StateFlow<TimerNotice?> = notices.notices(discardNotices)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Claims [notice] for the screen about to show it, and returns what to show, or null when
     * there is nothing left to show. A too-short pomodoro is recorded as shown here, once, so a
     * replay of its state or a restart does not show it again.
     */
    suspend fun claim(notice: TimerNotice): TimerNotice? = notices.claim(notice)

    /** The screen has shown [notice], or stopped showing it. */
    fun noticeShown(notice: TimerNotice) = notices.shown(notice)

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Idle ?: return
        val workspaceId = shown.workspaceId ?: return
        beginTap(TapTrace.START) { it is TimerState.Running }
        startWith(workspaceId, shown.fields, typed = null)
    }

    /**
     * Tells the view model that the screen has drawn [state], which ends the trace section of a Start
     * or Stop tap once [state] shows what the tap did. Call it on the main thread.
     */
    fun shown(state: TimerState) {
        val tap = openTap ?: return
        if (tap.done(state)) {
            openTap = null
            tap.end()
        }
    }

    private fun beginTap(section: String, done: (TimerState) -> Boolean) {
        // A screen that stopped showing the timer never reported the last tap's state.
        openTap?.end()
        openTap = OpenTap(trace.begin(section), done)
    }

    /**
     * Continues [source] (docs/PRD.md T2): starts a timer with its description, project, task,
     * tags and billable, in its workspace, as the web app's Continue does. A running entry stops
     * at the new one's start, with a description still being typed for it. Ignored while the
     * cache is unread, and while a tap the cache or the server does not show yet is pending, so a
     * double tap starts one entry.
     */
    fun continueEntry(source: CachedTimeEntry) {
        if (local.value.pending != null) return
        val typed = when (val shown = snapshot()) {
            TimerState.Loading -> return
            is TimerState.Idle, is TimerState.Paused -> null
            is TimerState.Running -> typedFor(shown)
        }
        startWith(source.workspaceId, source.fields(), typed)
    }

    /**
     * Starts a timer with [fields] at once, as a favorite does (docs/PRD.md T5), in the account's
     * default workspace, through the same start as Continue: a running entry stops at the new
     * one's start, with a description still being typed for it, as the server does for the web
     * app's favorites (TimerBar.tsx in ../tokkl/packages/web). Does nothing until a workspace is
     * known, or while a tap is pending.
     */
    fun startFrom(fields: EntryFields) {
        if (local.value.pending != null) return
        when (val shown = snapshot()) {
            TimerState.Loading -> Unit
            is TimerState.Idle -> shown.workspaceId?.let { startWith(it, fields, typed = null) }
            is TimerState.Running -> startWith(accountSettings.value?.defaultWorkspaceId ?: shown.workspaceId, fields, typedFor(shown))
            is TimerState.Paused -> startWith(accountSettings.value?.defaultWorkspaceId ?: shown.workspaceId, fields, typed = null)
        }
    }

    /** The description still being typed for the running entry [shown], not queued yet. */
    private fun typedFor(shown: TimerState.Running): TypedEdit? =
        editFor(shown)?.takeIf { !it.sent }?.let { edit ->
            shown.entryId?.let { TypedEdit(RunningEntryIdentity(it, shown.workspaceId, shown.start), edit) }
        }

    /**
     * Starts a timer with [fields] in [workspaceId], after queuing the [typed] description of the
     * running entry, which the start then stops. The idle timer's draft makes way for the new entry,
     * and comes back when nothing starts.
     */
    private fun startWith(workspaceId: Long, fields: EntryFields, typed: TypedEdit?) {
        val draft = local.value.draft
        val starting = PendingTap.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), fields, workspaceId)
        local.update { it.copy(pending = starting, draft = EntryFields(), edit = if (typed != null) null else it.edit) }
        // Undispatched, so the queue reads the tap time within the tap's handler.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var queued = false
            try {
                if (typed != null) changes.updateRunning(typed.identity, typed.edit.fields, typed.identity.start)
                starting.entryId.complete(changes.start(workspaceId, starting.fields))
                queued = true
                queuedStarts.tryEmit(Unit)
                settle { it.runningEntry()?.start?.isBefore(starting.at) == false }
            } finally {
                starting.entryId.complete(null)
                local.update { taps ->
                    when {
                        taps.pending != starting -> taps
                        // Nothing started, so the draft comes back.
                        !queued && taps.draft == EntryFields() -> taps.copy(pending = null, draft = draft)
                        else -> taps.copy(pending = null)
                    }
                }
            }
        }
    }

    /**
     * Stops the running entry, with a description still being typed, through the queue. A
     * running clone stops in the same way, and the server ends its chain. While paused, ends the
     * server's chain with Discard.
     */
    fun stop() {
        if (local.value.pending != null) return
        when (val shown = snapshot()) {
            is TimerState.Running -> stopRunning(shown)
            is TimerState.Paused -> discardPaused()
            else -> Unit
        }
    }

    private fun stopRunning(shown: TimerState.Running) {
        val entryId = shown.entryId ?: return
        val edited = editFor(shown)?.fields
        beginTap(TapTrace.STOP) { it is TimerState.Idle }
        val stopping = PendingTap.Stopping(shown.start)
        local.update { it.copy(pending = stopping, edit = null) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.stop(entryId, edited)) settle { it.runningEntry()?.start != shown.start }
            } finally {
                local.update { if (it.pending == stopping) it.copy(pending = null) else it }
            }
        }
    }

    /** Ends the paused chain on the server, at the revision it was shown at. */
    private fun discardPaused() {
        val actions = pauseChain ?: return
        val paused = cachedControls() as? TimerControls.Paused ?: return
        beginTap(TapTrace.STOP) { it is TimerState.Idle }
        act(paused.target, done = { it.target != paused.target }) { actions.discard(paused.target.revision) }
    }

    /**
     * Pauses the running entry on the server (docs/PRD.md T1). A description still being typed is
     * queued and sent first, so the chain keeps it. The timer shows what it showed until the
     * server's paused state does.
     */
    fun pause() {
        val actions = pauseChain ?: return
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Running ?: return
        val running = cachedControls() as? TimerControls.Running ?: return
        if (!running.pausable || shown.entryId != running.entry.id) return
        val typed = editFor(shown)?.takeIf { !it.sent }
        if (typed != null) send(shown, typed.fields)
        act(running.target, done = { it.target != running.target }) {
            if (typed != null) {
                while (fieldWrites.isNotEmpty()) fieldWrites.toList().joinAll()
                sendQueued()
            }
            actions.pause()
        }
    }

    /**
     * Resumes the paused chain on the server (docs/PRD.md T1), at the revision it was shown at:
     * the server starts a clone with the fields kept at Pause, and the clock goes on from the
     * chain's seconds. The paused view stays until the server's clone shows.
     */
    fun resume() {
        val actions = pauseChain ?: return
        if (local.value.pending != null) return
        if (snapshot() !is TimerState.Paused) return
        val paused = cachedControls() as? TimerControls.Paused ?: return
        act(paused.target, done = { it.target != paused.target }, onDone = { queuedStarts.tryEmit(Unit) }) {
            actions.resume(paused.target.revision)
        }
    }

    /**
     * Sends a pause chain action for what [target] showed, ignoring other taps until the server
     * answers and its state shows, as [done] tells. A refusal or no answer becomes a [notice].
     */
    private fun act(target: TimerTarget, done: (TimerControls) -> Boolean, onDone: () -> Unit = {}, send: suspend () -> TimerActionResult) {
        val acting = PendingTap.Acting(target)
        local.update { it.copy(pending = acting) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = send()
                if (result == TimerActionResult.Done) {
                    onDone()
                    settle(done)
                } else {
                    notices.failed(result)
                }
            } finally {
                local.update { if (it.pending == acting) it.copy(pending = null) else it }
            }
        }
    }

    /** A focused control keeps the durable target that supplied its displayed context. */
    fun isShown(target: TimerTarget): Boolean = local.value.pending == null && cachedControls()?.target == target

    fun pauseShown(target: TimerTarget) { if (isShown(target)) pause() }
    fun resumeShown(target: TimerTarget) { if (isShown(target)) resume() }
    fun stopShown(target: TimerTarget) {
        if (!isShown(target)) return
        when (val shown = snapshot()) {
            is TimerState.Running -> stopRunning(shown)
            is TimerState.Paused -> discardPaused()
            else -> Unit
        }
    }

    /** Reuse the editor's local-write settlement and identity/remap checks. */
    suspend fun runningForEditShown(target: TimerTarget): RunningEntryIdentity? =
        if (isShown(target)) runningForEdit() else null

    /** Shows [description] as typed. It goes to the queue with [descriptionDone], a pick, Pause or Stop. */
    fun editDescription(description: String) = edit(send = false) { it.copy(description = description) }

    /** Sends a description typed for the running entry when it differs from the cache's. */
    fun descriptionDone() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Running ?: return
        val edit = editFor(shown)?.takeIf { !it.sent } ?: return
        if (edit.fields == cachedRunning()?.fields()) {
            local.update { if (it.edit == edit) it.copy(edit = null) else it }
        } else {
            send(shown, edit.fields)
        }
    }

    /**
     * Settles the description and initial Start before opening the shared editor. The caller
     * clears field focus first, so the text field has handed over its latest value. Waiting is
     * for local writes only. Keep the captured id through remapping and never select a replacement
     * just because it has the same start time. Cancellation, including a disposed navigation host,
     * opens nothing. Nothing opens while a Stop or a pause chain action is on its way.
     */
    suspend fun runningForEdit(): RunningEntryIdentity? {
        if (openingRunning) return null
        val shown = snapshot() as? TimerState.Running ?: return null
        val pending = local.value.pending
        if (pending is PendingTap.Stopping || pending is PendingTap.Acting) return null
        openingRunning = true
        try {
            val id = if (pending is PendingTap.Starting) {
                val startedId = pending.entryId.await() ?: return null
                local.first { it.pending != pending }
                startedId
            } else shown.entryId ?: return null
            descriptionDone()
            while (fieldWrites.isNotEmpty()) fieldWrites.toList().joinAll()
            val settled = snapshot() as? TimerState.Running ?: return null
            val identity = RunningEntryIdentity(id, shown.workspaceId, if (pending is PendingTap.Starting) settled.start else shown.start)
            return identity.takeIf { changes.runningEntry(it) != null }
        } finally {
            openingRunning = false
        }
    }

    /** Sets the project, task and tags, as [EntryFields.withPicked] says. */
    fun pick(picked: Picked) = edit(send = true) { it.withPicked(picked, catalog.value.projects) }

    fun setBillable(billable: Boolean) = edit(send = true) { it.copy(billable = billable) }

    /**
     * Sets every field to [fields], as a picked description suggestion does: the web app's
     * pickSuggestion in TimerBar.tsx (../tokkl/packages/web) fills the description, project, task,
     * tags and billable, on the idle timer's draft or the running entry.
     */
    fun fill(fields: EntryFields) = edit(send = true) { fields }

    /**
     * Changes the idle timer's draft or the running entry's fields. While a tap the cache or the
     * server does not show yet is pending, the running entry's fields stay as they are: its id is
     * not known yet, or it is stopping. A paused timer's fields are read-only.
     */
    private fun edit(send: Boolean, change: (EntryFields) -> EntryFields) {
        when (val shown = snapshot()) {
            TimerState.Loading, is TimerState.Paused -> Unit
            is TimerState.Idle -> local.update { it.copy(draft = change(it.draft)) }
            is TimerState.Running -> {
                if (local.value.pending != null || shown.entryId == null) return
                val fields = change(shown.fields)
                when {
                    fields == shown.fields -> Unit
                    send -> send(shown, fields)
                    else -> local.update { it.copy(edit = FieldEdit(shown.entryId, shown.workspaceId, shown.start, fields, sent = false)) }
                }
            }
        }
    }

    /**
     * Queues [fields] for the running entry, and shows them until the cache has them, or has
     * anything newer than what it had before.
     */
    private fun send(shown: TimerState.Running, fields: EntryFields) {
        val entryId = shown.entryId ?: return
        val before = cachedRunning()?.fields()
        val edit = FieldEdit(entryId, shown.workspaceId, shown.start, fields, sent = true)
        local.update { it.copy(edit = edit) }
        val write = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.updateRunning(RunningEntryIdentity(entryId, shown.workspaceId, shown.start), fields, shown.start)) {
                    settle { controls ->
                        val entry = controls.runningEntry()
                        entry == null || entry.start != shown.start || entry.fields().let { it == fields || it != before }
                    }
                }
            } finally {
                local.update { if (it.edit == edit) it.copy(edit = null) else it }
            }
        }
        fieldWrites += write
        write.invokeOnCompletion { fieldWrites -= write }
    }

    /** Waits until the controls pass [done], or gives up after [SETTLE_MS]. */
    private suspend fun settle(done: (TimerControls) -> Boolean) {
        withTimeoutOrNull(SETTLE_MS) {
            cached.first { it is CachedControls.Read && done(it.controls) }
        }
    }

    private fun cachedControls(): TimerControls? = (cached.value as? CachedControls.Read)?.controls

    private fun cachedRunning(): CachedTimeEntry? = cachedControls()?.runningEntry()

    private fun editFor(shown: TimerState.Running): FieldEdit? {
        val read = cached.value as? CachedControls.Read ?: return null
        val entry = read.controls.runningEntry()?.takeIf { it.id == shown.entryId } ?: return null
        return local.value.edit?.takeIf { it.matches(entry, read) }
    }

    /** The state as of now, without labels, which the tap handlers act on. */
    private fun snapshot(): TimerState = timerState(cached.value, accountSettings.value?.defaultWorkspaceId, local.value, pauseOffered.value)

    private data class Catalog(val projects: List<CachedProject> = emptyList(), val tasks: List<CachedTask> = emptyList())

    /** A tap's open trace section, which [end] closes once the screen shows a state that is [done]. */
    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * How long a tap shows what it did while the cache does not: far longer than a write to
         * the phone's database or a server's answer reaching the cache takes, so only a cache that
         * never shows the tap reaches it.
         */
        const val SETTLE_MS = 5_000L
    }
}
