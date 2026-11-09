package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry

/** Deleted IDs remain suppressed across stale requests; restored snapshots bridge cache pruning. */
fun reconcileEntryRows(
    cached: List<EntryWithSendState>,
    loaded: List<TimeEntry>,
    overrides: Map<EntryId, TimeEntry?>,
): Pair<List<EntryWithSendState>, List<TimeEntry>> {
    val deleted = overrides.filterValues { it == null }.keys
    val current = cached.filter { it.entry.id !in deleted }
    val currentIds = current.mapTo(HashSet()) { it.entry.id }
    val restored = overrides.values.filterNotNull()
    val replaced = overrides.keys + restored.map { it.id }
    val server = loaded.filter { it.id !in replaced } + restored.filter { it.id !in currentIds }
    return current to server
}

/** Remaps both the tombstone and its restored fallback after a local create reaches the server. */
suspend fun resolveEntryOverrides(overrides: Map<EntryId, TimeEntry?>, ids: EntryChanges): Map<EntryId, TimeEntry?> =
    overrides.map { (id, entry) -> ids.currentId(id) to entry?.let { it.copy(id = ids.currentId(it.id)) } }.toMap()
