package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Which entries a week shows and when it asks the server, against a MockWebServer that answers
 * `GET /api/v9/me/time_entries` with [answer]. Today is Sunday 25 October 2026 in UTC, weeks start
 * on Monday, and the phone keeps the entries that start from 12 October on, so the weeks of 12 and
 * 19 October come from the cache and every other week from the server.
 */
class CalendarModelTest {

    private val server = MockWebServer()

    /** Each request's `start_date` and `end_date`. */
    private val requests = CopyOnWriteArrayList<String>()

    /** The body of every answer. */
    @Volatile
    private var answer: String = "[]"

    /** The status of every answer. */
    @Volatile
    private var status: Int = 200

    private lateinit var url: ServerUrl

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.url.queryParameter("start_date")} ${request.url.queryParameter("end_date")}"
                return MockResponse.Builder().code(status).body(answer).addHeader("Content-Type", "application/json").build()
            }
        }
        server.start()
        url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        ConfiguredServer.readStoredFrom { url }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    /**
     * An entry from 26 September at 23:00 to 28 September at 01:00 runs into the week of 28
     * September from more than a day before it. The week asks from 999 hours before its start,
     * Tokkl's longest stopped entry, and keeps the entries that run into it.
     */
    @Test
    fun aWeekOutsideTheCacheAsksFromTheLongestEntryBeforeItAndKeepsWhatRunsIntoIt() {
        answer = json(
            Triple(1L, "2026-09-26T23:00:00Z", "2026-09-28T01:00:00Z"),
            Triple(2L, "2026-09-20T10:00:00Z", "2026-09-20T11:00:00Z"),
            Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z"),
        )
        val model = model(cached = emptyList())

        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }

        assertEquals(listOf(1L, 3L), week.entries.map { it.id }.sorted())
        assertEquals(listOf("2026-08-17T09:00:00Z 2026-10-05T00:00:00Z"), requests)
    }

    /**
     * The cache keeps entries by their start, so an entry from the evening of 11 October that
     * runs past midnight is not in it. The first cached week loads the entries that start in the
     * 999 hours before the cache and run into it, once for both cached weeks.
     */
    @Test
    fun theFirstCachedWeekGetsTheEntryFromTheEveningBeforeTheCache() {
        answer = json(
            Triple(9L, "2026-10-11T23:00:00Z", "2026-10-12T01:00:00Z"),
            Triple(8L, "2026-10-10T10:00:00Z", "2026-10-10T11:00:00Z"),
        )
        val model = model(cached = listOf(entry(5, "2026-10-13T10:00:00Z", "2026-10-13T11:00:00Z")))

        model.step(-7)
        assertEquals(WeekSource.Cached, shown(model) { it.weekStart == OCTOBER_12 }.week)
        model.loadWeek(OCTOBER_12)
        val week = shown(model) { it.weekStart == OCTOBER_12 && it.entries.size == 2 }

        assertEquals(WeekSource.Cached, week.week)
        assertEquals(listOf(5L, 9L), week.entries.map { it.id }.sorted())
        assertEquals(listOf("2026-08-31T09:00:00Z 2026-10-12T00:00:00Z"), requests)

        model.showToday()
        model.loadWeek(OCTOBER_19)
        shown(model) { it.weekStart == OCTOBER_19 }
        assertEquals("today's week shares the span, so no second request", 1, requests.size)
    }

    @Test
    fun aCachedWeekShowsTheCacheOfflineWithNoError() {
        server.close()
        val model = model(cached = listOf(entry(5, "2026-10-13T10:00:00Z", "2026-10-13T11:00:00Z")))

        model.step(-7)
        model.loadWeek(OCTOBER_12)
        val week = shown(model) { it.weekStart == OCTOBER_12 }

        assertEquals(WeekSource.Cached, week.week)
        assertEquals(listOf(5L), week.entries.map { it.id })
    }

    @Test
    fun aWeekBehindADownProxyIsUnreachableAndLoadsOnTheNextTry() {
        status = 503
        val model = model(cached = emptyList())

        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        assertEquals(WeekSource.Unreachable, shown(model) { it.weekStart == SEPTEMBER_28 && it.week != WeekSource.Loading }.week)

        status = 200
        answer = json(Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z"))
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }

        assertEquals(listOf(3L), week.entries.map { it.id })
        assertEquals(2, requests.size)
    }

    @Test
    fun aFailedWeekSaysWhyAndLoadsOnTheNextTry() {
        status = 500
        val model = model(cached = emptyList())

        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        assertEquals(WeekSource.Failed(500), shown(model) { it.weekStart == SEPTEMBER_28 && it.week != WeekSource.Loading }.week)

        status = 200
        model.loadWeek(SEPTEMBER_28)

        assertEquals(WeekSource.Loaded, shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }.week)
    }

    /**
     * Coming back to the foreground makes every loaded week load again the next time it shows,
     * and the old entries show until the new ones come. A start without a stop before it, as a
     * rotation, is no trip to the background.
     */
    @Test
    fun theForegroundLoadsTheShownWeekAgainAndTheOthersWhenTheyShow() {
        answer = json(Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z"))
        val model = model(cached = emptyList())
        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        val first = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }
        assertEquals(listOf(3L), first.entries.map { it.id })
        model.step(-7)
        model.loadWeek(SEPTEMBER_21)
        shown(model) { it.weekStart == SEPTEMBER_21 && it.week == WeekSource.Loaded }
        assertEquals(2, requests.size)

        model.loadWeek(SEPTEMBER_21)
        assertEquals("no trip to the background, no request", 2, requests.size)
        model.cameToForeground()
        model.loadWeek(SEPTEMBER_21)
        assertEquals("a start with no stop, as a rotation, asks nothing", 2, requests.size)
        assertEquals(0, model.foregrounds.value)

        // The entry is deleted on the server while the app is in the background.
        answer = "[]"
        model.wentToBackground()
        model.cameToForeground()
        assertEquals(1, model.foregrounds.value)
        model.loadWeek(SEPTEMBER_21)
        assertEquals("the shown week loads again", 3, requests.size)

        model.step(7)
        val old = shown(model) { it.weekStart == SEPTEMBER_28 }
        assertEquals("the other week shows its old entries until it loads", listOf(3L), old.entries.map { it.id })
        model.loadWeek(SEPTEMBER_28)
        val reloaded = shown(model) { it.weekStart == SEPTEMBER_28 && it.entries.isEmpty() }
        assertEquals(WeekSource.Loaded, reloaded.week)
        assertEquals(4, requests.size)
        model.loadWeek(SEPTEMBER_28)
        assertEquals("loaded since the foreground, so no request", 4, requests.size)
    }

    @Test
    fun anOfflineForegroundKeepsTheLoadedWeekWithNoError() {
        answer = json(Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z"))
        val model = model(cached = emptyList())
        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }

        server.close()
        model.wentToBackground()
        model.cameToForeground()
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 }

        assertEquals(WeekSource.Loaded, week.week)
        assertEquals(listOf(3L), week.entries.map { it.id })
    }

    /**
     * A day chosen in the strip shows with its week, and a step goes on from it: a day forward
     * from Sunday 4 October is Monday 5 October, in the next week, which the phone does not keep.
     */
    @Test
    fun aChosenDayShowsWithItsWeekAndAStepCrossesIntoTheNext() {
        val model = model(cached = emptyList())

        model.select(OCTOBER_4)
        val sunday = shown(model) { it.selected == OCTOBER_4 }
        model.step(1)
        val monday = shown(model) { it.selected == OCTOBER_5 }

        assertEquals(SEPTEMBER_28, sunday.weekStart)
        assertEquals(OCTOBER_5, monday.weekStart)
        assertEquals(WeekSource.Loading, monday.week)
    }

    private fun model(cached: List<CachedTimeEntry>) = CalendarModel(
        entries = flowOf(cached.map { EntryWithSendState(it, unsent = false) }),
        projects = flowOf(emptyList()),
        tasks = flowOf(emptyList()),
        accountSettings = MutableStateFlow(SETTINGS),
        client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        clock = CLOCK,
    )

    private fun shown(model: CalendarModel, condition: (CalendarState.Shown) -> Boolean): CalendarState.Shown = runBlocking {
        withTimeout(TIMEOUT_MS) { model.state.first { it is CalendarState.Shown && condition(it) } as CalendarState.Shown }
    }

    private fun entry(id: Long, start: String, stop: String?) = CachedTimeEntry(
        id = id,
        workspaceId = 1,
        description = "e$id",
        projectId = null,
        taskId = null,
        tagIds = emptyList(),
        billable = false,
        start = Instant.parse(start),
        stop = stop?.let(Instant::parse),
        tags = emptyList(),
    )

    /** Stopped entries as `GET /api/v9/me/time_entries` lists them: id, start and stop. */
    private fun json(vararg entries: Triple<Long, String, String>) = entries.joinToString(",", "[", "]") { (id, start, stop) ->
        """{"id":$id,"workspace_id":1,"description":"e$id","start":"$start","stop":"$stop","duration":60,"tag_ids":[],"billable":false}"""
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        val SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /** Sunday 25 October at noon. */
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)

        val SEPTEMBER_21: LocalDate = LocalDate.of(2026, 9, 21)
        val SEPTEMBER_28: LocalDate = LocalDate.of(2026, 9, 28)
        val OCTOBER_4: LocalDate = LocalDate.of(2026, 10, 4)
        val OCTOBER_5: LocalDate = LocalDate.of(2026, 10, 5)
        val OCTOBER_12: LocalDate = LocalDate.of(2026, 10, 12)
        val OCTOBER_19: LocalDate = LocalDate.of(2026, 10, 19)
    }
}
