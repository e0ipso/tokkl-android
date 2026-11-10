package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.PendingEdit
import com.github.e0ipso.tokkl.cache.afterWrite
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

internal sealed interface CachedControls {
    data object Unread : CachedControls

    data class Read(val controls: TimerControls, val editId: EntryId?, val resolvedEditId: EntryId?) : CachedControls
}

/**
 * What the taps have done that the cache or the server may not show yet. Only [reduce] makes a
 * new one, for a [TapEvent].
 */
internal data class TimerTaps(
    /** What Start starts with. */
    val draft: EntryFields = EntryFields(),
    val edit: FieldEdit? = null,
    val pending: PendingTap? = null,
) {
    /** The edit shown over the running entry [shown], while it still matches the [cached] entry. */
    fun editFor(shown: TimerState.Running, cached: CachedControls): FieldEdit? {
        val read = cached as? CachedControls.Read ?: return null
        val entry = read.controls.runningEntry()?.takeIf { it.id == shown.entryId } ?: return null
        return edit?.takeIf { it.matches(entry, read) }
    }

    /** The description still being typed for the running entry [shown], not queued yet. */
    fun typedFor(shown: TimerState.Running, cached: CachedControls): TypedEdit? =
        editFor(shown, cached)?.takeIf { !it.sent }?.let { edit ->
            shown.entryId?.let { TypedEdit(RunningEntryIdentity(it, shown.workspaceId, shown.start), edit) }
        }
}

/** What happened to the taps: a tap, or the end of the effect one started. */
internal sealed interface TapEvent {
    /** Start was tapped: [tap] waits for the queue. A [typed] edit goes with it. */
    data class StartTapped(val tap: PendingTap.Starting, val typed: Boolean) : TapEvent

    /**
     * The Start [tap] ended. It [queued] the entry or not, and [draft] is the draft it cleared and
     * [typed] the edit it took with it, which both come back when it queued nothing.
     */
    data class StartEnded(
        val tap: PendingTap.Starting,
        val queued: Boolean,
        val draft: EntryFields,
        val typed: FieldEdit? = null,
    ) : TapEvent

    data class StopTapped(val tap: PendingTap.Stopping) : TapEvent

    /** Pause, Resume or Stop while paused was tapped: [tap] waits for the server. */
    data class ActionTapped(val tap: PendingTap.Acting) : TapEvent

    /** The Stop or pause chain [tap] ended. A Stop the storage refused brings back the edit it took, [restored]. */
    data class TapEnded(val tap: PendingTap, val restored: FieldEdit? = null) : TapEvent

    data class Drafted(val draft: EntryFields) : TapEvent

    data class Edited(val edit: FieldEdit) : TapEvent

    /**
     * The write of [edit] ended: it reached the cache or gave way, or the storage [refused] to
     * queue it, and it stays as typed, not sent.
     */
    data class EditWritten(val edit: FieldEdit, val refused: Boolean) : TapEvent
}

/**
 * The taps after [event]. A tap that ends changes nothing once a newer one replaced it, and an
 * edit that ends nothing once a newer edit replaced it.
 */
internal fun TimerTaps.reduce(event: TapEvent): TimerTaps = when (event) {
    // The draft makes way for the new entry, and a typed edit goes with the start.
    is TapEvent.StartTapped -> copy(pending = event.tap, draft = EntryFields(), edit = if (event.typed) null else edit)

    // A start that queued nothing brings its draft and its typed edit back, unless new ones came meanwhile.
    is TapEvent.StartEnded -> when {
        pending != event.tap -> this

        event.queued -> copy(pending = null)

        else -> copy(
            pending = null,
            draft = if (draft == EntryFields()) event.draft else draft,
            edit = edit ?: event.typed,
        )
    }

    // The edit a Stop sends with it no longer shows.
    is TapEvent.StopTapped -> copy(pending = event.tap, edit = null)

    is TapEvent.ActionTapped -> copy(pending = event.tap)

    is TapEvent.TapEnded -> if (pending == event.tap) copy(pending = null, edit = edit ?: event.restored) else this

    is TapEvent.Drafted -> copy(draft = event.draft)

    is TapEvent.Edited -> copy(edit = event.edit)

    is TapEvent.EditWritten -> copy(edit = edit.afterWrite(event.edit, event.refused))
}

/** Fields for the running entry with the identity it was shown with, shown until the cache has them. */
internal typealias FieldEdit = PendingEdit<RunningEntryIdentity>

/** An [edit] not queued yet for the active [identity], which a Continue or a favorite stops. */
internal data class TypedEdit(val identity: RunningEntryIdentity, val edit: FieldEdit)

/**
 * A tap that the cache or the server may not show yet. Each is equal only to itself: two taps with
 * the same time and fields are still two taps, and the end of one never ends the other.
 */
internal sealed interface PendingTap {
    /** Start was tapped at [at], in whole milliseconds as the cache keeps times. */
    class Starting(val at: Instant, val fields: EntryFields, val workspaceId: WorkspaceId) : PendingTap {
        val entryId = CompletableDeferred<EntryId?>()
    }

    /** Stop was tapped for the entry that started at [start]. */
    class Stopping(val start: Instant) : PendingTap

    /** Pause, Resume or Stop while paused went to the server for what [target] showed. */
    class Acting(val target: TimerTarget) : PendingTap
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

internal fun TimerState.labelled(projects: List<Project>, tasks: List<Task>): TimerState = when (this) {
    TimerState.Loading -> this
    is TimerState.Idle -> copy(labels = entryLabels(fields, projects, tasks))
    is TimerState.Running -> copy(labels = entryLabels(fields, projects, tasks))
    is TimerState.Paused -> copy(labels = entryLabels(fields, projects, tasks))
}

/** Whether this edit is for [entry], also once the cache has remapped its local id as [read] says. */
internal fun FieldEdit.matches(entry: TimeEntry, read: CachedControls.Read): Boolean {
    val (entryId, workspaceId, start) = target
    val remapped = entryId != entry.id && read.editId == entryId && read.resolvedEditId == entry.id
    return (entryId == entry.id || remapped) && workspaceId == entry.workspaceId &&
        (start == entry.start || (remapped && start.truncatedTo(ChronoUnit.SECONDS) == entry.start))
}

internal fun TimerControls.runningEntry(): TimeEntry? = (this as? TimerControls.Running)?.entry

internal fun CachedControls.controls(): TimerControls? = (this as? CachedControls.Read)?.controls

internal data class Catalog(val projects: List<Project> = emptyList(), val tasks: List<Task> = emptyList())

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
