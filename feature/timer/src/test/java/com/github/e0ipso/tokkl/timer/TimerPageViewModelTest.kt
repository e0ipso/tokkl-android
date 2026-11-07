package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.picker.PickerSource
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Timer page's own state, from flows that stand in for the cache, the network and the
 * account settings. Unconfined runs every update to the end inside the call.
 */
class TimerPageViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val views = object : TimerViewChoice {
        override val view = MutableStateFlow(TimerView.LIST)
        override fun choose(view: TimerView) {
            this.view.value = view
        }
    }
    private val entries = MutableStateFlow(listOf(entry(1, "Code review", WEBSITE.id)))
    private val picker = MutableStateFlow(PickerSource(projects = listOf(WEBSITE), clients = listOf(ACME)))
    private val offline = MutableStateFlow(false)
    private val settings: MutableStateFlow<AccountSettings?> = MutableStateFlow(null)

    private val model = TimerPageViewModel(
        views = views,
        entries = entries,
        picker = picker,
        settings = settings,
        offline = offline,
        clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC),
        scope = scope,
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun theSuggestionsJoinTheCachedEntriesWithTheirProjectsAndClients() = runBlocking {
        val shown = model.state.shownWhere { it.picker.projects.isNotEmpty() }

        assertEquals(picker.value, shown.picker)
        assertEquals(listOf("Code review"), shown.suggestions.forQuery("acme").map { it.fields.description })
        assertEquals("Website", shown.suggestions.forQuery("").single().project)
    }

    @Test
    fun theViewTheNetworkAndTheSettingsFollowTheirSources() = runBlocking {
        model.chooseView(TimerView.CALENDAR)
        offline.value = true
        settings.value = SETTINGS

        val shown = model.state.shownWhere { it.settings != null }

        assertEquals(TimerView.CALENDAR, views.view.value)
        assertEquals(TimerView.CALENDAR, shown.view)
        assertEquals(true, shown.offline)
        assertEquals(SETTINGS, shown.settings)
    }

    @Test
    fun theTotalsFollowTheCacheInTheAccountsSettings() = runBlocking {
        assertEquals(TimerTotalsState.Hidden, model.totals.value)
        settings.value = SETTINGS

        assertEquals(
            TimerTotalsState.Shown("1:00:00", "1:00:00", 3_600, 3_600),
            model.totals.shownWhere { it != TimerTotalsState.Hidden },
        )
    }

    /** The first value of this flow, as a screen reading it sees it, that passes [shows]. */
    private suspend fun <T> StateFlow<T>.shownWhere(shows: (T) -> Boolean): T {
        val reader = scope.launch { collect {} }
        return first(shows).also { reader.cancel() }
    }

    private fun entry(id: Long, description: String, projectId: Long?) = CachedTimeEntry(
        id = id,
        workspaceId = WORKSPACE,
        projectId = projectId,
        taskId = null,
        description = description,
        start = Instant.parse("2026-10-15T09:00:00Z"),
        stop = Instant.parse("2026-10-15T10:00:00Z"),
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private companion object {
        const val WORKSPACE = 10L
        val ACME = CachedClient(7, WORKSPACE, "Acme", archived = false)
        val WEBSITE = CachedProject(
            1,
            WORKSPACE,
            clientId = ACME.id,
            name = "Website",
            color = "#0b83d9",
            active = true,
            billable = false,
        )
        val SETTINGS = AccountSettings(
            timeZone = "UTC",
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
        )
    }
}
