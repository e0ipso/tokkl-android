package com.github.e0ipso.tokkl.history

// The web app's grouping of similar entries within a day (docs/PRD.md H4), ported from
// similarityKey and rowsForDay in ../tokkl/packages/web/src/components/EntryList.tsx, at tokkl
// commit 0031de3. Plain Kotlin, so JVM tests cover it.

/**
 * One line of a day in the history: a single entry, or a group of the day's similar entries,
 * which shows its count and its total and expands to its members.
 *
 * @property key the oldest member's id, the smallest, as EntryList.tsx keys a row, so a group keeps
 *   its key when a newer entry joins it.
 * @property entries the members, newest first, as the day lists them.
 */
data class HistoryRow(val key: Long, val entries: List<HistoryEntry>) {
    val grouped: Boolean get() = entries.size > 1

    /** The newest member, whose fields the row shows, `row.entries[0]` in EntryList.tsx. */
    val newest: HistoryEntry get() = entries.first()

    /** The oldest member, whose start begins the row's time range. */
    val oldest: HistoryEntry get() = entries.last()

    /** The members' whole seconds, summed as the web app sums entrySeconds. */
    val seconds: Long get() = entries.sumOf { it.seconds }
}

/**
 * The text two entries share when the web app groups them: the description, project, task,
 * billable flag and tags in sorted order, joined as similarityKey joins them. JavaScript's
 * Array.join writes a null as an empty string and Boolean as true or false, and the tags join
 * with commas, so a tag named "a,b" matches the tags "a" and "b", as on the web app.
 */
fun similarityKey(entry: HistoryEntry): String =
    listOf(
        entry.description,
        entry.projectId?.toString().orEmpty(),
        entry.taskId?.toString().orEmpty(),
        entry.billable.toString(),
        // JavaScript's sort() orders strings by UTF-16 code units, as String.compareTo does.
        entry.tags.sorted().joinToString(","),
    ).joinToString("\u0000")

/**
 * The rows of one day's [entries], newest first: with [collapse], the account's
 * `collapseTimeEntries` preference, entries with the same [similarityKey] share a row, placed
 * where its newest member falls; without it, each entry has its own.
 */
fun rowsForDay(entries: List<HistoryEntry>, collapse: Boolean): List<HistoryRow> {
    if (!collapse) return entries.map { HistoryRow(it.id, listOf(it)) }
    val groups = LinkedHashMap<String, MutableList<HistoryEntry>>()
    for (entry in entries) groups.getOrPut(similarityKey(entry)) { mutableListOf() } += entry
    return groups.values.map { members -> HistoryRow(members.minOf { it.id }, members) }
}
