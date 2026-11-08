package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.entryfields.EntryLabels
import com.github.e0ipso.tokkl.entryfields.entryLabels
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.RunningEntryIdentity
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.model.fields
import com.github.e0ipso.tokkl.picker.Picked
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The controls, once read. */
internal sealed interface CachedControls {
    data object Unread : CachedControls

    data class Read(val controls: TimerControls, val editId: EntryId?, val resolvedEditId: EntryId?) : CachedControls
}

/**
 * What the taps have done that the cache or the server may not show yet. It changes only through
 * the transitions below, one for each tap and for each end of one.
 */
internal data class TimerTaps(
    /** What Start starts with. */
    val draft: EntryFields = EntryFields(),
    val edit: FieldEdit? = null,
    val pending: PendingTap? = null,
) {
    /**
     * A Start tap: [tap] waits for the queue, and the draft makes way for the new entry. A [typed]
     * edit goes with it.
     */
    fun startTapped(tap: PendingTap.Starting, typed: Boolean) =
        copy(pending = tap, draft = EntryFields(), edit = if (typed) null else edit)

    /**
     * The Start [tap] ended, unless a newer tap replaced it. When it [queued] nothing and no new draft
     * was typed meanwhile, the [draft] it cleared comes back.
     */
    fun startEnded(tap: PendingTap.Starting, queued: Boolean, draft: EntryFields) = when {
        pending != tap -> this
        !queued && this.draft == EntryFields() -> copy(pending = null, draft = draft)
        else -> copy(pending = null)
    }

    /** A Stop tap: [tap] waits for the queue, and the edit it sends with it no longer shows. */
    fun stopTapped(tap: PendingTap.Stopping) = copy(pending = tap, edit = null)

    /** A Pause, Resume or Stop while paused: [tap] waits for the server. */
    fun actionTapped(tap: PendingTap.Acting) = copy(pending = tap)

    /** [tap] ended, unless a newer tap replaced it. */
    fun tapEnded(tap: PendingTap) = if (pending == tap) copy(pending = null) else this

    /** The idle timer's draft changed with [change]. */
    fun drafted(change: (EntryFields) -> EntryFields) = copy(draft = change(draft))

    /** [edit] shows over the running entry. */
    fun editing(edit: FieldEdit) = copy(edit = edit)

    /** [edit] no longer shows, unless a newer edit replaced it: the cache has it, or it gave way. */
    fun editEnded(edit: FieldEdit) = if (this.edit == edit) copy(edit = null) else this

    /** The edit shown over the running entry [shown], while it still matches the [cached] entry. */
    fun editFor(shown: TimerState.Running, cached: CachedControls): FieldEdit? {
        val read = cached as? CachedControls.Read ?: return null
        val entry = read.controls.runningEntry()?.takeIf { it.id == shown.entryId } ?: return null
        return edit?.takeIf { it.matches(entry, read) }
    }
}

/** [fields] for the running entry that started at [start], shown until the cache has them. [sent] once queued. */
internal data class FieldEdit(
    val entryId: EntryId,
    val workspaceId: WorkspaceId,
    val start: Instant,
    val fields: EntryFields,
    val sent: Boolean,
)

/** An [edit] not queued yet for the active [identity], which a Continue or a favorite stops. */
internal data class TypedEdit(val identity: RunningEntryIdentity, val edit: FieldEdit)

/** A tap that the cache or the server may not show yet. */
internal sealed interface PendingTap {
    /** Start was tapped at [at], in whole milliseconds as the cache keeps times. */
    data class Starting(val at: Instant, val fields: EntryFields, val workspaceId: WorkspaceId) : PendingTap {
        val entryId = CompletableDeferred<EntryId?>()
    }

    /** Stop was tapped for the entry that started at [start]. */
    data class Stopping(val start: Instant) : PendingTap

    /** Pause, Resume or Stop while paused went to the server for what [target] showed. */
    data class Acting(val target: TimerTarget) : PendingTap
}

/**
 * What the timer shows for the [cached] controls and what the [taps] did that the controls may not
 * show yet, without labels.
 */
internal fun timerState(
    cached: CachedControls,
    defaultWorkspaceId: WorkspaceId?,
    taps: TimerTaps,
    pauseOffered: Boolean,
): TimerState {
    if (cached !is CachedControls.Read) return TimerState.Loading
    val controls = cached.controls
    val idle = TimerState.Idle(
        taps.draft,
        EntryLabels(),
        defaultWorkspaceId,
        authoritative = controls == TimerControls.Idle && taps.pending == null,
    )
    when (val pending = taps.pending) {
        // The cache's entry shows once the start has settled, with an id that Stop can take.
        is PendingTap.Starting -> return TimerState.Running(
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

        is PendingTap.Stopping -> if (controls.runningEntry()?.start == pending.start) return idle

        // The timer shows what it showed until the server's answer reaches the controls.
        is PendingTap.Acting, null -> Unit
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

/** This state with the labels of its project, task and tags, named from [projects] and [tasks]. */
internal fun TimerState.labelled(projects: List<Project>, tasks: List<Task>): TimerState = when (this) {
    TimerState.Loading -> this
    is TimerState.Idle -> copy(labels = entryLabels(fields, projects, tasks))
    is TimerState.Running -> copy(labels = entryLabels(fields, projects, tasks))
    is TimerState.Paused -> copy(labels = entryLabels(fields, projects, tasks))
}

/** Whether this edit is for [entry], also once the cache has remapped its local id as [read] says. */
internal fun FieldEdit.matches(entry: TimeEntry, read: CachedControls.Read): Boolean {
    val remapped = entryId != entry.id && read.editId == entryId && read.resolvedEditId == entry.id
    return (entryId == entry.id || remapped) && workspaceId == entry.workspaceId &&
        (start == entry.start || (remapped && start.truncatedTo(ChronoUnit.SECONDS) == entry.start))
}

/** The running entry, or null while idle or paused. */
internal fun TimerControls.runningEntry(): TimeEntry? = (this as? TimerControls.Running)?.entry

/** The controls, or null while unread. */
internal fun CachedControls.controls(): TimerControls? = (this as? CachedControls.Read)?.controls

/**
 * Waits until the controls pass [done], or gives up after [SETTLE_MS]: far longer than a write to
 * the phone's database or a server's answer reaching the cache takes, so only a cache that never
 * shows the tap reaches it.
 */
internal suspend fun StateFlow<CachedControls>.settle(done: (TimerControls) -> Boolean) {
    withTimeoutOrNull(SETTLE_MS) {
        first { it is CachedControls.Read && done(it.controls) }
    }
}

private const val SETTLE_MS = 5_000L
