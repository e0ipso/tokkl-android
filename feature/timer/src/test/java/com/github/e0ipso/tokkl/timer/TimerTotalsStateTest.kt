package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Today and This week row the Timer page's view model gives its screen, from the cached
 * entries at a fixed noon on Thursday 15 October 2026, in UTC with weeks from Monday.
 */
class TimerTotalsStateTest {

    @Test
    fun nothingShowsUntilTheSettingsAndTheEntriesAreRead() {
        assertEquals(TimerTotalsState.Hidden, timerTotalsAt(null, SETTINGS, NOON))
        assertEquals(TimerTotalsState.Hidden, timerTotalsAt(emptyList(), null, NOON))
    }

    @Test
    fun aZoneThePhoneCannotResolveIsNamed() {
        assertEquals(
            TimerTotalsState.UnknownZone("Unresolved/Account"),
            timerTotalsAt(emptyList(), SETTINGS.copy(timeZone = "Unresolved/Account"), NOON),
        )
    }

    @Test
    fun todayAndThisWeekSumTheirEntriesInTheAccountsFormat() {
        val rows = listOf(
            entry(1, start = "2026-10-15T09:00:00Z", stop = "2026-10-15T10:30:00Z"),
            entry(2, start = "2026-10-12T08:00:00Z", stop = "2026-10-12T09:00:00Z"),
            entry(3, start = "2026-10-11T08:00:00Z", stop = "2026-10-11T09:00:00Z"),
        )

        assertEquals(
            TimerTotalsState.Shown(today = "1:30:00", week = "2:30:00", todaySeconds = 5_400, weekSeconds = 9_000),
            timerTotalsAt(rows, SETTINGS, NOON),
        )
    }

    @Test
    fun aRunningEntryCountsUpToNow() {
        val rows = listOf(entry(1, start = "2026-10-15T11:59:50Z", stop = null))

        assertEquals(
            TimerTotalsState.Shown(today = "0:00:10", week = "0:00:10", todaySeconds = 10, weekSeconds = 10),
            timerTotalsAt(rows, SETTINGS, NOON),
        )
    }

    @Test
    fun theFlowFollowsTheSettingsWithOneSubscriptionToTheCache() = runBlocking {
        val settings = MutableStateFlow<AccountSettings?>(SETTINGS)
        val rows = MutableStateFlow(listOf(entry(1, start = "2026-10-15T09:00:00Z", stop = "2026-10-15T10:00:00Z")))
        var subscriptions = 0
        val entries = rows.onStart { subscriptions++ }
        val shown = mutableListOf<TimerTotalsState>()
        val collecting = launch {
            timerTotals(entries, settings, Clock.fixed(NOON, ZoneOffset.UTC)).take(3).toList(shown)
        }
        while (shown.size < 1) yield()
        settings.value = SETTINGS.copy(timeZone = "Unresolved/Account")
        while (shown.size < 2) yield()
        settings.value = null
        collecting.join()

        assertEquals(
            listOf(
                TimerTotalsState.Shown("1:00:00", "1:00:00", 3_600, 3_600),
                TimerTotalsState.UnknownZone("Unresolved/Account"),
                TimerTotalsState.Hidden,
            ),
            shown,
        )
        assertEquals(1, subscriptions)
    }

    private fun entry(id: Long, start: String, stop: String?) = CachedTimeEntry(
        id = id,
        workspaceId = 1,
        projectId = null,
        taskId = null,
        description = "",
        start = Instant.parse(start),
        stop = stop?.let(Instant::parse),
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private companion object {
        val NOON: Instant = Instant.parse("2026-10-15T12:00:00Z")
        val SETTINGS = AccountSettings(
            timeZone = "UTC",
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
        )
    }
}
