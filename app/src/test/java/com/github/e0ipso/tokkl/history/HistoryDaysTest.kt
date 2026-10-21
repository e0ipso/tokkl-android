package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.time.AccountTime
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How historyDays groups entries into days in the account's timezone, Madrid here. */
class HistoryDaysTest {

    @Test
    fun entriesGoUnderTheirAccountDayNewestFirstWithDayTotalsAndTheRunningOneStaysOut() {
        val days = historyDays(
            cached = listOf(
                // 00:30 on the 15th in Madrid, though the 14th in UTC.
                sent(entry(1, "2026-10-14T22:30:00Z", "2026-10-14T23:00:00Z")),
                // 23:00 on the 14th in Madrid.
                sent(entry(2, "2026-10-14T21:00:00Z", "2026-10-14T21:45:30.900Z")),
                sent(entry(3, "2026-10-15T08:00:00Z", stop = null)),
                sent(entry(4, "2026-10-15T06:00:00Z", "2026-10-15T07:00:00Z", project = 9, task = 90)),
            ),
            older = emptyList(),
            projects = listOf(CachedProject(9, 1, null, "Tokkl for Android", "#0b83d9", active = true, billable = false)),
            tasks = listOf(CachedTask(90, 1, 9, "Reports", active = true)),
            time = time(resolution = 15.0),
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14)), days.map { it.date })
        assertEquals("newest first, and no running entry", listOf(4L, 1L), days[0].entries.map { it.id })
        assertEquals(3600L + 1800L, days[0].totalSeconds)
        assertEquals("whole seconds, as the web app's entrySeconds", 2730L, days[1].totalSeconds)
        val named = days[0].entries[0]
        assertEquals("Tokkl for Android", named.project)
        assertEquals("#0b83d9", named.projectColor)
        assertEquals("Reports", named.task)
    }

    /** The server snaps stopped entries itself, so the phone shows what it sent, snapped or not. */
    @Test
    fun anEntryTheServerHasShowsTheServersTimes() {
        val raw = entry(2, "2026-10-14T21:00:07Z", "2026-10-14T21:45:30.900Z")

        val shown = historyDays(listOf(sent(raw)), emptyList(), emptyList(), emptyList(), time(resolution = 15.0))
            .single().entries.single()

        assertEquals(raw.start, shown.start)
        assertEquals(raw.stop, shown.stop)
        assertFalse(shown.unsent)
    }

    /** An unsent entry shows the times the server will store, so they do not move when it answers. */
    @Test
    fun anUnsentEntryShowsTheTimesTheServerWillStore() {
        val raw = entry(5, "2026-10-15T07:07:31.500Z", "2026-10-15T08:01:02.250Z")
        val snapped = time(resolution = 15.0).snapUnsent(raw.start, raw.stop!!)!!

        val shown = historyDays(listOf(unsent(raw)), emptyList(), emptyList(), emptyList(), time(resolution = 15.0))
            .single().entries.single()

        assertEquals(Instant.parse("2026-10-15T07:15:00Z"), snapped.start)
        assertEquals(snapped.start, shown.start)
        assertEquals(snapped.stop, shown.stop)
        assertTrue(shown.unsent)

        val withoutResolution = historyDays(listOf(unsent(raw)), emptyList(), emptyList(), emptyList(), time(resolution = null))
            .single().entries.single()
        assertEquals(raw.start, withoutResolution.start)
        assertEquals(raw.stop, withoutResolution.stop)
    }

    @Test
    fun theSnappedStartDecidesTheDayOfAnUnsentEntry() {
        // 23:53 in Madrid snaps to midnight, the next day, with 15 minutes.
        val raw = entry(6, "2026-10-14T21:53:00Z", "2026-10-14T22:20:00Z")

        val days = historyDays(listOf(unsent(raw)), emptyList(), emptyList(), emptyList(), time(resolution = 15.0))

        assertEquals(LocalDate.of(2026, 10, 15), days.single().date)
    }

    @Test
    fun anEntryInTheCacheShowsOnceAsCachedAndOneInTwoPagesAsTheLaterPageHasIt() {
        val days = historyDays(
            cached = listOf(sent(entry(1, "2026-10-14T08:00:00Z", "2026-10-14T09:00:00Z"))),
            older = listOf(
                entry(1, "2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z"),
                entry(2, "2026-09-02T08:00:00Z", "2026-09-02T09:00:00Z"),
                entry(2, "2026-09-03T08:00:00Z", "2026-09-03T09:00:00Z"),
            ),
            projects = emptyList(),
            tasks = emptyList(),
            time = time(resolution = null),
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 9, 3)), days.map { it.date })
        assertEquals(listOf(1L), days[0].entries.map { it.id })
        assertEquals(listOf(2L), days[1].entries.map { it.id })
        assertTrue("the phone holds it, so the queue can edit it", days[0].entries.single().cached)
        assertFalse("only the server has it, so the queue cannot edit it", days[1].entries.single().cached)
    }

    @Test
    fun aCachedEntryFromBeforeTheWindowShowsAsNotCached() {
        val days = historyDays(
            cached = listOf(sent(entry(1, "2026-10-14T08:00:00Z", "2026-10-14T09:00:00Z")), unsent(entry(2, "2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z"))),
            older = emptyList(),
            projects = emptyList(),
            tasks = emptyList(),
            time = time(resolution = null),
            windowStart = LocalDate.of(2026, 10, 2),
        )

        assertTrue("inside the cache's two weeks", days[0].entries.single().cached)
        val older = days[1].entries.single()
        assertFalse("the cache holds it only until its next refresh", older.cached)
        assertTrue("and it still shows its change as not sent", older.unsent)
    }

    @Test
    fun aRejectedPendingSpanStaysVisibleAtItsActualTimesForCorrection() {
        val raw = entry(5, "2026-10-15T07:00:00Z", "2026-10-15T07:10:00Z")
        val shown = historyDays(listOf(unsent(raw)), emptyList(), emptyList(), emptyList(), time(resolution = 60.0))
            .single().entries.single()
        assertEquals(raw.start, shown.start)
        assertEquals(raw.stop, shown.stop)
        assertTrue(shown.unsent)
    }

    private fun time(resolution: Double?) = AccountTime(MADRID, DayOfWeek.MONDAY, resolution, CLOCK)

    private fun sent(entry: CachedTimeEntry) = EntryWithSendState(entry, unsent = false)

    private fun unsent(entry: CachedTimeEntry) = EntryWithSendState(entry, unsent = true)

    private fun entry(id: Long, start: String, stop: String?, project: Long? = null, task: Long? = null) = CachedTimeEntry(
        id = id,
        workspaceId = 1,
        projectId = project,
        taskId = task,
        description = "Entry $id",
        start = Instant.parse(start),
        stop = stop?.let(Instant::parse),
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    }
}
