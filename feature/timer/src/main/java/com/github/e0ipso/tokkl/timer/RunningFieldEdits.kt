package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.entryfields.withPicked
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * The timer's field edits, for [TimerViewModel]: the idle timer's draft and the running entry's
 * fields. A pick of project, task or tags, billable and a picked suggestion go to [changes] at
 * once. A description goes once typing ends, so the queue does not get a change for every letter.
 * Until the cache has an edit, the timer shows it over the cache's entry. A paused timer's fields
 * are read-only.
 */
internal class RunningFieldEdits(
    private val taps: TimerTapState,
    private val changes: EntryChanges,
    private val projects: () -> List<Project>,
) {
    private val writes = mutableSetOf<Job>()
    private var openingRunning = false

    fun editDescription(description: String) = edit(send = false) { it.copy(description = description) }

    fun descriptionDone() {
        if (taps.local.value.pending != null) return
        val shown = taps.shown() as? TimerState.Running ?: return
        val edit = taps.editFor(shown)?.takeIf { !it.sent } ?: return
        if (edit.fields == taps.running()?.fields()) {
            taps.local.update { if (it.edit == edit) it.copy(edit = null) else it }
        } else {
            send(shown, edit.fields)
        }
    }

    fun pick(picked: Picked) = edit(send = true) { it.withPicked(picked, projects()) }

    fun setBillable(billable: Boolean) = edit(send = true) { it.copy(billable = billable) }

    fun fill(fields: EntryFields) = edit(send = true) { fields }

    /** Waits until every field write queued here has ended, including ones that start meanwhile. */
    suspend fun awaitWrites() {
        while (writes.isNotEmpty()) writes.toList().joinAll()
    }

    /** See [TimerViewModel.runningForEdit]. */
    suspend fun runningForEdit(): RunningEntryIdentity? {
        if (openingRunning) return null
        val shown = taps.shown() as? TimerState.Running ?: return null
        val pending = taps.local.value.pending
        if (pending is PendingTap.Stopping || pending is PendingTap.Acting) return null
        openingRunning = true
        try {
            val id = if (pending is PendingTap.Starting) {
                val startedId = pending.entryId.await() ?: return null
                taps.local.first { it.pending != pending }
                startedId
            } else {
                shown.entryId ?: return null
            }
            descriptionDone()
            awaitWrites()
            val settled = taps.shown() as? TimerState.Running ?: return null
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
        when (val shown = taps.shown()) {
            TimerState.Loading, is TimerState.Paused -> Unit

            is TimerState.Idle -> taps.local.update { it.copy(draft = change(it.draft)) }

            is TimerState.Running -> {
                if (taps.local.value.pending != null || shown.entryId == null) return
                val fields = change(shown.fields)
                when {
                    fields == shown.fields -> Unit

                    send -> send(shown, fields)

                    else -> taps.local.update {
                        it.copy(
                            edit = FieldEdit(shown.entryId, shown.workspaceId, shown.start, fields, sent = false),
                        )
                    }
                }
            }
        }
    }

    /**
     * Queues [fields] for the running entry, and shows them until the cache has them, or has
     * anything newer than what it had before.
     */
    fun send(shown: TimerState.Running, fields: EntryFields) {
        val entryId = shown.entryId ?: return
        val before = taps.running()?.fields()
        val edit = FieldEdit(entryId, shown.workspaceId, shown.start, fields, sent = true)
        taps.local.update { it.copy(edit = edit) }
        val write = taps.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (changes.updateRunning(
                        RunningEntryIdentity(entryId, shown.workspaceId, shown.start),
                        fields,
                        shown.start,
                    )
                ) {
                    taps.settle { controls ->
                        val entry = controls.runningEntry()
                        entry == null || entry.start != shown.start ||
                            entry.fields().let { it == fields || it != before }
                    }
                }
            } finally {
                taps.local.update { if (it.edit == edit) it.copy(edit = null) else it }
            }
        }
        writes += write
        write.invokeOnCompletion { writes -= write }
    }
}
