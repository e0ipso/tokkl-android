package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.entry.fields

// Continue (docs/PRD.md T2), as continueEntry and the "c" shortcut in
// ../tokkl/packages/web/src/pages/TimerPage.tsx at tokkl commit 0031de3: a new running entry with
// the source's description, project, task, tags and billable.

/**
 * The entry that continue-last continues: the stopped entry that started last, as the web app's
 * "c" shortcut takes the first stopped one of its newest-first list. Tied starts prefer local
 * creates over server entries, then the newest create: smaller negative queue IDs or larger
 * server IDs. Null when none is stopped.
 */
fun lastStopped(entries: List<CachedTimeEntry>): CachedTimeEntry? =
    entries.filter { it.stop != null }.maxWithOrNull(stoppedEntryOrder)

private val stoppedEntryOrder = compareBy<CachedTimeEntry> { it.start }
    .thenBy { it.id < 0 }
    .thenComparator { a, b ->
        // Local IDs are negative queue sequences, so smaller means newer.
        if (a.id < 0) b.id.compareTo(a.id) else a.id.compareTo(b.id)
    }

/**
 * Starts a timer with [source]'s description, project, task, tags and billable, in [source]'s
 * workspace, where its project and tags are. It goes through [EntryChanges.start], so it is on the
 * phone at once, works offline, and stops a running entry at the new one's start. Returns the new
 * entry's local id.
 */
suspend fun EntryChanges.continueEntry(source: CachedTimeEntry): Long = start(source.workspaceId, source.fields())

/**
 * Continues the entry [lastStopped] picks among [entries], such as the cache's entries, for a
 * surface without the history, such as the Quick Settings tile (docs/PRD.md N4). Returns the new
 * entry's local id, or null when no entry is stopped.
 */
suspend fun EntryChanges.continueLast(entries: List<CachedTimeEntry>): Long? = lastStopped(entries)?.let { continueEntry(it) }
