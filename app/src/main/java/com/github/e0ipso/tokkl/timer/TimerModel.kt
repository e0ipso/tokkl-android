package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.seconds
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.entry.withPicked
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    data class Idle(val fields: EntryFields, val labels: EntryLabels, val workspaceId: Long?) : TimerState

    /**
     * An entry runs since [start], with [fields]: the cached entry's, or an edit the cache does not
     * have yet.
     *
     * @property entryId the cached entry's id, or null from a Start tap until the cache shows the entry.
     * @property original the project, task and tags the cache has, which the picker keeps on offer
     *   when they are archived.
     * @property unsent the server has not received every change to the entry.
     * @property accumulatedSeconds what the pause chain's stopped segments store before this entry,
     *   which the clock adds to the time since [start] (docs/PRD.md T1), or 0 outside a chain.
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
    ) : TimerState

    /**
     * The timer is paused at [seconds], the time the pause chain's segments store (docs/PRD.md
     * T1). Resume starts [fields] in [workspaceId] as a new entry, and an edit changes what it
     * starts. Stop ends the chain.
     *
     * @property original the project, task and tags the chain keeps, which the picker keeps on
     *   offer when they are archived.
     */
    data class Paused(
        val workspaceId: Long,
        val seconds: Long,
        val fields: EntryFields,
        val labels: EntryLabels,
        val original: Picked,
    ) : TimerState
}

/**
 * The timer on the Timer page (docs/PRD.md T1): Start, Pause, Resume, Stop and the running entry's
 * fields. Every tap goes to [changes], the change queue, which changes the cache on the phone and
 * queues the change for the server, so nothing here waits for the network (docs/PRD.md principle
 * 2). The timer shows what the queue's [controls] say, and from the tap until the cache shows it,
 * what the tap did. Taps in between are ignored, so a double tap starts one entry.
 *
 * Start starts the draft that the idle timer holds, and [continueEntry] starts a timer from a past
 * entry. The cache keeps one running entry, and a start on the phone stops a cached running entry
 * first, as the server does, so one entry runs at a time, offline too. Pause stops the running
 * entry and Resume starts a new one with its fields (T1); any other start ends the pause chain.
 *
 * A pick of project, task or tags, and billable, goes to the queue at once. A description goes
 * once typing ends, with [descriptionDone], Pause or Stop, so the queue does not get a change for
 * every letter. Until the cache has an edit, the timer shows it over the cache's entry, so a
 * refresh cannot take back what is being typed. While paused, edits change what Resume starts.
 *
 * Call the tap handlers on the main thread. The running clock is the screen's: it counts from
 * [TimerState.Running.start] on the phone and never asks the server (principle 3).
 *
 * @param controls the running entry or paused chain, as ChangeQueue.timerControls gives them.
 * @param accountSettings for the default workspace, where Start starts, and for the Today and
 *   This week totals.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 * @param trace marks each Start and Stop tap until the screen reports with [shown] that it drew
 *   what the tap did.
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
     * queue has it, for the notification permission's question (docs/PRD.md N5). The start never
     * waits for a collector. The latest start waits for a screen collector after a configuration
     * change.
     */
    val starts: SharedFlow<Unit> = queuedStarts.asSharedFlow()

    val state: StateFlow<TimerState> = combine(cached, catalog, accountSettings, local) { running, names, settings, taps ->
        timerState(running, settings?.defaultWorkspaceId, taps).labelled(names)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerState.Loading)

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
     * cache is unread, and while a Start or Stop the cache does not show yet is pending, so a
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
     * known, or while a Start or Stop is pending.
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
     * Stops the running entry, with a description still being typed, which ends a pause chain.
     * While paused, ends the chain, which needs no request.
     */
    fun stop() {
        if (local.value.pending != null) return
        when (val shown = snapshot()) {
            is TimerState.Running -> stopRunning(shown)
            is TimerState.Paused -> stopPaused()
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

    private fun stopPaused() {
        val paused = cachedControls() as? TimerControls.Paused ?: return
        beginTap(TapTrace.STOP) { it is TimerState.Idle }
        val clearing = Pending.Clearing(paused.target.chainId)
        local.update { it.copy(pending = clearing, pausedEdit = null) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.stop(paused.target)) settle { (it as? TimerControls.Paused)?.target?.chainId != clearing.chainId }
            } finally {
                local.update { if (it.pending == clearing) it.copy(pending = null) else it }
            }
        }
    }

    /**
     * Pauses the running entry (docs/PRD.md T1), with a description still being typed: the queue
     * stops it and keeps its fields, and the timer shows the paused total at once.
     */
    fun pause() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Running ?: return
        val running = cachedControls() as? TimerControls.Running ?: return
        if (shown.entryId != running.entry.id) return
        val edited = editFor(shown)?.fields
        val pausing = Pending.Pausing(shown.start, shown.workspaceId, running.seconds(clock.instant()), shown.fields, shown.original)
        local.update { it.copy(pending = pausing, edit = null) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.pause(running.target, edited)) settle { it.runningEntry()?.start != shown.start }
            } finally {
                local.update { if (it.pending == pausing) it.copy(pending = null) else it }
            }
        }
    }

    /**
     * Resumes the paused timer (docs/PRD.md T1): the queue starts a new entry with the kept
     * fields, and an edit still being made to them, and the clock goes on from the paused total.
     */
    fun resume() {
        if (local.value.pending != null) return
        val shown = snapshot() as? TimerState.Paused ?: return
        val paused = cachedControls() as? TimerControls.Paused ?: return
        val edited = local.value.pausedEdit?.takeIf { it.chainId == paused.target.chainId }?.fields
        val starting = Pending.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), shown.fields, shown.workspaceId, shown.seconds)
        local.update { it.copy(pending = starting, pausedEdit = null) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val id = changes.resume(paused.target, edited)
                starting.entryId.complete(id)
                if (id != null) {
                    queuedStarts.tryEmit(Unit)
                    settle { it.runningEntry()?.start?.isBefore(starting.at) == false }
                }
            } finally {
                starting.entryId.complete(null)
                local.update { if (it.pending == starting) it.copy(pending = null) else it }
            }
        }
    }

    /** Shows [description] as typed. It goes to the queue with [descriptionDone], a pick, Pause or Stop. */
    fun editDescription(description: String) = edit(send = false) { it.copy(description = description) }

    /** Sends a description typed for the running entry, or the paused timer, when it differs from the cache's. */
    fun descriptionDone() {
        if (local.value.pending != null) return
        when (val shown = snapshot()) {
            is TimerState.Running -> {
                val edit = editFor(shown)?.takeIf { !it.sent } ?: return
                if (edit.fields == cachedRunning()?.fields()) {
                    local.update { if (it.edit == edit) it.copy(edit = null) else it }
                } else {
                    send(shown, edit.fields)
                }
            }
            is TimerState.Paused -> {
                val paused = cachedControls() as? TimerControls.Paused ?: return
                val edit = local.value.pausedEdit?.takeIf { it.chainId == paused.target.chainId && !it.sent } ?: return
                if (edit.fields == paused.fields) {
                    local.update { if (it.pausedEdit == edit) it.copy(pausedEdit = null) else it }
                } else {
                    sendPaused(paused, edit.fields)
                }
            }
            else -> Unit
        }
    }

    /**
     * Settles the description and initial Start before opening the shared editor. The caller
     * clears field focus first, so the text field has handed over its latest value. Waiting is
     * for local writes only. Keep the captured id through remapping and never select a replacement
     * just because it has the same start time. Cancellation, including a disposed navigation host,
     * opens nothing.
     */
    suspend fun runningForEdit(): RunningEntryIdentity? {
        if (openingRunning) return null
        val shown = snapshot() as? TimerState.Running ?: return null
        val pending = local.value.pending
        if (pending is Pending.Stopping) return null
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
     * Changes the idle timer's draft, the running entry's fields, or what the paused timer resumes.
     * While a tap the cache does not show yet is pending, the running entry's fields stay as they
     * are: its id is not known yet, or it has stopped.
     */
    private fun edit(send: Boolean, change: (EntryFields) -> EntryFields) {
        when (val shown = snapshot()) {
            TimerState.Loading -> Unit
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
            is TimerState.Paused -> {
                if (local.value.pending != null) return
                val paused = cachedControls() as? TimerControls.Paused ?: return
                val fields = change(shown.fields)
                when {
                    fields == shown.fields -> Unit
                    send -> sendPaused(paused, fields)
                    else -> local.update { it.copy(pausedEdit = PausedEdit(paused.target.chainId, fields, sent = false)) }
                }
            }
        }
    }

    /** Keeps [fields] for the [paused] timer's Resume, and shows them until the cache has them. */
    private fun sendPaused(paused: TimerControls.Paused, fields: EntryFields) {
        val edit = PausedEdit(paused.target.chainId, fields, sent = true)
        local.update { it.copy(pausedEdit = edit) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.editPaused(paused.target, fields)) {
                    settle { (it as? TimerControls.Paused)?.takeIf { now -> now.target.chainId == edit.chainId }?.fields != paused.fields }
                }
            } finally {
                local.update { if (it.pausedEdit == edit) it.copy(pausedEdit = null) else it }
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

    /** Waits until the queue's controls pass [done], or gives up after [SETTLE_MS]. */
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
    private fun snapshot(): TimerState = timerState(cached.value, accountSettings.value?.defaultWorkspaceId, local.value)

    private fun timerState(cached: CachedControls, defaultWorkspaceId: Long?, taps: Local): TimerState {
        if (cached !is CachedControls.Read) return TimerState.Loading
        val controls = cached.controls
        val idle = TimerState.Idle(taps.draft, EntryLabels(), defaultWorkspaceId)
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
                accumulatedSeconds = pending.accumulatedSeconds,
            )
            is Pending.Stopping -> if (controls.runningEntry()?.start == pending.start) return idle
            is Pending.Pausing -> if (controls.runningEntry()?.start == pending.start) {
                return TimerState.Paused(pending.workspaceId, pending.seconds, pending.fields, EntryLabels(), pending.original)
            }
            is Pending.Clearing -> if ((controls as? TimerControls.Paused)?.target?.chainId == pending.chainId) return idle
            null -> Unit
        }
        return when (controls) {
            TimerControls.Idle -> idle
            is TimerControls.Paused -> TimerState.Paused(
                workspaceId = controls.workspaceId,
                seconds = controls.seconds,
                fields = taps.pausedEdit?.takeIf { it.chainId == controls.target.chainId }?.fields ?: controls.fields,
                labels = EntryLabels(),
                original = Picked(controls.fields.projectId, controls.fields.taskId, controls.fields.tags),
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

    /** The queue's controls, once read. */
    private sealed interface CachedControls {
        data object Unread : CachedControls

        data class Read(val controls: TimerControls, val editId: Long?, val resolvedEditId: Long?) : CachedControls
    }

    private data class Catalog(val projects: List<CachedProject> = emptyList(), val tasks: List<CachedTask> = emptyList())

    /** What the taps have done that the cache may not show yet. */
    private data class Local(
        /** What Start starts with. */
        val draft: EntryFields = EntryFields(),
        val edit: Edit? = null,
        val pausedEdit: PausedEdit? = null,
        val pending: Pending? = null,
    )

    /** [fields] for the running entry that started at [start], shown until the cache has them. [sent] once queued. */
    private data class Edit(val entryId: Long, val workspaceId: Long, val start: Instant, val fields: EntryFields, val sent: Boolean)

    /** [fields] for the Resume of the paused chain [chainId], shown until the chain has them. [sent] once kept. */
    private data class PausedEdit(val chainId: Long, val fields: EntryFields, val sent: Boolean)

    /** A tap's open trace section, which [end] closes once the screen shows a state that is [done]. */
    private class OpenTap(val end: () -> Unit, val done: (TimerState) -> Boolean)

    /** An [edit] not queued yet for the active [identity], which a Continue or a favorite stops. */
    private data class Typed(val identity: RunningEntryIdentity, val edit: Edit)

    /** A tap that the cache may not show yet. */
    private sealed interface Pending {
        /**
         * Start or Resume was tapped at [at], in whole milliseconds as the cache keeps times.
         * [accumulatedSeconds] is the paused total a Resume goes on from.
         */
        data class Starting(val at: Instant, val fields: EntryFields, val workspaceId: Long, val accumulatedSeconds: Long = 0) : Pending {
            val entryId = CompletableDeferred<Long?>()
        }

        /** Stop was tapped for the entry that started at [start]. */
        data class Stopping(val start: Instant) : Pending

        /** Pause was tapped for the entry that started at [start], with the clock at [seconds]. */
        data class Pausing(
            val start: Instant,
            val workspaceId: Long,
            val seconds: Long,
            val fields: EntryFields,
            val original: Picked,
        ) : Pending

        /** Stop was tapped while the chain [chainId] was paused. */
        data class Clearing(val chainId: Long) : Pending
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * How long a tap shows what it did while the cache does not: far longer than a write to
         * the phone's database takes, so only a cache that never shows the tap reaches it.
         */
        const val SETTLE_MS = 5_000L
    }
}
