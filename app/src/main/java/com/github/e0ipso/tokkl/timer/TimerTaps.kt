package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningEntryIdentity
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.entry.entryLabels
import com.github.e0ipso.tokkl.entry.fields
import com.github.e0ipso.tokkl.picker.Picked
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CompletableDeferred

/** The controls, once read. */
internal sealed interface CachedControls {
    data object Unread : CachedControls

    data class Read(val controls: TimerControls, val editId: Long?, val resolvedEditId: Long?) : CachedControls
}

/** What the taps have done that the cache or the server may not show yet. */
internal data class TimerTaps(
    /** What Start starts with. */
    val draft: EntryFields = EntryFields(),
    val edit: FieldEdit? = null,
    val pending: PendingTap? = null,
)

/** [fields] for the running entry that started at [start], shown until the cache has them. [sent] once queued. */
internal data class FieldEdit(val entryId: Long, val workspaceId: Long, val start: Instant, val fields: EntryFields, val sent: Boolean)

/** An [edit] not queued yet for the active [identity], which a Continue or a favorite stops. */
internal data class TypedEdit(val identity: RunningEntryIdentity, val edit: FieldEdit)

/** A tap that the cache or the server may not show yet. */
internal sealed interface PendingTap {
    /** Start was tapped at [at], in whole milliseconds as the cache keeps times. */
    data class Starting(val at: Instant, val fields: EntryFields, val workspaceId: Long) : PendingTap {
        val entryId = CompletableDeferred<Long?>()
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
internal fun timerState(cached: CachedControls, defaultWorkspaceId: Long?, taps: TimerTaps, pauseOffered: Boolean): TimerState {
    if (cached !is CachedControls.Read) return TimerState.Loading
    val controls = cached.controls
    val idle = TimerState.Idle(taps.draft, EntryLabels(), defaultWorkspaceId, authoritative = controls == TimerControls.Idle && taps.pending == null)
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
internal fun TimerState.labelled(projects: List<CachedProject>, tasks: List<CachedTask>): TimerState = when (this) {
    TimerState.Loading -> this
    is TimerState.Idle -> copy(labels = entryLabels(fields, projects, tasks))
    is TimerState.Running -> copy(labels = entryLabels(fields, projects, tasks))
    is TimerState.Paused -> copy(labels = entryLabels(fields, projects, tasks))
}

/** Whether this edit is for [entry], also once the cache has remapped its local id as [read] says. */
internal fun FieldEdit.matches(entry: CachedTimeEntry, read: CachedControls.Read): Boolean {
    val remapped = entryId != entry.id && read.editId == entryId && read.resolvedEditId == entry.id
    return (entryId == entry.id || remapped) && workspaceId == entry.workspaceId &&
        (start == entry.start || (remapped && start.truncatedTo(ChronoUnit.SECONDS) == entry.start))
}

/** The running entry, or null while idle or paused. */
internal fun TimerControls.runningEntry(): CachedTimeEntry? = (this as? TimerControls.Running)?.entry
