package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.LocalWrite
import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.cache.localWrite
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.target
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * The timer's commands (docs/PRD.md T1, T2, T5): Start, Continue, a favorite, Stop, Pause and
 * Resume, in the order they may go, which [TimerViewModel] adapts for its screens. It is the one
 * owner of [taps], what the taps did that the cache or the server may not show yet, and changes it
 * only by dispatching a [TapEvent] through [reduce]. A tap decides from [now], dispatches its event,
 * and launches its effect, a queue write or a server action, which dispatches the event that ends
 * it. What the screens show, [state], is the projection [timerState] of [taps] over the cached
 * controls. The field edits are [edits], which read and change the taps through the same events.
 *
 * Start, Stop of a running entry, Continue and favorites go to [changes], the change queue, so they
 * never wait for the network (docs/PRD.md principle 2). From such a tap until the cache shows it,
 * other taps are ignored, so a double tap starts one entry. A start stops a cached running entry
 * first, as the server does, so one entry runs at a time, offline too. Pause, Resume and Stop while
 * paused belong to the server's pause chain and go to [pauseChain] at once, at the revision the
 * timer was shown at, so a stale tap gets a refusal; until the server's state shows, the timer
 * keeps what it showed and ignores other taps. A refusal or no answer goes to [notices].
 *
 * A queue write the phone's storage refuses ([LocalWrite.Failed]) gives the timer back as it was
 * before the tap, as the entry form gives its form back: Start brings its draft back and Stop
 * leaves the entry running, with the edit it took.
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
internal class TimerCommands(
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
            val editId = taps.value.edit?.target?.entryId
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

    /** The taps as the field edits read and change them. */
    private val ledger = object : TapLedger {
        override val taps get() = this@TimerCommands.taps
        override val cached get() = this@TimerCommands.cached
        override val catalog get() = this@TimerCommands.catalog

        override fun now() = this@TimerCommands.now()

        override fun dispatch(event: TapEvent) = this@TimerCommands.dispatch(event)
    }

    /** The idle timer's draft and the running entry's fields, and the editor's opening. */
    val edits = RunningFieldEdits(ledger, changes, scope)

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
        if (typed != null) edits.send(shown, typed.fields)
        act(running.target, done = { it.target != running.target }) {
            if (typed != null) {
                edits.awaitWrites()
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
