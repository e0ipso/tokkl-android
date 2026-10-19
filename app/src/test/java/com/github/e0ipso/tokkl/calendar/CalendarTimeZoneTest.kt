package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
    fun theWeekAndItsBlocksFollowTheAccountTimezone() = runBlocking {
        val model = CalendarModel(
            entries = flowOf(listOf(EntryWithSendState(LATE_CALL, unsent = false))),
            projects = flowOf(emptyList()),
            tasks = flowOf(emptyList()),
            accountSettings = MutableStateFlow(TOKYO_SUNDAY),
            client = { null },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            clock = NOW,
        )

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

    private companion object {
        val TOKYO_SUNDAY = AccountSettings("Asia/Tokyo", DayOfWeek.SUNDAY, timeResolutionMinutes = null)

        /** Noon on Wednesday 14 October in Tokyo, 20:00 on Tuesday the 13th in Los Angeles. */
        val NOW: Clock = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneOffset.UTC)

        val LATE_CALL = CachedTimeEntry(
            id = 1,
            workspaceId = 1,
            projectId = null,
            taskId = null,
            description = "Late call",
            start = Instant.parse("2026-10-13T14:30:00Z"),
            stop = Instant.parse("2026-10-13T16:00:00Z"),
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
        )
    }
}
