package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.cache.RunningEntryEdits
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The taps as [RunningFieldEdits] reads and changes them. [TimerCommands] owns them and changes
 * them only through [dispatch], which runs the [TapEvent] through [reduce].
 */
internal interface TapLedger {

    /** What the taps did that the cache or the server may not show yet. */
    val taps: StateFlow<TimerTaps>

    /** The timer controls as the cache gives them, with the id the cache holds an edit's entry under. */
    val cached: StateFlow<CachedControls>

    /** The cached projects and tasks. */
    val catalog: StateFlow<Catalog>

    /** The timer state as of now, without labels. */
    fun now(): TimerState

    fun dispatch(event: TapEvent)
}

/**
 * The timer's field edits (docs/PRD.md T1): the idle timer's draft, which Start takes, and the
 * running entry's fields, until the cache has them. A pick of project, task or tags, billable and a
 * picked suggestion go to the queue at once. A description goes once typing ends, so the queue does
 * not get a change for every letter. Until the cache has an edit, the timer shows it over the
 * cache's entry, as [ledger] keeps it. A paused timer's fields are read-only.
 *
 * Each write goes through [RunningEntryEdits], which the Pomodoro page's edits share. A write the
 * phone's storage refuses leaves the edit on the timer as typed, not sent, for the next send to take.
 *
 * Call everything on the main thread.
 *
 * @param ledger what the taps did, which [TimerCommands] owns.
 * @param changes the change queue, which the running entry's fields go to.
 * @param scope the view model's scope, where each write runs.
 */
internal class RunningFieldEdits(
    private val ledger: TapLedger,
    private val changes: RunningEntryChanges,
    private val scope: CoroutineScope,
) {

    /** The running entry's field writes on their way to the cache. */
    private val writes = RunningEntryEdits(changes)
    private var openingRunning = false

    fun editDescription(description: String) = edit(send = false) { it.copy(description = description) }

    /** Sends a description typed for the running entry when it differs from the cache's. */
    fun descriptionDone() {
        if (ledger.taps.value.pending != null) return
        val shown = ledger.now() as? TimerState.Running ?: return
        val cached = ledger.cached.value
        val edit = ledger.taps.value.editFor(shown, cached)?.takeIf { !it.sent } ?: return
        if (edit.fields == cached.controls()?.runningEntry()?.fields()) {
            ledger.dispatch(TapEvent.EditWritten(edit, refused = false))
        } else {
            send(shown, edit.fields)
        }
    }

    fun pick(picked: Picked) = edit(send = true) { it.withPicked(picked, ledger.catalog.value.projects) }

    fun setBillable(billable: Boolean) = edit(send = true) { it.copy(billable = billable) }

    fun fill(fields: EntryFields) = edit(send = true) { fields }

    /** Waits until every field write started here has reached the queue, including ones that start meanwhile. */
    suspend fun awaitWrites() {
        writes.awaitQueued()
    }

    /** See [TimerViewModel.runningForEdit]. */
    suspend fun runningForEdit(): RunningEntryIdentity? {
        if (openingRunning) return null
        val shown = ledger.now() as? TimerState.Running ?: return null
        val pending = ledger.taps.value.pending
        if (pending is PendingTap.Stopping || pending is PendingTap.Acting) return null
        openingRunning = true
        try {
            val id = if (pending is PendingTap.Starting) {
                val startedId = pending.entryId.await() ?: return null
                ledger.taps.first { it.pending != pending }
                startedId
            } else {
                shown.entryId ?: return null
            }
            descriptionDone()
            awaitWrites()
            val settled = ledger.now() as? TimerState.Running ?: return null
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
     * Queues [fields] for the running entry, and shows them until the cache has them, or has
     * anything newer than what it had before.
     */
    fun send(shown: TimerState.Running, fields: EntryFields) {
        val entryId = shown.entryId ?: return
        val before = ledger.cached.value.controls()?.runningEntry()?.fields()
        val edit = FieldEdit(RunningEntryIdentity(entryId, shown.workspaceId, shown.start), fields, sent = true)
        ledger.dispatch(TapEvent.Edited(edit))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var refused = false
            try {
                val running = ledger.cached.filterIsInstance<CachedControls.Read>().map { it.controls.runningEntry() }
                refused = writes.write(edit.target, fields, before, running)
            } finally {
                ledger.dispatch(TapEvent.EditWritten(edit, refused))
            }
        }
    }

    /**
     * Changes the idle timer's draft or the running entry's fields. While a tap the cache or the
     * server does not show yet is pending, the running entry's fields stay as they are: its id is
     * not known yet, or it is stopping.
     */
    private fun edit(send: Boolean, change: (EntryFields) -> EntryFields) {
        when (val shown = ledger.now()) {
            TimerState.Loading, is TimerState.Paused -> Unit

            is TimerState.Idle -> ledger.dispatch(TapEvent.Drafted(change(shown.fields)))

            is TimerState.Running -> {
                if (ledger.taps.value.pending != null || shown.entryId == null) return
                val fields = change(shown.fields)
                when {
                    fields == shown.fields -> Unit

                    send -> send(shown, fields)

                    else -> ledger.dispatch(
                        TapEvent.Edited(
                            FieldEdit(RunningEntryIdentity(shown.entryId, shown.workspaceId, shown.start), fields, false),
                        ),
                    )
                }
            }
        }
    }
}
