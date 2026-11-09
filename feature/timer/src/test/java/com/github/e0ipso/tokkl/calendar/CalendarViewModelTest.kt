package com.github.e0ipso.tokkl.calendar

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.entries.ServerWeeks
import com.github.e0ipso.tokkl.entries.WeekSource
import com.github.e0ipso.tokkl.entries.sessionEntries
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Which entries a week shows and when it asks the server, against a MockWebServer that answers
 * `GET /api/v9/me/time_entries` with [answer]. Today is Sunday 25 October 2026 in UTC, weeks start
 * on Monday, and the phone keeps the entries that start from 12 October on, so the weeks of 12 and
 * 19 October come from the cache and every other week from the server.
 */
class CalendarViewModelTest {

    private val testApi = TestApi(null)

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

    /** What escaped the view model's coroutines, which on the phone would reach the thread and end the app. */
    private val escaped = CopyOnWriteArrayList<Throwable>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.url.queryParameter("start_date")} ${request.url.queryParameter("end_date")}"
                return MockResponse.Builder().code(
                    status,
                ).body(answer).addHeader("Content-Type", "application/json").build()
            }
        }
        server.start()
        url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
    }

    @After
    fun tearDown() {
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

        assertEquals(listOf(1L, 3L), week.entries.map { it.id.value }.sorted())
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
        assertEquals(listOf(5L, 9L), week.entries.map { it.id.value }.sorted())
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
        assertEquals(listOf(5L), week.entries.map { it.id.value })
    }

    @Test
    fun aWeekBehindADownProxyIsUnreachableAndLoadsOnTheNextTry() {
        status = 503
        val model = model(cached = emptyList())

        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        assertEquals(
            WeekSource.Unreachable,
            shown(model) { it.weekStart == SEPTEMBER_28 && it.week != WeekSource.Loading }.week,
        )

        status = 200
        answer = json(Triple(3L, "2026-09-29T10:00:00Z", "2026-09-29T11:00:00Z"))
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }

        assertEquals(listOf(3L), week.entries.map { it.id.value })
        assertEquals(2, requests.size)
    }

    @Test
    fun aFailedWeekSaysWhyAndLoadsOnTheNextTry() {
        status = 500
        val model = model(cached = emptyList())

        model.step(-27)
        model.loadWeek(SEPTEMBER_28)
        assertEquals(
            WeekSource.Failed(500),
            shown(model) { it.weekStart == SEPTEMBER_28 && it.week != WeekSource.Loading }.week,
        )

        status = 200
        model.loadWeek(SEPTEMBER_28)

        assertEquals(
            WeekSource.Loaded,
            shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }.week,
        )
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
        assertEquals(listOf(3L), first.entries.map { it.id.value })
        model.step(-7)
        model.loadWeek(SEPTEMBER_21)
        shown(model) { it.weekStart == SEPTEMBER_21 && it.week == WeekSource.Loaded }
        assertEquals(2, requests.size)

        model.loadWeek(SEPTEMBER_21)
        assertEquals("no trip to the background, no request", 2, requests.size)
        assertEquals(0, model.foregrounds.value)

        // The entry is deleted on the server while the app is in the background.
        answer = "[]"
        app.trip()
        assertEquals(1, model.foregrounds.value)
        model.loadWeek(SEPTEMBER_21)
        assertEquals("the shown week loads again", 3, requests.size)

        model.step(7)
        val old = shown(model) { it.weekStart == SEPTEMBER_28 }
        assertEquals("the other week shows its old entries until it loads", listOf(3L), old.entries.map { it.id.value })
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
        app.trip()
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 }

        assertEquals(WeekSource.Loaded, week.week)
        assertEquals(listOf(3L), week.entries.map { it.id.value })
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
        app.reconnect()
        assertEquals("the calendar on screen loads its week", 1, model.foregrounds.value)
        model.loadWeek(SEPTEMBER_28)
        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }
        assertEquals(listOf(3L), week.entries.map { it.id.value })
        assertEquals(2, requests.size)

        app.reconnect()
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
            app.reconnect()
            model.loadWeek(SEPTEMBER_28)
        } finally {
            release.countDown()
        }

        val week = shown(model) { it.weekStart == SEPTEMBER_28 && it.week == WeekSource.Loaded }
        assertEquals(listOf(3L), week.entries.map { it.id.value })
        assertEquals("one pending reconnect retries once", 2, attempts.get())
        app.reconnect()
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

    /**
     * A drag on empty space queues an entry in the default workspace, on whole steps of 15 minutes, and hands back its
     * id.
     */
    @Test
    fun aCreateDragQueuesAnEntryInTheDefaultWorkspace() {
        val model = model(cached = emptyList(), settings = DRAG_SETTINGS)
        val created = mutableListOf<EntryId>()

        model.create(OCTOBER_20, fromMinute = 600, toMinute = 690) { created += it }
        model.create(OCTOBER_20, fromMinute = 600, toMinute = 600) { created += it }

        assertEquals(listOf("create 7 ${EntryFields()} 2026-10-20T10:00:00Z 2026-10-20T11:30:00Z"), changes.calls)
        assertEquals(listOf(EntryId(LOCAL_ID)), created)
    }

    /** A create drag the phone's storage refuses makes nothing and opens no form, as a refused entry form save. */
    @Test
    fun aCreateDragTheStorageRefusesMakesNothing() {
        val model = model(cached = emptyList(), settings = DRAG_SETTINGS)
        val created = mutableListOf<EntryId>()
        changes.refusal = IOException("disk full")

        model.create(OCTOBER_20, fromMinute = 600, toMinute = 690) { created += it }

        assertEquals(emptyList<Throwable>(), escaped)
        assertEquals(emptyList<EntryId>(), created)
        changes.refusal = null
        model.create(OCTOBER_20, fromMinute = 600, toMinute = 690) { created += it }
        assertEquals("the calendar still takes drags", listOf(EntryId(LOCAL_ID)), created)
    }

    /** A move the phone's storage refuses leaves the entry where it was. */
    @Test
    fun aMoveTheStorageRefusesLeavesTheEntry() {
        val cached = entry(4, "2026-10-20T09:00:00Z", "2026-10-20T10:00:00Z")
        val model = model(cached = listOf(cached), settings = DRAG_SETTINGS)
        val block = shown(model) { true }.entries.single()
        changes.refusal = IOException("disk I/O error")

        model.move(block, OCTOBER_20, deltaMinutes = 60, dayMode = true)
        model.resize(block, ResizeEdge.END, deltaMinutes = 30)

        assertEquals(emptyList<Throwable>(), escaped)
        assertEquals(emptyList<String>(), changes.calls)
    }

    /** A move and a resize of a cached entry queue it with every field, from the times its block shows. */
    @Test
    fun aMoveAndAResizeQueueTheCachedEntry() {
        val cached = entry(
            4,
            "2026-10-20T09:00:00Z",
            "2026-10-20T10:00:00Z",
        ).copy(description = "Plan", billable = true)
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

        assertEquals(
            listOf("updateOlder 8 1 ${EntryFields(description = "e8")} 2026-10-06T08:30:00Z 2026-10-06T09:30:00Z"),
            changes.calls,
        )
        assertEquals(EntryId(8), model.serverEntry(EntryId(8))?.id)
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

        assertEquals(
            Instant.parse("2026-10-06T10:00:00Z") to Instant.parse("2026-10-06T11:00:00Z"),
            kept.start to kept.stop,
        )
        assertEquals(moved, model.serverEntry(EntryId(8)))

        model.move(kept, OCTOBER_5.plusDays(1), deltaMinutes = 60, dayMode = false)
        assertEquals(
            listOf(
                "updateOlder 8 1 ${EntryFields(description = "e8")} 2026-10-06T10:00:00Z 2026-10-06T11:00:00Z",
                "updateOlder 8 1 ${EntryFields(description = "e8")} 2026-10-06T11:00:00Z 2026-10-06T12:00:00Z",
            ),
            changes.calls,
        )

        answer = json(Triple(8L, "2026-10-06T13:00:00Z", "2026-10-06T14:00:00Z"))
        app.trip()
        model.loadWeek(OCTOBER_5)
        val reloaded = shown(
            model,
        ) { it.entries.singleOrNull()?.start == Instant.parse("2026-10-06T13:00:00Z") }.entries.single()
        assertEquals(Instant.parse("2026-10-06T14:00:00Z"), reloaded.stop)
    }

    /** The timer edits the running entry, so no drag changes it, and nothing is created without a default workspace. */
    @Test
    fun theRunningEntryAndAnAccountWithoutAWorkspaceQueueNothing() {
        val model = model(
            cached = listOf(entry(5, "2026-10-25T09:00:00Z", stop = null)),
            settings = DRAG_SETTINGS.copy(defaultWorkspaceId = null),
        )
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
        val model = model(
            cached = listOf(cached),
            settings = DRAG_SETTINGS.copy(timeResolutionMinutes = 60.0),
            cache = flowOf(listOf(EntryWithSendState(cached, unsent = true))),
        )
        val block = shown(model) { true }.entries.single()
        assertEquals(cached.start to cached.stop, block.start to block.stop)
        assertEquals(true, block.unsent)
        assertEquals(emptyList<String>(), changes.calls)
    }

    /**
     * A no-time entry has no hour on the grid. It is in no block, sits in the All day map under its
     * day, and counts its full duration in that day's total and the week's.
     */
    @Test
    fun aNoTimeEntryIsOutOfTheBlocksInTheAllDayMapAndInTheTotals() {
        val timed = entry(4, "2026-10-20T09:00:00Z", "2026-10-20T10:00:00Z")
        val noTime = noTimeEntry(6, "2026-10-20T00:00:00Z", seconds = 5400)
        val model = model(cached = listOf(timed, noTime))

        val state = shown(model) { it.entries.size == 2 }
        val now = CLOCK.instant()

        assertEquals(
            listOf(4L),
            calendarBlocks(state.entries, state.weekDays, state.zone, now).map { it.entry.id.value },
        )
        assertEquals(
            mapOf(OCTOBER_20 to listOf(6L)),
            state.allDay.mapValues { (_, entries) -> entries.map { it.id.value } },
        )
        assertEquals(3600L + 5400L, dayTotalSeconds(state.entries, OCTOBER_20, state.zone, now))
        assertEquals(3600L + 5400L, state.weekDays.sumOf { dayTotalSeconds(state.entries, it, state.zone, now) })
    }

    @Test
    fun allDayEntriesKeepOnlyTheShownDaysInAStableOrder() {
        val first = noTimeCalendarEntry(9, OCTOBER_20)
        val second = noTimeCalendarEntry(3, OCTOBER_20)
        val other = noTimeCalendarEntry(5, OCTOBER_25)
        val outside = noTimeCalendarEntry(7, OCTOBER_5)
        val timed = noTimeCalendarEntry(
            8,
            OCTOBER_20,
        ).let { it.copy(timing = EntryTiming.Stopped(it.start, it.stop!!)) }

        val byDay = allDayEntries(
            listOf(first, other, outside, timed, second),
            listOf(OCTOBER_20, OCTOBER_25),
            ZoneOffset.UTC,
        )

        assertEquals(
            mapOf(OCTOBER_20 to listOf(3L, 9L), OCTOBER_25 to listOf(5L)),
            byDay.mapValues { (_, entries) -> entries.map { it.id.value } },
        )
    }

    /** A queued no-time entry shows the duration the server will store, from midnight of its day. */
    @Test
    fun anUnsentNoTimeEntryShowsASnappedDurationAtMidnight() {
        val queued = noTimeEntry(6, "2026-10-20T00:00:00Z", seconds = 4000, day = OCTOBER_20)
        val model = model(
            cached = listOf(queued),
            settings = DRAG_SETTINGS,
            cache = flowOf(listOf(EntryWithSendState(queued, unsent = true))),
        )

        val shown = shown(model) { true }.entries.single()

        assertEquals(
            Instant.parse("2026-10-20T00:00:00Z") to Instant.parse("2026-10-20T01:05:00Z"),
            shown.start to shown.stop,
        )
        assertEquals(OCTOBER_20, (shown.timing as EntryTiming.AllDay).day)
        assertEquals(true, shown.unsent)
    }

    /** A no-time entry has no time to drag, so a move or a resize queues nothing. */
    @Test
    fun aMoveAndAResizeLeaveANoTimeEntryAlone() {
        val noTime = noTimeEntry(6, "2026-10-20T00:00:00Z", seconds = 5400)
        val model = model(cached = listOf(noTime), settings = DRAG_SETTINGS)
        val block = shown(model) { true }.entries.single()

        model.move(block, OCTOBER_20, deltaMinutes = 60, dayMode = true)
        model.resize(block, ResizeEdge.END, deltaMinutes = 30)
        model.resize(block, ResizeEdge.START, deltaMinutes = -45)

        assertEquals(emptyList<String>(), changes.calls)
    }

    /** A drag that creates an entry still makes a timed one. */
    @Test
    fun aCreateDragStaysTimedWhenAllDayIsOffered() {
        val model = model(cached = emptyList(), settings = DRAG_SETTINGS, allDayOffered = true)

        model.create(OCTOBER_20, fromMinute = 600, toMinute = 690)

        assertEquals(listOf("create 7 ${EntryFields()} 2026-10-20T10:00:00Z 2026-10-20T11:30:00Z"), changes.calls)
    }

    @Test
    fun theAllDayRowIsOfferedOnlyWhenTheServerStoresNoTimeEntries() {
        assertEquals(false, shown(model(cached = emptyList())) { true }.allDayOffered)
        assertEquals(true, shown(model(cached = emptyList(), allDayOffered = true)) { true }.allDayOffered)
    }

    private val app = TestApp(TestCoroutineScheduler())

    private fun model(
        cached: List<TimeEntry>,
        settings: AccountSettings = SETTINGS,
        cache: Flow<List<EntryWithSendState>> = flowOf(cached.map { EntryWithSendState(it, unsent = false) }),
        io: CoroutineContext = Dispatchers.Unconfined,
        allDayOffered: Boolean = false,
    ): CalendarViewModel {
        val accountSettings = MutableStateFlow<AccountSettings?>(settings)
        val scope = CoroutineScope(
            Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, thrown -> escaped += thrown },
        )
        return CalendarViewModel(
            entries = sessionEntries(
                cache,
                accountSettings,
                scope,
                CLOCK,
                changes = changes,
                serverWeeks = ServerWeeks { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
                app = app.app,
                io = io,
            ),
            projects = flowOf(emptyList()),
            tasks = flowOf(emptyList()),
            accountSettings = accountSettings,
            changes = changes,
            scope = scope,
            computation = Dispatchers.Unconfined,
            clock = CLOCK,
            allDayOffered = flowOf(allDayOffered),
        )
    }

    private fun shown(model: CalendarViewModel, condition: (CalendarState.Shown) -> Boolean): CalendarState.Shown =
        runBlocking {
            withTimeout(
                TIMEOUT_MS,
            ) { model.state.first { it is CalendarState.Shown && condition(it) } as CalendarState.Shown }
        }

    private fun entry(id: Long, start: String, stop: String?) = TimeEntry(
        id = EntryId(id),
        workspaceId = WorkspaceId(1),
        description = "e$id",
        projectId = null,
        taskId = null,
        tagIds = emptyList(),
        billable = false,
        timing = EntryTiming.of(Instant.parse(start), stop?.let(Instant::parse), allDay = false, day = null),
        tags = emptyList(),
    )

    /**
     * A no-time entry as the server stores it: local midnight [start] and [seconds] long, without
     * a [day] unless a change on the phone made it.
     */
    private fun noTimeEntry(id: Long, start: String, seconds: Long, day: LocalDate? = null) =
        entry(id, start, null).copy(timing = EntryTiming.AllDay(day, Instant.parse(start), Duration.ofSeconds(seconds)))

    private fun noTimeCalendarEntry(id: Long, day: LocalDate) = CalendarEntry(
        id = EntryId(id),
        description = "e$id",
        project = null,
        projectColor = null,
        task = null,
        timing = EntryTiming.AllDay(day, day.atStartOfDay(ZoneOffset.UTC).toInstant(), Duration.ofHours(1)),
        unsent = false,
    )

    /** Stopped entries as `GET /api/v9/me/time_entries` lists them: id, start and stop. */
    private fun json(vararg entries: Triple<Long, String, String>) = entries.joinToString(
        ",",
        "[",
        "]",
    ) { (id, start, stop) ->
        """{"id":$id,"workspace_id":1,"description":"e$id","start":"$start","stop":"$stop","duration":60""" +
            ""","tag_ids":[],"billable":false}"""
    }

    /** Records what the calendar queues. */
    private class RecordingChanges : EntryChanges {
        val calls = CopyOnWriteArrayList<String>()

        /** What the phone's storage throws for each write, or null while it takes them. */
        @Volatile
        var refusal: Exception? = null

        override suspend fun create(
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ): EntryId {
            refusal?.let { throw it }
            val start = timing.start
            val stop = timing.stop
            calls += "create $workspaceId $fields $start $stop"
            return EntryId(LOCAL_ID)
        }

        override suspend fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean {
            refusal?.let { throw it }
            val start = timing.start
            val stop = (timing as? EntryTiming.Ended)?.stop
            calls += "update $entryId $fields $start $stop"
            return true
        }

        override suspend fun updateOlder(
            entryId: EntryId,
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ) {
            refusal?.let { throw it }
            val start = timing.start
            val stop = timing.stop
            calls += "updateOlder $entryId $workspaceId $fields $start $stop"
        }

        override suspend fun delete(entryId: EntryId): Boolean = throw AssertionError("the calendar deletes nothing")

        override suspend fun delete(entry: TimeEntry): TimeEntry? = throw AssertionError("unexpected delete")

        override suspend fun currentId(entryId: EntryId): EntryId = entryId
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val LOCAL_ID = -3L
        val SETTINGS = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null)

        /** Tokkl's default resolution of 5 minutes, so drags step by 15, and a default workspace. */
        val DRAG_SETTINGS = AccountSettings(
            "UTC",
            DayOfWeek.MONDAY,
            timeResolutionMinutes = 5.0,
            defaultWorkspaceId = WorkspaceId(7),
        )

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
