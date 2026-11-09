package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.LocalWrite
import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.cache.localWrite
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.target
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * The timer's taps and field edits (docs/PRD.md T1, T2, T5), which [TimerViewModel] adapts for its
 * screens. It is the one owner of [taps], what the taps did that the cache or the server may not
 * show yet, and changes it only by [dispatch]ing a [TapEvent] through [reduce]. A tap decides from
 * [now], dispatches its event, and launches its effect, a queue write or a server action, which
 * dispatches the event that ends it. What the screens show, [state], is the projection
 * [timerState] of [taps] over the cached controls.
 *
 * Start, Stop of a running entry, Continue and favorites go to [changes], the change queue, so they
 * never wait for the network (docs/PRD.md principle 2). From such a tap until the cache shows it,
 * other taps are ignored, so a double tap starts one entry. A start stops a cached running entry
 * first, as the server does, so one entry runs at a time, offline too. Pause, Resume and Stop while
 * paused belong to the server's pause chain and go to [pauseChain] at once, at the revision the
 * timer was shown at, so a stale tap gets a refusal; until the server's state shows, the timer
 * keeps what it showed and ignores other taps. A refusal or no answer goes to [notices].
 *
 * A pick of project, task or tags, billable and a picked suggestion go to the queue at once. A
 * description goes once typing ends, so the queue does not get a change for every letter. Until
 * the cache has an edit, the timer shows it over the cache's entry. A paused timer's fields are
 * read-only.
 *
 * A queue write the phone's storage refuses ([LocalWrite.Failed]) gives the timer back as it was
 * before the tap, as the entry form gives its form back: Start brings its draft back, Stop leaves
 * the entry running, and an edit stays on the timer as typed, not sent, for the next send to take.
 *
 * Call everything on the main thread.
 *
 * @param controls the running entry or paused chain, as the app's shared timer controls give them.
 * @param settings for the default workspace, where Start and a favorite start.
 * @param timerApi whether the server offers the timer API, which decides whether a timer that the
 *   cache does not show yet offers Pause.
 * @param sender sends the queue now and waits for it, so a description typed before Pause reaches
 *   the server before the Pause does.
 * @param sections marks each Start and Stop tap in the system trace.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 * @param scope the view model's scope.
 */
internal class TimerController(
    controls: Flow<TimerControls>,
    projects: Flow<List<Project>>,
    tasks: Flow<List<Task>>,
    private val settings: StateFlow<AccountSettings?>,
    timerApi: Flow<Boolean>,
    private val changes: RunningEntryChanges,
    private val pauseChain: PauseActions?,
    private val sender: QueueSender,
    private val notices: TimerNoticeBoard,
    private val sections: TapSections,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {

    private val mutableTaps = MutableStateFlow(TimerTaps())

    /** What the taps did that the cache or the server may not show yet. */
    val taps: StateFlow<TimerTaps> = mutableTaps.asStateFlow()

    private val cached: StateFlow<CachedControls> = controls
        .map<TimerControls, CachedControls> { entry ->
            val editId = taps.value.edit?.entryId
            CachedControls.Read(entry, editId, editId?.let { changes.currentId(it) })
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CachedControls.Unread)

    private val catalog: StateFlow<Catalog> = combine(projects, tasks, ::Catalog)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Catalog())

    private val pauseOffered: StateFlow<Boolean> =
        timerApi.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** What the timer shows: [taps] over the cached controls, with the names of its project, task and tags. */
    val state: StateFlow<TimerState> = combine(
        cached,
        catalog,
        settings,
        taps,
        pauseOffered,
    ) { running, names, current, tapped, offered ->
        timerState(running, current?.defaultWorkspaceId, tapped, offered).labelled(names.projects, names.tasks)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerState.Loading)

    private val queuedStarts = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One item for each timer start made here, Start, Resume, Continue or a favorite, once the
     * queue or the server has it, for the notification permission's question (docs/PRD.md N5).
     * The start never waits for a collector. The latest start waits for a screen collector after
     * a configuration change.
     */
    val starts: SharedFlow<Unit> = queuedStarts.asSharedFlow()

    /** The running entry's field writes that have not ended. */
    private val writes = mutableSetOf<Job>()
    private var openingRunning = false

    /** The timer state as of now, without labels, which the taps and the field edits act on. */
    fun now(): TimerState = timerState(cached.value, settings.value?.defaultWorkspaceId, taps.value, pauseOffered.value)

    private fun dispatch(event: TapEvent) = mutableTaps.update { it.reduce(event) }

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() {
        if (taps.value.pending != null) return
        val shown = now() as? TimerState.Idle ?: return
        val workspaceId = shown.workspaceId ?: return
        sections.begin(TapTrace.START) { it is TimerState.Running }
        startWith(workspaceId, shown.fields, typed = null)
    }

    /**
     * Continues [source] (docs/PRD.md T2): starts a timer with its description, project, task,
     * tags and billable, in its workspace, as the web app's Continue does. A running entry stops
     * at the new one's start, with a description still being typed for it. Ignored while the
     * cache is unread, and while a tap the cache or the server does not show yet is pending, so a
     * double tap starts one entry.
     */
    fun continueEntry(source: TimeEntry) {
        if (taps.value.pending != null) return
        val typed = when (val shown = now()) {
            TimerState.Loading -> return
            is TimerState.Idle, is TimerState.Paused -> null
            is TimerState.Running -> taps.value.typedFor(shown, cached.value)
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
        if (taps.value.pending != null) return
        when (val shown = now()) {
            TimerState.Loading -> Unit

            is TimerState.Idle -> shown.workspaceId?.let { startWith(it, fields, typed = null) }

            is TimerState.Running -> startWith(
                settings.value?.defaultWorkspaceId ?: shown.workspaceId,
                fields,
                taps.value.typedFor(shown, cached.value),
            )

            is TimerState.Paused -> startWith(
                settings.value?.defaultWorkspaceId ?: shown.workspaceId,
                fields,
                typed = null,
            )
        }
    }

    /**
     * Starts a timer with [fields] in [workspaceId], after queuing the [typed] description of the
     * running entry, which the start then stops. The idle timer's draft makes way for the new entry,
     * and comes back when nothing starts.
     */
    private fun startWith(workspaceId: WorkspaceId, fields: EntryFields, typed: TypedEdit?) {
        val draft = taps.value.draft
        val starting = PendingTap.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), fields, workspaceId)
        dispatch(TapEvent.StartTapped(starting, typed = typed != null))
        // Undispatched, so the queue reads the tap time within the tap's handler.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var queued = false
            try {
                val started = localWrite {
                    if (typed != null) changes.updateRunning(typed.identity, typed.edit.fields, typed.identity.start)
                    changes.start(workspaceId, starting.fields)
                }
                if (started is LocalWrite.Written) {
                    starting.entryId.complete(started.value)
                    queued = true
                    queuedStarts.tryEmit(Unit)
                    cached.settle { it.runningEntry()?.start?.isBefore(starting.at) == false }
                }
            } finally {
                starting.entryId.complete(null)
                dispatch(TapEvent.StartEnded(starting, queued, draft, typed?.edit))
            }
        }
    }

    /**
     * Stops the running entry, with a description still being typed, through the queue. A
     * running clone stops in the same way, and the server ends its chain. While paused, ends the
     * server's chain with Discard.
     */
    fun stop() {
        if (taps.value.pending != null) return
        when (val shown = now()) {
            is TimerState.Running -> stopRunning(shown)
            is TimerState.Paused -> discard()
            else -> Unit
        }
    }

    private fun stopRunning(shown: TimerState.Running) {
        val entryId = shown.entryId ?: return
        val edit = taps.value.editFor(shown, cached.value)
        sections.begin(TapTrace.STOP) { it is TimerState.Idle }
        val stopping = PendingTap.Stopping(shown.start)
        dispatch(TapEvent.StopTapped(stopping))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var refused = false
            try {
                when (val stopped = localWrite { changes.stop(entryId, edit?.fields) }) {
                    is LocalWrite.Written -> if (stopped.value) {
                        cached.settle { it.runningEntry()?.start != shown.start }
                    }

                    is LocalWrite.Failed -> refused = true
                }
            } finally {
                // The edit the refused Stop took with it shows again, not sent.
                dispatch(TapEvent.TapEnded(stopping, restored = edit?.takeIf { refused }?.copy(sent = false)))
            }
        }
    }

    /**
     * Pauses the running entry on the server (docs/PRD.md T1). A description still being typed is
     * queued and sent first, so the chain keeps it. The timer shows what it showed until the
     * server's paused state does.
     */
    fun pause() {
        val actions = pauseChain ?: return
        if (taps.value.pending != null) return
        val shown = now() as? TimerState.Running ?: return
        val running = cached.value.controls() as? TimerControls.Running ?: return
        if (!running.pausable || shown.entryId != running.entry.id) return
        val typed = taps.value.editFor(shown, cached.value)?.takeIf { !it.sent }
        if (typed != null) send(shown, typed.fields)
        act(running.target, done = { it.target != running.target }) {
            if (typed != null) {
                awaitWrites()
                sender.sendQueued()
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
        if (taps.value.pending != null) return
        if (now() !is TimerState.Paused) return
        val paused = cached.value.controls() as? TimerControls.Paused ?: return
        act(paused.target, done = { it.target != paused.target }, onDone = { queuedStarts.tryEmit(Unit) }) {
            actions.resume(paused.target.revision)
        }
    }

    /** Ends the paused chain on the server, at the revision it was shown at. */
    private fun discard() {
        val actions = pauseChain ?: return
        val paused = cached.value.controls() as? TimerControls.Paused ?: return
        sections.begin(TapTrace.STOP) { it is TimerState.Idle }
        act(paused.target, done = { it.target != paused.target }) { actions.discard(paused.target.revision) }
    }

    /**
     * Sends a pause chain action for what [target] showed, ignoring other taps until the server
     * answers and its state shows, as [done] tells. A refusal or no answer goes to [notices].
     */
    private fun act(
        target: TimerTarget,
        done: (TimerControls) -> Boolean,
        onDone: () -> Unit = {},
        send: suspend () -> TimerActionResult,
    ) {
        val acting = PendingTap.Acting(target)
        dispatch(TapEvent.ActionTapped(acting))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = send()
                if (result == TimerActionResult.Done) {
                    onDone()
                    cached.settle(done)
                } else {
                    notices.failed(result)
                }
            } finally {
                dispatch(TapEvent.TapEnded(acting))
            }
        }
    }

    /** A focused control keeps the durable target that supplied its displayed context. */
    fun isShown(target: TimerTarget): Boolean = taps.value.pending == null && cached.value.controls()?.target == target

    fun editDescription(description: String) = edit(send = false) { it.copy(description = description) }

    /** Sends a description typed for the running entry when it differs from the cache's. */
    fun descriptionDone() {
        if (taps.value.pending != null) return
        val shown = now() as? TimerState.Running ?: return
        val edit = taps.value.editFor(shown, cached.value)?.takeIf { !it.sent } ?: return
        if (edit.fields == cached.value.controls()?.runningEntry()?.fields()) {
            dispatch(TapEvent.EditEnded(edit))
        } else {
            send(shown, edit.fields)
        }
    }

    fun pick(picked: Picked) = edit(send = true) { it.withPicked(picked, catalog.value.projects) }

    fun setBillable(billable: Boolean) = edit(send = true) { it.copy(billable = billable) }

    fun fill(fields: EntryFields) = edit(send = true) { fields }

    /** Waits until every field write queued here has ended, including ones that start meanwhile. */
    private suspend fun awaitWrites() {
        while (writes.isNotEmpty()) writes.toList().joinAll()
    }

    /** See [TimerViewModel.runningForEdit]. */
    suspend fun runningForEdit(): RunningEntryIdentity? {
        if (openingRunning) return null
        val shown = now() as? TimerState.Running ?: return null
        val pending = taps.value.pending
        if (pending is PendingTap.Stopping || pending is PendingTap.Acting) return null
        openingRunning = true
        try {
            val id = if (pending is PendingTap.Starting) {
                val startedId = pending.entryId.await() ?: return null
                taps.first { it.pending != pending }
                startedId
            } else {
                shown.entryId ?: return null
            }
            descriptionDone()
            awaitWrites()
            val settled = now() as? TimerState.Running ?: return null
            val identity = RunningEntryIdentity(
                id,
                shown.workspaceId,
                if (pending is PendingTap.Starting) settled.start else shown.start,
            )
            return identity.takeIf { changes.runningEntry(it) != null }
        } finally {
            openingRunning = false
        }
    }

    /**
     * Changes the idle timer's draft or the running entry's fields. While a tap the cache or the
     * server does not show yet is pending, the running entry's fields stay as they are: its id is
     * not known yet, or it is stopping.
     */
    private fun edit(send: Boolean, change: (EntryFields) -> EntryFields) {
        when (val shown = now()) {
            TimerState.Loading, is TimerState.Paused -> Unit

            is TimerState.Idle -> dispatch(TapEvent.Drafted(change(shown.fields)))

            is TimerState.Running -> {
                if (taps.value.pending != null || shown.entryId == null) return
                val fields = change(shown.fields)
                when {
                    fields == shown.fields -> Unit

                    send -> send(shown, fields)

                    else -> dispatch(
                        TapEvent.Edited(FieldEdit(shown.entryId, shown.workspaceId, shown.start, fields, sent = false)),
                    )
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
        val before = cached.value.controls()?.runningEntry()?.fields()
        val edit = FieldEdit(entryId, shown.workspaceId, shown.start, fields, sent = true)
        dispatch(TapEvent.Edited(edit))
        val write = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var refused = false
            try {
                val identity = RunningEntryIdentity(entryId, shown.workspaceId, shown.start)
                when (val written = localWrite { changes.updateRunning(identity, fields, shown.start) }) {
                    is LocalWrite.Written -> if (written.value) {
                        cached.settle { controls ->
                            val entry = controls.runningEntry()
                            entry == null || entry.start != shown.start ||
                                entry.fields().let { it == fields || it != before }
                        }
                    }

                    is LocalWrite.Failed -> refused = true
                }
            } finally {
                dispatch(if (refused) TapEvent.EditRefused(edit) else TapEvent.EditEnded(edit))
            }
        }
        writes += write
        write.invokeOnCompletion { writes -= write }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
