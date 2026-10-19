package com.github.e0ipso.tokkl.history

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The history's older days against a MockWebServer that answers `GET /api/app/bounds` and
 * `GET /api/v9/me/time_entries` from one entry a day, unless a test sets [boundsAnswer] or
 * [entriesAnswer], with a clock the test can move on a day while the model lives, as when the app
 * stays open past midnight.
 */
class HistoryModelTest {

    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()
    private val now = AtomicReference(OCTOBER_19)
    private val cached = MutableStateFlow<List<EntryWithSendState>>(emptyList())
    private val main: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainDispatcher: ExecutorCoroutineDispatcher = main.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private lateinit var client: TogglApiClient

    /** The day the account's first entry starts on, as `GET /api/app/bounds` says. */
    private var firstDay = LocalDate.parse("2026-01-01")

    /** What `GET /api/app/bounds` answers instead of [firstDay], when set. */
    @Volatile
    private var boundsAnswer: MockResponse? = null

    /** What `GET /api/v9/me/time_entries` answers instead of the entries from [firstDay] on, when set. */
    @Volatile
    private var entriesAnswer: MockResponse? = null

    /** The `start_date` and `end_date` of each `GET /api/v9/me/time_entries`. */
    private val pages = CopyOnWriteArrayList<String>()

    /** One entry a day on the server, from [firstDay] on. */
    private val serverEntries: List<CachedTimeEntry>
        get() = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { it <= LAST_DAY }.map(::entryOn).toList()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request.url.encodedPath
                return when (request.url.encodedPath) {
                    "/api/app/bounds" -> boundsAnswer ?: json("""{"first_date":"$firstDay"}""")
                    "/api/v9/me/time_entries" -> {
                        pages += "${request.url.queryParameter("start_date")} ${request.url.queryParameter("end_date")}"
                        entriesAnswer?.let { return it }
                        val start = Instant.parse(request.url.queryParameter("start_date"))
                        val end = Instant.parse(request.url.queryParameter("end_date"))
                        json(serverEntries.filter { it.start >= start && it.start < end }.joinToString(",", "[", "]") { it.json() })
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        ConfiguredServer.readStoredFrom { configured }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(configured))
        client = TogglApiClient(configured, ApiCredentials.ApiToken("tok-123"))
    }

    @After
    fun tearDown() {
        scope.cancel()
        main.shutdown()
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun theDaysTheCacheDropsWhenItsWindowMovesStayBetweenItAndTheOlderDays() {
        cached.value = cachedDays(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-19"))
        val model = model()
        awaitShown(model) { it.days.size == 14 }
        onMain { model.loadOlder() }
        val loaded = awaitShown(model) { it.days.size == 28 }
        assertEquals(days("2026-10-19", "2026-09-22"), loaded.days.map { it.date })
        val sent = requests.size

        // Past midnight, a refresh moves the cache's window on a day and drops October 6.
        now.set(OCTOBER_19.plus(Duration.ofDays(1)))
        cached.value = cachedDays(LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-20"))
        val moved = awaitShown(model) { it.today == LocalDate.parse("2026-10-20") }

        assertEquals("every day from today to the oldest day loaded", days("2026-10-20", "2026-09-22"), moved.days.map { it.date })
        assertEquals("nothing loads before the list's end shows", sent, requests.size)

        onMain { model.loadOlder() }
        val older = awaitShown(model) { it.days.last().date == LocalDate.parse("2026-09-08") }
        assertEquals("the next page goes on from the oldest day loaded", days("2026-10-20", "2026-09-08"), older.days.map { it.date })
    }

    @Test
    fun theDaysTheCacheDropsStayWhenTheServerHasNothingOlder() {
        firstDay = LocalDate.parse("2026-10-06")
        cached.value = cachedDays(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-19"))
        val model = model()
        awaitShown(model) { it.days.size == 14 }
        onMain { model.loadOlder() }
        awaitShown(model) { it.older == OlderDays.End }
        assertEquals("only the bounds", listOf("/api/app/bounds"), requests)

        now.set(OCTOBER_19.plus(Duration.ofDays(1)))
        cached.value = cachedDays(LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-20"))
        val moved = awaitShown(model) { it.today == LocalDate.parse("2026-10-20") }

        assertEquals(days("2026-10-20", "2026-10-06"), moved.days.map { it.date })
        assertEquals(OlderDays.End, moved.older)
        assertEquals(listOf("/api/app/bounds"), requests)
    }

    /**
     * Each page is the 14 days before the oldest day shown, from midnight in Madrid, and the one
     * that reaches the account's first day, as the bounds say, ends the list. Nothing more loads
     * after that.
     */
    @Test
    fun olderDaysLoadFourteenAtATimeUntilTheAccountsFirstDay() {
        firstDay = LocalDate.parse("2026-09-10")
        cached.value = cachedDays(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-19"))
        val model = model()
        awaitShown(model) { it.days.size == 14 }
        assertEquals("nothing loads before the list's end shows", emptyList<String>(), requests)

        onMain { model.loadOlder() }
        val first = awaitShown(model) { it.older != OlderDays.Loading && it.days.size > 14 }
        assertEquals(OlderDays.More(auto = true), first.older)
        assertEquals(days("2026-10-19", "2026-09-22"), first.days.map { it.date })
        assertEquals(listOf("2026-09-21T22:00:00Z 2026-10-05T22:00:00Z"), pages)

        onMain { model.loadOlder() }
        val last = awaitShown(model) { it.older == OlderDays.End }
        assertEquals(days("2026-10-19", "2026-09-10"), last.days.map { it.date })
        assertEquals("2026-09-07T22:00:00Z 2026-09-21T22:00:00Z", pages.last())

        onMain { model.loadOlder() }
        assertEquals("the bounds once, and two pages", listOf("/api/app/bounds", PAGE, PAGE), requests)
    }

    /**
     * A server without the bounds, such as Toggl Track, gets pages until one comes back empty.
     * After that only a tap loads more, so scrolling never walks back through empty weeks.
     */
    @Test
    fun withoutBoundsAnEmptyPageWaitsForATap() {
        boundsAnswer = MockResponse.Builder().code(404).build()
        entriesAnswer = json("[]")
        cached.value = cachedDays(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-19"))
        val model = model()
        awaitShown(model) { it.days.size == 14 }

        onMain { model.loadOlder() }
        assertEquals(OlderDays.More(auto = false), awaitShown(model) { it.older.settled() }.older)

        entriesAnswer = null
        onMain { model.loadOlder() }
        val tapped = awaitShown(model) { it.older == OlderDays.More(auto = true) }
        assertEquals(OlderDays.More(auto = true), tapped.older)
        assertEquals("the tap loads the 14 days before the empty page", "2026-09-07T22:00:00Z 2026-09-21T22:00:00Z", pages.last())
        assertEquals(days("2026-10-19", "2026-10-06") + days("2026-09-21", "2026-09-08"), tapped.days.map { it.date })
    }

    @Test
    fun boundsWithNoEntriesEndTheListWithoutAPage() {
        boundsAnswer = json("""{"first_date":null,"last_date":null}""")
        val model = model()

        onMain { model.loadOlder() }

        assertEquals(OlderDays.End, awaitShown(model) { it.older.settled() }.older)
        assertEquals(listOf("/api/app/bounds"), requests)
    }

    @Test
    fun offlineTheOlderDaysAreUnreachableAndTheNextTryLoadsThem() {
        // The connection drops before any answer, as when the phone has no network. A dispatcher's
        // answer cannot act at the request's start, which only queued answers can.
        boundsAnswer = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
        cached.value = cachedDays(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-19"))
        val model = model()
        awaitShown(model) { it.days.size == 14 }

        onMain { model.loadOlder() }
        val offline = awaitShown(model) { it.older.settled() }
        assertEquals(OlderDays.Unreachable, offline.older)
        assertEquals(14, offline.days.size)

        boundsAnswer = null
        onMain { model.loadOlder() }
        val loaded = awaitShown(model) { it.older != OlderDays.Loading && it.days.size > 14 }
        assertEquals(days("2026-10-19", "2026-09-22"), loaded.days.map { it.date })
    }

    /** A reverse proxy answers 502, 503 or 504 while the server behind it is down. */
    @Test
    fun aProxyThatCannotReachTheServerIsUnreachable() {
        entriesAnswer = MockResponse.Builder().code(503).build()
        val model = model()

        onMain { model.loadOlder() }

        assertEquals(OlderDays.Unreachable, awaitShown(model) { it.older.settled() }.older)
    }

    private fun model() = HistoryModel(
        entries = cached,
        projects = MutableStateFlow(emptyList<CachedProject>()),
        tasks = MutableStateFlow(emptyList<CachedTask>()),
        accountSettings = MutableStateFlow(SETTINGS),
        client = { client },
        scope = scope,
        io = Dispatchers.IO,
        clock = MovableClock(now),
    )

    /**
     * The page has come back with anything but more days to load on scroll, the state before
     * the first loadOlder() too.
     */
    private fun OlderDays.settled(): Boolean = this != OlderDays.Loading && this != OlderDays.More(auto = true)

    /** loadOlder() runs on the main thread, which [scope] stands in for. */
    private fun onMain(block: () -> Unit) = runBlocking(mainDispatcher) { block() }

    /** Fails with the last state shown and the requests sent when [condition] never holds. */
    private fun awaitShown(model: HistoryModel, condition: (HistoryState.Shown) -> Boolean): HistoryState.Shown {
        var last: HistoryState.Shown? = null
        return runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) { model.state.filterIsInstance<HistoryState.Shown>().onEach { last = it }.first(condition) }
        } ?: throw AssertionError(
            "timed out; the last state had ${last?.days?.size} days and ${last?.older} at the end, after the requests $requests",
        )
    }

    private fun cachedDays(first: LocalDate, last: LocalDate): List<EntryWithSendState> =
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { EntryWithSendState(entryOn(it), unsent = false) }.toList()

    /** Newest first, from [newest] back to [oldest]. */
    private fun days(newest: String, oldest: String): List<LocalDate> =
        generateSequence(LocalDate.parse(newest)) { it.minusDays(1) }.takeWhile { it >= LocalDate.parse(oldest) }.toList()

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    /** The clock's instant comes from [now], also in the zones it is moved to. */
    private class MovableClock(private val now: AtomicReference<Instant>, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val PAGE = "/api/v9/me/time_entries"
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val SETTINGS = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = null)

        val LAST_DAY: LocalDate = LocalDate.parse("2026-10-20")

        /** Noon in Madrid. */
        val OCTOBER_19: Instant = Instant.parse("2026-10-19T10:00:00Z")

        /** An hour from nine in the morning, Madrid time. */
        fun entryOn(day: LocalDate): CachedTimeEntry {
            val start = day.atTime(9, 0).atZone(MADRID).toInstant()
            return CachedTimeEntry(
                id = day.toEpochDay(),
                workspaceId = 1,
                projectId = null,
                taskId = null,
                description = "Worked on $day",
                start = start,
                stop = start.plusSeconds(3600),
                billable = false,
                tags = emptyList(),
                tagIds = emptyList(),
            )
        }

        fun CachedTimeEntry.json(): String =
            """{"id":$id,"workspace_id":$workspaceId,"description":"$description","start":"$start","stop":"$stop",""" +
                """"billable":false,"tags":[],"tag_ids":[]}"""
    }
}
