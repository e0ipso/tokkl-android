package com.github.e0ipso.tokkl.calendar

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.entries.WeekSource
import com.github.e0ipso.tokkl.entries.sessionEntries
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * The calendar lays out the week in the account's timezone and from its first day of the week,
 * never the device's. The device runs in Los Angeles here, and the account in Tokyo, 16 hours
 * ahead in October, so a wrong zone moves every block to another hour and most to another day.
 */
class CalendarTimeZoneTest {

    private lateinit var deviceZone: TimeZone

    @Before
    fun putTheDeviceInAnotherZone() {
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun restoreTheDeviceZone() {
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun theWeekAndItsBlocksFollowTheAccountTimezone() = runTest {
        val model = model(flowOf(listOf(EntryWithSendState(LATE_CALL, unsent = false))))

        val state = model.state.first { it is CalendarState.Shown } as CalendarState.Shown

        assertEquals("today in Tokyo, while Los Angeles is still on the 13th", LocalDate.of(2026, 10, 14), state.today)
        assertEquals("the account's week starts on Sunday", LocalDate.of(2026, 10, 11), state.weekStart)
        assertEquals(WeekSource.Cached, state.week)
        val blocks = calendarBlocks(state.entries, state.weekDays, state.zone, NOW.instant())
        // 14:30 to 16:00 UTC is 23:30 to 01:00 in Tokyo, across midnight, while in Los Angeles it
        // would be 07:30 to 09:00 on the 13th.
        assertEquals(
            listOf(
                Triple(LocalDate.of(2026, 10, 13), 23 * 60 + 30.0, 24 * 60.0),
                Triple(LocalDate.of(2026, 10, 14), 0.0, 60.0),
            ),
            blocks.map { Triple(it.day, it.top, it.bottom) },
        )
        assertEquals(5400L, dayTotalSeconds(state.entries, LocalDate.of(2026, 10, 13), state.zone, NOW.instant()))
        assertEquals(0L, dayTotalSeconds(state.entries, LocalDate.of(2026, 10, 14), state.zone, NOW.instant()))
    }

    /**
     * A queued no-time entry keeps its stored day. Its midnight instant may be stale after a
     * timezone change, so the entry sits on its day, not on the date of that instant, and shows
     * the midnight of the account's zone.
     */
    @Test
    fun aQueuedNoTimeEntryStaysOnItsStoredDayWhenItsMidnightIsStale() = runTest {
        // 10:00 UTC on the 10th is 19:00 on the 10th in Tokyo, a day before the entry's day.
        val stale = LATE_CALL.copy(
            id = EntryId(2),
            timing = EntryTiming.AllDay(
                LocalDate.of(2026, 10, 11),
                Instant.parse("2026-10-10T10:00:00Z"),
                Duration.ofMinutes(90),
            ),
        )
        val model = model(flowOf(listOf(EntryWithSendState(stale, unsent = true))))

        val state = model.state.first { it is CalendarState.Shown } as CalendarState.Shown

        val entry = state.entries.single()
        // Midnight on the 11th in Tokyo is 15:00 UTC on the 10th.
        assertEquals(
            Instant.parse("2026-10-10T15:00:00Z") to Instant.parse("2026-10-10T16:30:00Z"),
            entry.start to entry.stop,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 11)), state.allDay.keys.toList())
        assertEquals(
            emptyList<CalendarBlock>(),
            calendarBlocks(state.entries, state.weekDays, state.zone, NOW.instant()),
        )
        assertEquals(5400L, dayTotalSeconds(state.entries, LocalDate.of(2026, 10, 11), state.zone, NOW.instant()))
        assertEquals(0L, dayTotalSeconds(state.entries, LocalDate.of(2026, 10, 10), state.zone, NOW.instant()))
    }

    private companion object {
        val TOKYO_SUNDAY = AccountSettings("Asia/Tokyo", DayOfWeek.SUNDAY, timeResolutionMinutes = null)

        /** Noon on Wednesday 14 October in Tokyo, 20:00 on Tuesday the 13th in Los Angeles. */
        val NOW: Clock = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneOffset.UTC)

        val LATE_CALL = TimeEntry(
            id = EntryId(1),
            workspaceId = WorkspaceId(1),
            projectId = null,
            taskId = null,
            description = "Late call",
            timing = EntryTiming.Stopped(Instant.parse("2026-10-13T14:30:00Z"), Instant.parse("2026-10-13T16:00:00Z")),
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
        )
    }

    /** The calendar on [cached], in Tokyo, with weeks that start on Sunday. */
    private fun model(cached: Flow<List<EntryWithSendState>>): CalendarViewModel {
        val settings = MutableStateFlow<AccountSettings?>(TOKYO_SUNDAY)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return CalendarViewModel(
            entries = sessionEntries(cached, settings, scope, NOW, changes = NoChanges),
            projects = flowOf(emptyList()),
            tasks = flowOf(emptyList()),
            accountSettings = settings,
            changes = NoChanges,
            scope = scope,
            computation = Dispatchers.Unconfined,
            clock = NOW,
            allDayOffered = flowOf(false),
            saved = SavedStateHandle(),
        )
    }

    /** The calendar only shows entries here. */
    private object NoChanges : EntryChanges {
        override suspend fun create(
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ): EntryId = throw AssertionError("nothing is created")

        override suspend fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean =
            throw AssertionError("nothing is edited")

        override suspend fun delete(entryId: EntryId): Boolean = throw AssertionError("nothing is deleted")

        override suspend fun updateOlder(
            entryId: EntryId,
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ): Unit = throw AssertionError("unexpected updateOlder")

        override suspend fun delete(entry: TimeEntry): TimeEntry? = throw AssertionError("unexpected delete")

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }
}
