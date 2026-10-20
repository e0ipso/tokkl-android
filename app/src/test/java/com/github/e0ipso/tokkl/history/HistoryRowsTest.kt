package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The web app's grouping of similar entries within a day, ported from similarityKey and rowsForDay
 * in ../tokkl/packages/web/src/components/EntryList.tsx. The web app has no unit test of them. Its
 * grouping cases live in ../tokkl/e2e/groups-bulk.spec.ts ("editing a group changes every member
 * and nothing else"), whose entries the first test copies, and in the comment above the refresh
 * test of ../tokkl/e2e/refresh-polling.spec.ts, which names the key's fields. Both at tokkl commit
 * 0031de3.
 */
class HistoryRowsTest {

    /** groups-bulk.spec.ts: three alike entries on one day are one group; the same on another day and a different one on the same day are rows of their own. */
    @Test
    fun alikeEntriesOfADayFormOneGroupAndOtherDaysAndDescriptionsStayApart() {
        val dayA = LocalDate.of(2026, 10, 18)
        val dayB = LocalDate.of(2026, 10, 17)
        val entries = listOf(
            cached(1, "Group work", at(dayA, 9), at(dayA, 10)),
            cached(2, "Group work", at(dayA, 10), at(dayA, 11)),
            cached(3, "Group work", at(dayA, 11), at(dayA, 12)),
            cached(4, "Group work", at(dayB, 9), at(dayB, 10)),
            cached(5, "Solo work", at(dayA, 12), at(dayA, 13)),
        )

        val days = days(entries, collapse = true)

        assertEquals(listOf(dayA, dayB), days.map { it.date })
        assertEquals(
            "the solo row first, as its entry is the day's newest, then the group, keyed by its oldest member",
            listOf(5L to listOf(5L), 1L to listOf(3L, 2L, 1L)),
            days[0].rows.map { it.key to it.entries.map(HistoryEntry::id) },
        )
        assertEquals(listOf(4L to listOf(4L)), days[1].rows.map { it.key to it.entries.map(HistoryEntry::id) })
        val group = days[0].rows[1]
        assertTrue(group.grouped)
        assertEquals("the members' total", 3 * 3600L, group.seconds)
        assertEquals("from the oldest member's start", at(dayA, 9), group.oldest.start)
        assertEquals("to the newest member's stop", at(dayA, 12), group.newest.stop)
    }

    /** refresh-polling.spec.ts: rows merge by description, project, task, tags and billable flag. */
    @Test
    fun eachFieldOfTheKeyKeepsEntriesApart() {
        val base = entry(1, "Work")
        val others = listOf(
            base.copy(id = 2, description = "Other work"),
            base.copy(id = 3, projectId = 9),
            base.copy(id = 4, taskId = 8),
            base.copy(id = 5, billable = true),
            base.copy(id = 6, tags = listOf("extra")),
        )
        for (other in others) {
            assertEquals(other.toString(), 2, rowsForDay(listOf(other, base), collapse = true).size)
        }
        assertEquals(1, rowsForDay(listOf(base.copy(id = 7), base), collapse = true).size)
    }

    @Test
    fun theStartTheStopAndTheProjectsNameDoNotCount() {
        val first = entry(1, "Work", project = "Website")
        val second = entry(2, "Work", project = "Renamed").copy(start = first.start.plusSeconds(7200), stop = first.stop.plusSeconds(9000))

        assertEquals(1, rowsForDay(listOf(second, first), collapse = true).size)
    }

    /** `[...entry.tags].sort().join(',')`: the order does not count, and a comma in a tag joins like two tags. */
    @Test
    fun tagsMatchInAnyOrderAndJoinWithCommasAsInJavaScript() {
        val a = entry(1, "Work").copy(tags = listOf("b", "a"))
        val b = entry(2, "Work").copy(tags = listOf("a", "b"))
        val c = entry(3, "Work").copy(tags = listOf("a,b"))

        assertEquals(listOf(listOf(1L, 2L, 3L)), rowsForDay(listOf(a, b, c), collapse = true).map { r -> r.entries.map { it.id }.sorted() })
        assertEquals("a\u0000\u0000\u0000false\u0000a,b", similarityKey(entry(4, "a").copy(tags = listOf("b", "a"))))
    }

    /** JavaScript's sort orders by UTF-16 code units: "Z" before "a", "é" after "z". */
    @Test
    fun tagsSortByUtf16CodeUnits() {
        assertEquals("x\u0000\u0000\u0000false\u0000Z,a,z,é", similarityKey(entry(1, "x").copy(tags = listOf("é", "z", "a", "Z"))))
    }

    /** null joins as an empty string, a number as its digits and a boolean as true or false. */
    @Test
    fun theKeyWritesFieldsAsJavaScriptJoinDoes() {
        assertEquals("Work\u000012\u000034\u0000true\u0000", similarityKey(entry(1, "Work").copy(projectId = 12, taskId = 34, billable = true)))
    }

    @Test
    fun withoutTheCollapsePreferenceEveryEntryHasItsOwnRow() {
        val day = LocalDate.of(2026, 10, 18)
        val entries = listOf(cached(1, "Group work", at(day, 9), at(day, 10)), cached(2, "Group work", at(day, 10), at(day, 11)))

        val rows = days(entries, collapse = false).single().rows

        assertEquals(listOf(2L, 1L), rows.map { it.key })
        assertFalse(rows.any { it.grouped })
    }

    private fun days(entries: List<CachedTimeEntry>, collapse: Boolean): List<HistoryDay> {
        val time = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null).time(CLOCK)!!
        return historyDays(entries.map { EntryWithSendState(it, unsent = false) }, emptyList(), emptyList(), emptyList(), time, collapse)
    }

    private fun at(day: LocalDate, hour: Int): Instant = day.atTime(hour, 0).toInstant(ZoneOffset.UTC)

    private fun cached(id: Long, description: String, start: Instant, stop: Instant) =
        CachedTimeEntry(id, 7, null, null, description, start, stop, false, emptyList(), emptyList())

    private fun entry(id: Long, description: String, project: String? = null) = HistoryEntry(
        id = id,
        description = description,
        project = project,
        projectColor = null,
        task = null,
        tags = emptyList(),
        billable = false,
        start = Instant.parse("2026-10-18T09:00:00Z"),
        stop = Instant.parse("2026-10-18T10:00:00Z"),
        unsent = false,
    )

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-18T18:00:00Z"), ZoneOffset.UTC)
    }
}
