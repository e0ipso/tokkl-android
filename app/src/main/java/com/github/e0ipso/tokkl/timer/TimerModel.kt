package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.cache.target
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.entry.withPicked
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.DiscardedPomodoro
import com.github.e0ipso.tokkl.timerstate.PauseChainActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CompletableDeferred
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

/** What the timer shows. */
sealed interface TimerState {

    /** The phone has not read its cache yet. */
    data object Loading : TimerState

    /**
     * No entry runs. Start starts one with [fields] in [workspaceId], the account's default
     * workspace, which is null until the phone has read it from the server. Start waits for it.
     */
    data class Idle(val fields: EntryFields, val labels: EntryLabels, val workspaceId: Long?, val authoritative: Boolean = false) : TimerState

    /**
     * An entry runs since [start], with [fields]: the cached entry's, or an edit the cache does not
     * have yet.
     *
     * @property entryId the cached entry's id, or null from a Start tap until the cache shows the entry.
     * @property original the project, task and tags the cache has, which the picker keeps on offer
     *   when they are archived.
     * @property unsent the server has not received every change to the entry.
     * @property accumulatedSeconds what the server's pause chain stored before this entry, its
     *   resumed clone, which the clock adds to the time since [start] (docs/PRD.md T1), or 0
     *   outside a chain.
     * @property pausable whether Pause shows: on a server with the timer API, outside a Pomodoro
     *   session.
     * @property clockOffset what the clock adds to the phone's time when it counts from [start]:
     *   the server's clock offset for the clone, whose start the server set, and zero otherwise.
     */
    data class Running(
        val entryId: Long?,
        val workspaceId: Long,
        val start: Instant,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
        val unsent: Boolean,
        val accumulatedSeconds: Long = 0,
        val target: TimerTarget? = null,
        val pausable: Boolean = true,
        val clockOffset: Duration = Duration.ZERO,
    ) : TimerState

    /**
     * The server's pause chain is paused at [seconds], the time its entries store (docs/PRD.md
     * T1). Resume starts [fields] in [workspaceId] as a new entry, and Stop ends the chain. The
     * fields are read-only: the server keeps the ones recorded at Pause.
     *
     * @property original the project, task and tags the chain keeps.
     */
    data class Paused(
        val workspaceId: Long,
        val seconds: Long,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
        val target: TimerTarget? = null,
    ) : TimerState
}

/**
 * What the timer's screens say about it: a Pause, Resume or Stop while paused that did not
 * happen, and a pomodoro the server discarded as too short. The navigation host shows them in one
 * place, whichever of the timer's pages is on screen.
 */
sealed interface TimerNotice {

    /**
     * A pause chain action the server refused, or that got no answer, as [result] says.
     * [number] tells two failures with the same result apart.
     */
    data class ActionFailed(val result: TimerActionResult, val number: Long) : TimerNotice

    /** The server discarded the pomodoro [entryId] as too short to save. */
    data class PomodoroDiscarded(val entryId: Long) : TimerNotice
}

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
class TimerModel(
    controls: Flow<TimerControls>,
    projects: Flow<List<CachedProject>>,
    tasks: Flow<List<CachedTask>>,
    val accountSettings: StateFlow<AccountSettings?>,
    private val changes: EntryChanges,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val trace: TapTrace = TapTrace.None,
    private val pauseChain: PauseChainActions? = null,
    private val sendQueued: suspend () -> Unit = {},
    discardNotices: Flow<DiscardedPomodoro?> = flowOf(null),
    private val takeDiscardNotice: suspend () -> DiscardedPomodoro? = { null },
    timerApi: Flow<Boolean> = flowOf(true),
) {

    /** The trace section of the last Start or Stop tap, until the screen shows what it did. */
    private var openTap: OpenTap? = null

    private val fieldWrites = mutableSetOf<Job>()
    private var openingRunning = false

    private val local = MutableStateFlow(Local())

    private val cached: StateFlow<CachedControls> = controls
        .map<TimerControls, CachedControls> { entry ->
            val editId = local.value.edit?.entryId
            CachedControls.Read(entry, editId, editId?.let { changes.currentId(it) })
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CachedControls.Unread)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    private val queuedStarts = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One item for each timer start made here, Start, Resume, Continue or a favorite, once the
     * queue or the server has it, for the notification permission's question (docs/PRD.md N5).
     * The start never waits for a collector. The latest start waits for a screen collector after
     * a configuration change.
     */
    val starts: SharedFlow<Unit> = queuedStarts.asSharedFlow()

    private val pauseOffered: StateFlow<Boolean> = timerApi.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val state: StateFlow<TimerState> = combine(cached, catalog, accountSettings, local, pauseOffered) { running, names, settings, taps, offered ->
        timerState(running, settings?.defaultWorkspaceId, taps, offered).labelled(names)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerState.Loading)

    private val failures = MutableStateFlow<TimerNotice.ActionFailed?>(null)
    private var failureCount = 0L

    /** The too-short pomodoro notice a screen has claimed and not finished showing. */
    private val claimedDiscard = MutableStateFlow<TimerNotice.PomodoroDiscarded?>(null)

    /**
     * What the timer's screens should say now, or null: a claimed notice until it has shown, then
     * the latest failed pause chain action, then a too-short pomodoro no screen has shown yet. A
     * screen [claim]s a notice before it shows it and calls [noticeShown] after.
     */
    val notice: StateFlow<TimerNotice?> = combine(claimedDiscard, failures, discardNotices) { claimed, failure, discarded ->
        claimed ?: failure ?: discarded?.let { TimerNotice.PomodoroDiscarded(it.entryId) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Claims [notice] for the screen about to show it, and returns what to show, or null when
     * there is nothing left to show. A too-short pomodoro is recorded as shown here, once, so a
     * replay of its state or a restart does not show it again.
     */
    suspend fun claim(notice: TimerNotice): TimerNotice? = when (notice) {
        is TimerNotice.ActionFailed -> notice.takeIf { failures.value == it }
        is TimerNotice.PomodoroDiscarded ->
            if (claimedDiscard.value == notice) {
                notice
            } else {
                takeDiscardNotice()?.let { taken -> TimerNotice.PomodoroDiscarded(taken.entryId).also { claimedDiscard.value = it } }
            }
    }

    /** The screen has shown [notice], or stopped showing it. */
    fun noticeShown(notice: TimerNotice) {
        when (notice) {
            is TimerNotice.ActionFailed -> failures.compareAndSet(notice, null)
            is TimerNotice.PomodoroDiscarded -> claimedDiscard.compareAndSet(notice, null)
        }
    }

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Idle ?: return
        val workspaceId = shown.workspaceId ?: return
        beginTap(TapTrace.START) { it is TimerState.Running }
        startWith(workspaceId, shown.fields, typed = null)
    }

    /**
     * Tells the model that the screen has drawn [state], which ends the trace section of a Start
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
    private fun typedFor(shown: TimerState.Running): Typed? =
        editFor(shown)?.takeIf { !it.sent }?.let { edit ->
            shown.entryId?.let { Typed(RunningEntryIdentity(it, shown.workspaceId, shown.start), edit) }
        }

    /**
     * Starts a timer with [fields] in [workspaceId], after queuing the [typed] description of the
     * running entry, which the start then stops. The idle timer's draft makes way for the new entry,
     * and comes back when nothing starts.
     */
    private fun startWith(workspaceId: Long, fields: EntryFields, typed: Typed?) {
        val draft = local.value.draft
        val starting = Pending.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), fields, workspaceId)
        local.update { it.copy(pending = starting, draft = EntryFields(), edit = if (typed != null) null else it.edit) }
        // Undispatched, so the queue reads the tap time within the tap's handler.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        val stopping = Pending.Stopping(shown.start)
        local.update { it.copy(pending = stopping, edit = null) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        val acting = Pending.Acting(target)
        local.update { it.copy(pending = acting) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = send()
                if (result == TimerActionResult.Done) {
                    onDone()
                    settle(done)
                } else {
                    failures.value = TimerNotice.ActionFailed(result, ++failureCount)
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
        if (pending is Pending.Stopping || pending is Pending.Acting) return null
        openingRunning = true
        try {
            val id = if (pending is Pending.Starting) {
                val startedId = pending.entryId.await() ?: return null
                local.first { it.pending != pending }
                startedId
            } else shown.entryId ?: return null
            descriptionDone()
            while (fieldWrites.isNotEmpty()) fieldWrites.toList().joinAll()
            val settled = snapshot() as? TimerState.Running ?: return null
            val identity = RunningEntryIdentity(id, shown.workspaceId, if (pending is Pending.Starting) settled.start else shown.start)
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
                    else -> local.update { it.copy(edit = Edit(shown.entryId, shown.workspaceId, shown.start, fields, sent = false)) }
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
        val edit = Edit(entryId, shown.workspaceId, shown.start, fields, sent = true)
        local.update { it.copy(edit = edit) }
        val write = scope.launch(start = CoroutineStart.UNDISPATCHED) {
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

    private fun TimerControls.runningEntry(): CachedTimeEntry? = (this as? TimerControls.Running)?.entry

    private fun editFor(shown: TimerState.Running): Edit? {
        val read = cached.value as? CachedControls.Read ?: return null
        val entry = read.controls.runningEntry()?.takeIf { it.id == shown.entryId } ?: return null
        return local.value.edit?.takeIf { it.matches(entry, read) }
    }

    private fun Edit.matches(entry: CachedTimeEntry, read: CachedControls.Read): Boolean {
        val remapped = entryId != entry.id && read.editId == entryId && read.resolvedEditId == entry.id
        return (entryId == entry.id || remapped) && workspaceId == entry.workspaceId &&
            (start == entry.start || (remapped && start.truncatedTo(ChronoUnit.SECONDS) == entry.start))
    }

    /** The state as of now, without labels, which the tap handlers act on. */
    private fun snapshot(): TimerState = timerState(cached.value, accountSettings.value?.defaultWorkspaceId, local.value, pauseOffered.value)

    private fun timerState(cached: CachedControls, defaultWorkspaceId: Long?, taps: Local, pauseOffered: Boolean): TimerState {
        if (cached !is CachedControls.Read) return TimerState.Loading
        val controls = cached.controls
        val idle = TimerState.Idle(taps.draft, EntryLabels(), defaultWorkspaceId, authoritative = controls == TimerControls.Idle && taps.pending == null)
        when (val pending = taps.pending) {
            // The cache's entry shows once the start has settled, with an id that Stop can take.
            is Pending.Starting -> return TimerState.Running(
                entryId = null,
                workspaceId = pending.workspaceId,
                start = pending.at,
                fields = pending.fields,
                labels = EntryLabels(),
                original = Picked(),
                unsent = true,
                // A tap on it waits for the cache's entry, as every tap during a Start does.
                pausable = pauseOffered,
            )
            is Pending.Stopping -> if (controls.runningEntry()?.start == pending.start) return idle
            // The timer shows what it showed until the server's answer reaches the controls.
            is Pending.Acting, null -> Unit
        }
        return when (controls) {
            TimerControls.Idle -> idle
            is TimerControls.Paused -> TimerState.Paused(
                workspaceId = controls.workspaceId,
                seconds = controls.seconds,
                fields = controls.fields,
                labels = EntryLabels(),
                original = Picked(controls.fields.projectId, controls.fields.taskId, controls.fields.tags),
                target = controls.target,
            )
            is TimerControls.Running -> {
                val entry = controls.entry
                val cachedFields = entry.fields()
                TimerState.Running(
                    entryId = entry.id,
                    workspaceId = entry.workspaceId,
                    start = entry.start,
                    fields = taps.edit?.takeIf { it.matches(entry, cached) }?.fields ?: cachedFields,
                    labels = EntryLabels(),
                    original = Picked(cachedFields.projectId, cachedFields.taskId, cachedFields.tags),
                    unsent = controls.unsent,
                    accumulatedSeconds = controls.accumulatedSeconds,
                    target = controls.target,
                    pausable = controls.pausable,
                    clockOffset = controls.clockOffset,
                )
            }
        }
    }

    private fun TimerState.labelled(names: Catalog): TimerState = when (this) {
        TimerState.Loading -> this
        is TimerState.Idle -> copy(labels = entryLabels(fields, names.projects, names.tasks))
        is TimerState.Running -> copy(labels = entryLabels(fields, names.projects, names.tasks))
        is TimerState.Paused -> copy(labels = entryLabels(fields, names.projects, names.tasks))
    }

    /** The controls, once read. */
    private sealed interface CachedControls {
        data object Unread : CachedControls

        data class Read(val controls: TimerControls, val editId: Long?, val resolvedEditId: Long?) : CachedControls
    }

    private data class Catalog(val projects: List<CachedProject> = emptyList(), val tasks: List<CachedTask> = emptyList())

    /** What the taps have done that the cache or the server may not show yet. */
    private data class Local(
        /** What Start starts with. */
        val draft: EntryFields = EntryFields(),
        val edit: Edit? = null,
        val pending: Pending? = null,
    )

    /** [fields] for the running entry that started at [start], shown until the cache has them. [sent] once queued. */
    private data class Edit(val entryId: Long, val workspaceId: Long, val start: Instant, val fields: EntryFields, val sent: Boolean)

    /** A tap's open trace section, which [end] closes once the screen shows a state that is [done]. */
    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)

    /** An [edit] not queued yet for the active [identity], which a Continue or a favorite stops. */
    private data class Typed(val identity: RunningEntryIdentity, val edit: Edit)

    /** A tap that the cache or the server may not show yet. */
    private sealed interface Pending {
        /** Start was tapped at [at], in whole milliseconds as the cache keeps times. */
        data class Starting(val at: Instant, val fields: EntryFields, val workspaceId: Long) : Pending {
            val entryId = CompletableDeferred<Long?>()
        }

        /** Stop was tapped for the entry that started at [start]. */
        data class Stopping(val start: Instant) : Pending

        /** Pause, Resume or Stop while paused went to the server for what [target] showed. */
        data class Acting(val target: TimerTarget) : Pending
    }

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

/** No durable target during an optimistic transition; stale controls cannot act on its successor. */
internal val TimerState.target: TimerTarget?
    get() = when (this) {
        is TimerState.Running -> target
        is TimerState.Paused -> target
        else -> null
    }

/**
 * The instant the running clock counts from on the phone's clock: the start, moved back by the
 * server's clock offset for its clone.
 */
internal val TimerState.Running.countsFrom: Instant get() = start.minus(clockOffset)
