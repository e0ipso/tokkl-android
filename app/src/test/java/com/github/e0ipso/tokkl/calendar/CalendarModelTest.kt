package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertTrue
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

    private val changes = RecordingChanges()

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
     * The network's return counts as a foreground for the calendar on screen, which then loads
     * its week again. A week that got no answer loads; one that loaded since asks nothing.
     */
    @Test
    fun theNetworksReturnLoadsTheShownWeekOnlyWhenItGotNoAnswer() {
        val model = model(cached = emptyList())
        model.step(-27)
        status = 503
        model.loadWeek(SEPTEMBER_28)
        shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Unreachable }

        status = 200
        answer = json(Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z"))
        model.reconnected()
        assertEquals("the calendar on screen loads its week", 1, model.foregrounds.value)
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }
        assertEquals(listOf(3L), week.entries.map { it.id })
        assertEquals(2, requests.size)

        model.reconnected()
        model.loadWeek(SEPTEMBER_28)
        assertEquals("a week loaded since asks nothing", 2, requests.size)
    }

    @Test
    fun aReconnectDuringAnUnreachableLoadRetriesWhenThatLoadEnds() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val attempts = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (attempts.incrementAndGet() == 1) {
                    started.countDown()
                    check(release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    return MockResponse.Builder().code(503).build()
                }
                return MockResponse.Builder().code(200).body(
                    json(Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z")),
                ).addHeader("Content-Type", "application/json").build()
            }
        }
        val model = model(cached = emptyList(), io = Dispatchers.IO)
        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        try {
            assertTrue("the first route's request is pending", started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            model.reconnected()
            model.loadWeek(SEPTEMBER_28)
        } finally {
            release.countDown()
        }

        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }
        assertEquals(listOf(3L), week.entries.map { it.id })
        assertEquals("one pending reconnect retries once", 2, attempts.get())
        model.reconnected()
        model.loadWeek(SEPTEMBER_28)
        assertEquals("a loaded week sends nothing on another route update", 2, attempts.get())
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

    /** A drag on empty space queues an entry in the default workspace, on whole steps of 15 minutes, and hands back its id. */
    @Test
    fun aCreateDragQueuesAnEntryInTheDefaultWorkspace() {
        val model = model(cached = emptyList(), settings = DRAG_SETTINGS)
        val created = mutableListOf<Long>()

        model.create(OCTOBER_20, fromMinute = 600, toMinute = 690) { created += it }
        model.create(OCTOBER_20, fromMinute = 600, toMinute = 600) { created += it }

        assertEquals(listOf("create 7 ${EntryFields()} 2026-10-20T10:00:00Z 2026-10-20T11:30:00Z"), changes.calls)
        assertEquals(listOf(LOCAL_ID), created)
    }

    /** A move and a resize of a cached entry queue it with every field, from the times its block shows. */
    @Test
    fun aMoveAndAResizeQueueTheCachedEntry() {
        val cached = entry(4, "2026-10-20T09:00:00Z", "2026-10-20T10:00:00Z").copy(description = "Plan", billable = true)
        val model = model(cached = listOf(cached), settings = DRAG_SETTINGS)
        val block = shown(model) { true }.entries.single()
        val fields = EntryFields(description = "Plan", billable = true)

        model.move(block, OCTOBER_20, deltaMinutes = 60, dayMode = true)
        model.resize(block, ResizeEdge.END, deltaMinutes = 30)
        model.resize(block, ResizeEdge.START, deltaMinutes = -45)

        assertEquals(
            listOf(
                "update 4 $fields 2026-10-20T10:00:00Z 2026-10-20T11:00:00Z",
                "update 4 $fields 2026-10-20T09:00:00Z 2026-10-20T10:30:00Z",
                "update 4 $fields 2026-10-20T08:15:00Z 2026-10-20T10:00:00Z",
            ),
            changes.calls,
        )
    }

    /** An entry from a week only the server has goes to the queue whole, in its own workspace. */
    @Test
    fun aMoveInAWeekFromTheServerQueuesTheServersEntry() {
        answer = json(Triple(8L, "2026-10-06T09:00:00Z", "2026-10-06T10:00:00Z"))
        val model = model(cached = emptyList(), settings = DRAG_SETTINGS)
        model.select(OCTOBER_5)
        model.loadWeek(OCTOBER_5)
        val block = shown(model) { it.week == WeekSource.Loaded }.entries.single()

        model.move(block, OCTOBER_5.plusDays(1), deltaMinutes = -30, dayMode = false)

        assertEquals(listOf("updateOlder 8 1 ${EntryFields(description = "e8")} 2026-10-06T08:30:00Z 2026-10-06T09:30:00Z"), changes.calls)
        assertEquals(8L, model.serverEntry(8)?.id)
    }

    /**
     * The cache holds an entry from a week only the server has from the drag until the send and
     * the refresh after it drop it. The week keeps the moved times after that, a second drag starts
     * from them, and the week's next load replaces them with the server's copy.
     */
    @Test
    fun aMovedEntryOfAWeekFromTheServerKeepsItsTimesOnceTheCacheDropsIt() {
        answer = json(Triple(8L, "2026-10-06T09:00:00Z", "2026-10-06T10:00:00Z"))
        val cache = MutableStateFlow(emptyList<EntryWithSendState>())
        val model = model(cached = emptyList(), settings = DRAG_SETTINGS, cache = cache)
        model.select(OCTOBER_5)
        model.loadWeek(OCTOBER_5)
        val block = shown(model) { it.week == WeekSource.Loaded }.entries.single()

        model.move(block, OCTOBER_5.plusDays(1), deltaMinutes = 60, dayMode = false)
        val moved = entry(8, "2026-10-06T10:00:00Z", "2026-10-06T11:00:00Z")
        cache.value = listOf(EntryWithSendState(moved, unsent = true))
        shown(model) { it.entries.single().unsent }
        cache.value = emptyList()
        val kept = shown(model) { !it.entries.single().unsent }.entries.single()

        assertEquals(Instant.parse("2026-10-06T10:00:00Z") to Instant.parse("2026-10-06T11:00:00Z"), kept.start to kept.stop)
        assertEquals(moved, model.serverEntry(8))

        model.move(kept, OCTOBER_5.plusDays(1), deltaMinutes = 60, dayMode = false)
        assertEquals(
            listOf(
                "updateOlder 8 1 ${EntryFields(description = "e8")} 2026-10-06T10:00:00Z 2026-10-06T11:00:00Z",
                "updateOlder 8 1 ${EntryFields(description = "e8")} 2026-10-06T11:00:00Z 2026-10-06T12:00:00Z",
            ),
            changes.calls,
        )

        answer = json(Triple(8L, "2026-10-06T13:00:00Z", "2026-10-06T14:00:00Z"))
        model.wentToBackground()
        model.cameToForeground()
        model.loadWeek(OCTOBER_5)
        val reloaded = shown(model) { it.entries.singleOrNull()?.start == Instant.parse("2026-10-06T13:00:00Z") }.entries.single()
        assertEquals(Instant.parse("2026-10-06T14:00:00Z"), reloaded.stop)
    }

    /** The timer edits the running entry, so no drag changes it, and nothing is created without a default workspace. */
    @Test
    fun theRunningEntryAndAnAccountWithoutAWorkspaceQueueNothing() {
        val model = model(cached = listOf(entry(5, "2026-10-25T09:00:00Z", stop = null)), settings = DRAG_SETTINGS.copy(defaultWorkspaceId = null))
        val running = shown(model) { true }.entries.single()

        model.move(running, OCTOBER_25, deltaMinutes = 60, dayMode = true)
        model.resize(running, ResizeEdge.START, deltaMinutes = 15)
        model.create(OCTOBER_25, fromMinute = 600, toMinute = 660)

        assertEquals(emptyList<String>(), changes.calls)
    }

    @Test
    fun aMovedLegacyShortEntryIsRejectedBeforeQueueing() {
        val cached = entry(4, "2026-10-20T09:00:00Z", "2026-10-20T09:10:00Z")
        val model = model(cached = listOf(cached), settings = DRAG_SETTINGS.copy(timeResolutionMinutes = 60.0))
        val block = shown(model) { true }.entries.single()
        model.move(block, OCTOBER_20, deltaMinutes = 60, dayMode = true)
        assertEquals(emptyList<String>(), changes.calls)
    }

    @Test
    fun aRejectedPendingSpanKeepsItsActualTimesAndUnsentState() {
        val cached = entry(4, "2026-10-20T09:00:00Z", "2026-10-20T09:10:00Z")
        val model = model(cached = listOf(cached), settings = DRAG_SETTINGS.copy(timeResolutionMinutes = 60.0),
            cache = flowOf(listOf(EntryWithSendState(cached, unsent = true))))
        val block = shown(model) { true }.entries.single()
        assertEquals(cached.start to cached.stop, block.start to block.stop)
        assertEquals(true, block.unsent)
        assertEquals(emptyList<String>(), changes.calls)
    }

    private fun model(
        cached: List<CachedTimeEntry>,
        settings: AccountSettings = SETTINGS,
        cache: Flow<List<EntryWithSendState>> = flowOf(cached.map { EntryWithSendState(it, unsent = false) }),
        io: CoroutineContext = Dispatchers.Unconfined,
    ) = CalendarModel(
        entries = cache,
        projects = flowOf(emptyList()),
        tasks = flowOf(emptyList()),
        accountSettings = MutableStateFlow(settings),
        changes = changes,
        client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = io,
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

    /** Records what the calendar queues. */
    private class RecordingChanges : EntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            calls += "create $workspaceId $fields $start $stop"
            return LOCAL_ID
        }

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean {
            calls += "update $entryId $fields $start $stop"
            return true
        }

        override suspend fun updateOlder(entryId: Long, workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant) {
            calls += "updateOlder $entryId $workspaceId $fields $start $stop"
        }

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("the calendar starts no timer")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("the calendar stops no timer")

        override suspend fun delete(entryId: Long): Boolean = throw AssertionError("the calendar deletes nothing")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val LOCAL_ID = -3L
        val SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /** Tokkl's default resolution of 5 minutes, so drags step by 15, and a default workspace. */
        val DRAG_SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = 5.0, defaultWorkspaceId = 7)

        /** Sunday 25 October at noon. */
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)

        val SEPTEMBER_21: LocalDate = LocalDate.of(2026, 9, 21)
        val SEPTEMBER_28: LocalDate = LocalDate.of(2026, 9, 28)
        val OCTOBER_4: LocalDate = LocalDate.of(2026, 10, 4)
        val OCTOBER_5: LocalDate = LocalDate.of(2026, 10, 5)
        val OCTOBER_12: LocalDate = LocalDate.of(2026, 10, 12)
        val OCTOBER_19: LocalDate = LocalDate.of(2026, 10, 19)
        val OCTOBER_20: LocalDate = LocalDate.of(2026, 10, 20)
        val OCTOBER_25: LocalDate = LocalDate.of(2026, 10, 25)
    }
}
