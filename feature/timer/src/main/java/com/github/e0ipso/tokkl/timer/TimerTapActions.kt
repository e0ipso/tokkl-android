package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.QueueSender
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * The timer's Start, Stop, Pause and Resume taps (docs/PRD.md T1, T2, T5), which [TimerViewModel]
 * adapts for its screens.
 *
 * Start, Stop of a running entry, Continue and favorites go to [changes], the change queue, so
 * they never wait for the network (docs/PRD.md principle 2). From such a tap until the cache shows
 * it, [local] holds what the tap did and other taps are ignored, so a double tap starts one entry.
 * A start stops a cached running entry first, as the server does, so one entry runs at a time,
 * offline too. Pause, Resume and Stop while paused belong to the server's pause chain and go to
 * [pauseChain] at once, at the revision the timer was shown at, so a stale tap gets a refusal;
 * until the server's state shows, the timer keeps what it showed and ignores other taps. A
 * refusal or no answer goes to [notices].
 *
 * Call every tap on the main thread.
 *
 * @param local what the taps did that the cache or the server may not show yet, which changes
 *   only through the transitions in TimerTaps.kt.
 * @param cached the controls as the cache read them.
 * @param snapshot the state the taps act on.
 * @param settings for the default workspace a favorite starts in.
 * @param pauseChain the server's Pause, Resume and Discard, or null where the app sends none.
 * @param sender sends the queue now and waits for it, so a description typed before Pause
 *   reaches the server before the Pause does.
 * @param fieldEdits the running entry's field edits, which Pause sends first.
 * @param sections marks each Start and Stop tap in the system trace.
 * @param clock the time a Start tap shows until the cache has the queue's own tap time.
 * @param scope the view model's scope.
 */
internal class TimerTapActions(
    private val local: MutableStateFlow<TimerTaps>,
    private val cached: StateFlow<CachedControls>,
    private val snapshot: TimerSnapshot,
    private val settings: StateFlow<AccountSettings?>,
    private val changes: RunningEntryChanges,
    private val pauseChain: PauseActions?,
    private val sender: QueueSender,
    private val fieldEdits: RunningFieldEdits,
    private val notices: TimerNoticeBoard,
    private val sections: TapSections,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {

    private val queuedStarts = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One item for each timer start made here, Start, Resume, Continue or a favorite, once the
     * queue or the server has it, for the notification permission's question (docs/PRD.md N5).
     * The start never waits for a collector. The latest start waits for a screen collector after
     * a configuration change.
     */
    val starts: SharedFlow<Unit> = queuedStarts.asSharedFlow()

    /** Starts a timer with the idle timer's fields, unless one runs or no workspace is known yet. */
    fun start() {
        if (local.value.pending != null) return
        val shown = snapshot.now() as? TimerState.Idle ?: return
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
        if (local.value.pending != null) return
        val typed = when (val shown = snapshot.now()) {
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
        when (val shown = snapshot.now()) {
            TimerState.Loading -> Unit

            is TimerState.Idle -> shown.workspaceId?.let { startWith(it, fields, typed = null) }

            is TimerState.Running -> startWith(
                settings.value?.defaultWorkspaceId ?: shown.workspaceId,
                fields,
                typedFor(shown),
            )

            is TimerState.Paused -> startWith(
                settings.value?.defaultWorkspaceId ?: shown.workspaceId,
                fields,
                typed = null,
            )
        }
    }

    /** The description still being typed for the running entry [shown], not queued yet. */
    private fun typedFor(shown: TimerState.Running): TypedEdit? =
        local.value.editFor(shown, cached.value)?.takeIf { !it.sent }?.let { edit ->
            shown.entryId?.let { TypedEdit(RunningEntryIdentity(it, shown.workspaceId, shown.start), edit) }
        }

    /**
     * Starts a timer with [fields] in [workspaceId], after queuing the [typed] description of the
     * running entry, which the start then stops. The idle timer's draft makes way for the new entry,
     * and comes back when nothing starts.
     */
    private fun startWith(workspaceId: WorkspaceId, fields: EntryFields, typed: TypedEdit?) {
        val draft = local.value.draft
        val starting = PendingTap.Starting(clock.instant().truncatedTo(ChronoUnit.MILLIS), fields, workspaceId)
        local.update { it.startTapped(starting, typed = typed != null) }
        // Undispatched, so the queue reads the tap time within the tap's handler.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var queued = false
            try {
                if (typed != null) changes.updateRunning(typed.identity, typed.edit.fields, typed.identity.start)
                starting.entryId.complete(changes.start(workspaceId, starting.fields))
                queued = true
                queuedStarts.tryEmit(Unit)
                cached.settle { it.runningEntry()?.start?.isBefore(starting.at) == false }
            } finally {
                starting.entryId.complete(null)
                local.update { it.startEnded(starting, queued, draft) }
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
        when (val shown = snapshot.now()) {
            is TimerState.Running -> stopRunning(shown)
            is TimerState.Paused -> discard()
            else -> Unit
        }
    }

    private fun stopRunning(shown: TimerState.Running) {
        val entryId = shown.entryId ?: return
        val edited = local.value.editFor(shown, cached.value)?.fields
        sections.begin(TapTrace.STOP) { it is TimerState.Idle }
        val stopping = PendingTap.Stopping(shown.start)
        local.update { it.stopTapped(stopping) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.stop(entryId, edited)) cached.settle { it.runningEntry()?.start != shown.start }
            } finally {
                local.update { it.tapEnded(stopping) }
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
        if (local.value.pending != null) return
        val shown = snapshot.now() as? TimerState.Running ?: return
        val running = cached.value.controls() as? TimerControls.Running ?: return
        if (!running.pausable || shown.entryId != running.entry.id) return
        val typed = local.value.editFor(shown, cached.value)?.takeIf { !it.sent }
        if (typed != null) fieldEdits.send(shown, typed.fields)
        act(running.target, done = { it.target != running.target }) {
            if (typed != null) {
                fieldEdits.awaitWrites()
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
        if (local.value.pending != null) return
        if (snapshot.now() !is TimerState.Paused) return
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
        local.update { it.actionTapped(acting) }
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
                local.update { it.tapEnded(acting) }
            }
        }
    }

    /** A focused control keeps the durable target that supplied its displayed context. */
    fun isShown(target: TimerTarget): Boolean = local.value.pending == null && cached.value.controls()?.target == target
}
