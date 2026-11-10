package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.api
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.CacheRefresh
import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.stopSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class ChangeSenderTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()
    private val store = InMemoryChangeStore()
    private val clock = MutableClock(TAP)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var url: ServerUrl
    private var signedIn = true
    private lateinit var sender: ChangeSender
    private lateinit var undo: DeleteUndoer

    @Before
    fun setUp() {
        server.start()
        url = ServerUrl.parse(server.url("/").toString())!!
        testApi.stored = url
        testApi.trust = InMemoryServerTrust(url)
        val client = ApiClientProvider { if (signedIn) testApi.api(url, ApiCredentials.ApiToken("tok-123")) else null }
        undo = DeleteUndoer(
            store,
            client.entries(),
            Dispatchers.IO,
            refreshes = AccountRefreshRequests(),
            scope = scope,
        )
        sender = changeSender(store, client, scope = scope, io = Dispatchers.IO, clock = clock, undo = undo)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    @Test
    fun sendsCreateEditStopAndDeleteOfAnOfflineEntryInOrderUnderTheServerId() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Offline")
        store.queue(ChangeKind.UPDATE, LOCAL, TAP, stop = null, description = "Edited")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(90), description = "Edited")
        store.queue(ChangeKind.DELETE, LOCAL, TAP, stop = TAP.plusSeconds(90), description = "Edited")
        store.queue(ChangeKind.CREATE, -9, TAP.plusSeconds(120), stop = TAP.plusSeconds(180), description = "Manual")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "Offline", stop = null))
        server.enqueue(entry(101, "Edited", stop = null))
        server.enqueue(entry(101, "Edited", stop = "2026-10-19T08:01:30+00:00"))
        server.enqueue(MockResponse.Builder().code(200).build())
        server.enqueue(entry(102, "Manual", stop = "2026-10-19T08:03:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 5), run { sender.drain() })

        val requests = List(6) { server.takeRequest() }
        assertEquals(
            listOf(
                "GET /api/v9/me/time_entries/current",
                "POST /api/v9/workspaces/7/time_entries",
                "PUT /api/v9/workspaces/7/time_entries/101",
                "PUT /api/v9/workspaces/7/time_entries/101",
                "DELETE /api/v9/workspaces/7/time_entries/101",
                "POST /api/v9/workspaces/7/time_entries",
            ),
            requests.map { "${it.method} ${it.url.encodedPath}" },
        )
        assertEquals(
            listOf(null, "Offline", "Edited", "Edited", null, "Manual"),
            requests.map { it.json()?.string("description") },
        )
        assertEquals(listOf(-1L to 101L, -9L to 102L), store.mapped)
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun aRunningCreateSendsTheTapTimeAsItsStartAndTagsByName() = runTest {
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            TAP.plusMillis(750),
            stop = null,
            description = "Offline",
            tags = listOf("focus", "new tag"),
        )
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "Offline", stop = null))

        run { sender.drain() }

        server.takeRequest()
        val body = server.takeRequest().json()!!
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"created_with":"Tokkl for Android","description":"Offline","project_id":3,""" +
                    """"task_id":null,"tags":["focus","new tag"],"billable":true,"start":"2026-10-19T08:00:00Z",""" +
                    """"stop":null,"duration":-1}""",
            ),
            body,
        )
    }

    @Test
    fun aLegacyQueuedStopStillSendsEveryFieldAfterMigration() = runTest {
        store.queue(ChangeKind.STOP, 55, TAP, stop = TAP.plusSeconds(90).plusMillis(400), description = "Running")
        server.enqueue(entry(55, "Running", stop = "2026-10-19T08:01:30+00:00"))

        run { sender.drain() }

        val request = server.takeRequest()
        assertEquals("PUT /api/v9/workspaces/7/time_entries/55", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"description":"Running","project_id":3,"task_id":null,"tags":[],""" +
                    """"billable":true,"start":"2026-10-19T08:00:00Z","stop":"2026-10-19T08:01:30Z","duration":90}""",
            ),
            request.json(),
        )
    }

    @Test
    fun aCapturedStopUsesTheFrozenTapInstantAfterTheClockMoves() = runTest {
        val captured = TAP.plusSeconds(90).plusMillis(400)
        store.queue(
            ChangeKind.STOP,
            55,
            TAP,
            stop = TAP.plusSeconds(90),
            description = "Running",
            capturedStop = captured,
        )
        clock.now = TAP.plusSeconds(600)
        server.enqueue(entry(55, "Running", stop = "2026-10-19T08:01:30+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(1), run { sender.drain() })

        val request = server.takeRequest()
        assertEquals("PATCH /api/v9/workspaces/7/time_entries/55/stop", "${request.method} ${request.url.encodedPath}")
        assertEquals(Json.parseToJsonElement("""{"stop":"2026-10-19T08:01:30Z"}"""), request.json())
    }

    @Test
    fun anUncertainCapturedStopReadsTheMatchingRowInsteadOfReplayingPatch() = runTest {
        store.queue(
            ChangeKind.STOP,
            55,
            TAP,
            TAP.plusSeconds(90),
            "Running",
            maybeSent = true,
            capturedStop = TAP.plusSeconds(90),
        )
        server.enqueue(entry(55, "Running", stop = "2026-10-19T08:01:30+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(1), run { sender.drain() })
        val request = server.takeRequest()
        assertEquals("GET /api/v9/me/time_entries/55", "${request.method} ${request.url.encodedPath}")
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun anUncertainShortStopRequiresTheExactTrashRow() = runTest {
        store.queue(
            ChangeKind.STOP,
            55,
            TAP,
            TAP,
            "Short",
            maybeSent = true,
            capturedStop = TAP.plusSeconds(2),
            shortStop = true,
        )
        server.enqueue(MockResponse.Builder().code(404).body("\"Not found\"").build())
        server.enqueue(
            entries(
                """{"id":55,"start":"2026-10-19T08:00:00+00:00","stop":"2026-10-19T08:00:00+00:00",""" +
                    """"server_deleted_at":"2026-10-19T08:00:02Z"}""",
            ),
        )

        assertEquals(ChangeSender.Outcome.Empty(1), run { sender.drain() })
        assertEquals(
            listOf("GET /api/v9/me/time_entries/55", "GET /api/app/trash"),
            List(2) { server.takeRequest() }.map { "${it.method} ${it.url.encodedPath}" },
        )
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun anAlreadyStoppedConflictWithDifferentSpanIsHeld() = runTest {
        store.queue(ChangeKind.STOP, 55, TAP, TAP.plusSeconds(90), "Running", capturedStop = TAP.plusSeconds(90))
        server.enqueue(MockResponse.Builder().code(409).body("\"Time entry already stopped\"").build())
        server.enqueue(entry(55, "Running", stop = "2026-10-19T08:05:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(0), run { sender.drain() })
        assertTrue(store.changes.single().held)
        assertEquals(listOf("PATCH", "GET"), List(2) { server.takeRequest().method })
    }

    @Test
    fun aDeleteSendsNoBody() = runTest {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(60), description = "Gone")
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(0L, request.bodySize)
    }

    /**
     * Tokkl's no-time create: the stored day as `start_date` and the duration, and no times. The
     * stored midnight is Madrid's, which falls on the day before in UTC.
     */
    @Test
    fun aNoTimeCreateSendsItsDayAndDurationAndNoTimes() = runTest {
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            MIDNIGHT,
            stop = MIDNIGHT.plusSeconds(5400),
            description = "Workshop",
            allDay = true,
            day = DAY,
        )
        server.enqueue(entry(101, "Workshop", stop = "2026-10-19T09:30:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { noTimeSender().drain() })

        val request = server.takeRequest()
        assertEquals("POST /api/v9/workspaces/7/time_entries", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"created_with":"Tokkl for Android","description":"Workshop","project_id":3""" +
                    ""","task_id":null,""" +
                    """"tags":[],"billable":true,"all_day":true,"start_date":"2026-10-19","duration":5400}""",
            ),
            request.json(),
        )
    }

    /**
     * Editing a no-time entry and turning a timed one into one send the same PUT. Tokkl refuses a
     * `start` that is not local midnight and a `stop` that is not the anchored stop, so neither goes.
     */
    @Test
    fun aNoTimeEditAndTurningNoTimeOnSendTheDayAndDurationWithoutTimes() = runTest {
        store.queue(
            ChangeKind.UPDATE,
            55,
            MIDNIGHT,
            stop = MIDNIGHT.plusSeconds(3600),
            description = "Renamed",
            allDay = true,
            day = DAY,
        )
        val nextMidnight = MIDNIGHT.plusSeconds(86_400)
        store.queue(
            ChangeKind.UPDATE,
            56,
            nextMidnight,
            stop = nextMidnight.plusSeconds(7200),
            description = "Was timed",
            allDay = true,
            day = DAY.plusDays(1),
        )
        server.enqueue(entry(55, "Renamed", stop = "2026-10-19T09:00:00+00:00"))
        server.enqueue(entry(56, "Was timed", stop = "2026-10-19T10:00:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { noTimeSender().drain() })

        val (edit, turnedOn) = List(2) { server.takeRequest() }
        assertEquals("PUT /api/v9/workspaces/7/time_entries/55", "${edit.method} ${edit.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"description":"Renamed","project_id":3,"task_id":null,"tags":[]""" +
                    ""","billable":true,""" +
                    """"all_day":true,"start_date":"2026-10-19","duration":3600}""",
            ),
            edit.json(),
        )
        assertEquals("PUT /api/v9/workspaces/7/time_entries/56", "${turnedOn.method} ${turnedOn.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"description":"Was timed","project_id":3,"task_id":null,"tags":[]""" +
                    ""","billable":true,""" +
                    """"all_day":true,"start_date":"2026-10-20","duration":7200}""",
            ),
            turnedOn.json(),
        )
    }

    /** Without a stored day, the change sends its local midnight, from which Tokkl takes the day. */
    @Test
    fun aNoTimeChangeWithoutADaySendsItsMidnightInsteadOfStartDate() = runTest {
        store.queue(
            ChangeKind.UPDATE,
            55,
            MIDNIGHT,
            stop = MIDNIGHT.plusSeconds(3600),
            description = "Renamed",
            allDay = true,
        )
        server.enqueue(entry(55, "Renamed", stop = "2026-10-19T09:00:00+00:00"))

        run { noTimeSender().drain() }

        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"description":"Renamed","project_id":3,"task_id":null,"tags":[]""" +
                    ""","billable":true,""" +
                    """"all_day":true,"start":"2026-10-18T22:00:00Z","duration":3600}""",
            ),
            server.takeRequest().json(),
        )
    }

    /**
     * With no-time entries on, a timed change says so. Tokkl turns a no-time entry off only with
     * `all_day: false` and both its start and its stop in the same PUT.
     */
    @Test
    fun timedChangesSendAllDayFalseAndTurningNoTimeOffSendsStartAndStopInOnePut() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(5400), description = "Timed again")
        store.queue(ChangeKind.CREATE, LOCAL, TAP.plusSeconds(7200), stop = null, description = "Running")
        server.enqueue(entry(55, "Timed again", stop = "2026-10-19T09:30:00+00:00"))
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "Running", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { noTimeSender().drain() })

        val (turnedOff, _, start) = List(3) { server.takeRequest() }
        assertEquals("PUT /api/v9/workspaces/7/time_entries/55", "${turnedOff.method} ${turnedOff.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"description":"Timed again","project_id":3,"task_id":null,"tags":[]""" +
                    ""","billable":true,""" +
                    """"start":"2026-10-19T08:00:00Z","stop":"2026-10-19T09:30:00Z","duration":5400,"all_day":false}""",
            ),
            turnedOff.json(),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"created_with":"Tokkl for Android","description":"Running","project_id":3""" +
                    ""","task_id":null,""" +
                    """"tags":[],"billable":true,"start":"2026-10-19T10:00:00Z","stop":null,"duration":-1""" +
                    ""","all_day":false}""",
            ),
            start.json(),
        )
    }

    /** Toggl Track, and Tokkl before 1.11.0, never get `all_day`: every change goes as it always did. */
    @Test
    fun withNoTimeEntriesOffNoRequestCarriesAllDay() = runTest {
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            MIDNIGHT,
            stop = MIDNIGHT.plusSeconds(5400),
            description = "Workshop",
            allDay = true,
            day = DAY,
        )
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        server.enqueue(entry(101, "Workshop", stop = "2026-10-19T09:30:00+00:00"))
        server.enqueue(entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })

        val (create, update) = List(2) { server.takeRequest() }
        assertEquals(
            Json.parseToJsonElement(
                """{"workspace_id":7,"created_with":"Tokkl for Android","description":"Workshop","project_id":3""" +
                    ""","task_id":null,""" +
                    """"tags":[],"billable":true""" +
                    ""","start":"2026-10-18T22:00:00Z","stop":"2026-10-18T23:30:00Z","duration":5400}""",
            ),
            create.json(),
        )
        assertFalse(update.json().toString(), "all_day" in update.json()!!)
    }

    @Test
    fun theServersAnswerReachesTheStore() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        server.enqueue(entry(55, "Edited on the server", stop = "2026-10-19T08:05:00+00:00"))

        run { sender.drain() }

        assertEquals("Edited on the server", store.answers.single().description)
        assertEquals(Instant.parse("2026-10-19T08:05:00Z"), store.answers.single().stop)
    }

    @Test
    fun aNetworkFailureStopsTheDrainAndKeepsTheChangeForTheNextDrain() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        // The start's read of the running entry is the drain's first request. A dropped connection
        // gets one more try at once, so it drops twice.
        repeat(2) { server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }

        val outcome = run { sender.drain() }

        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.Network)
        assertEquals(listOf(ChangeKind.CREATE, ChangeKind.STOP), store.changes.map { it.kind })

        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })
        val sent = remainingRequests().takeLast(2)
        assertEquals(
            listOf("POST /api/v9/workspaces/7/time_entries", "PUT /api/v9/workspaces/7/time_entries/101"),
            sent.map { "${it.method} ${it.url.encodedPath}" },
        )
    }

    @Test
    fun aChangeWhosePooledConnectionTheServerClosedGoesOutOnANewOneInTheSameDrain() = runTest {
        for ((kind, method) in listOf(
            ChangeKind.UPDATE to "PUT",
            ChangeKind.DELETE to "DELETE",
            ChangeKind.CREATE to "POST",
        )) {
            poolAConnectionTheServerCloses()
            store.queue(
                kind,
                if (kind == ChangeKind.CREATE) LOCAL else 55,
                TAP,
                stop = TAP.plusSeconds(60),
                description = "Edited",
            )
            // A create that may have reached the server looks for the entry it made first.
            if (kind == ChangeKind.CREATE) server.enqueue(entries())
            server.enqueue(
                if (kind == ChangeKind.DELETE) {
                    MockResponse.Builder().code(
                        200,
                    ).build()
                } else {
                    entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00")
                },
            )

            assertEquals(kind.name, ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

            assertEquals(kind.name, 1, remainingRequests().count { it.method == method })
            assertTrue(kind.name, store.changes.isEmpty())
        }
    }

    @Test
    fun aConnectionThatDropsAfterTheServerAnsweredLeadsToOneMoreTryAtMost() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        // The server reads the update, then the connection drops before the answer, twice.
        repeat(2) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }

        val outcome = run { sender.drain() }

        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.Network)
        assertEquals(
            "the first try and one more, each once",
            listOf("PUT", "PUT"),
            remainingRequests().map { it.method },
        )
        assertEquals(listOf(ChangeKind.UPDATE), store.changes.map { it.kind })
    }

    @Test
    fun onlyAConnectionThatFailedAfterTheRequestLeftThePhoneIsWorthAnotherTryAtOnce() {
        val again = listOf(
            ApiError.Network(java.io.IOException("unexpected end of stream on http://127.0.0.1/...")),
            ApiError.Network(java.io.EOFException("\\n not found: limit=0")),
            ApiError.Network(java.net.SocketException("Connection reset")),
        )
        val not = listOf(
            ApiError.Network(java.net.SocketTimeoutException("timeout")),
            ApiError.Network(java.io.InterruptedIOException("timeout")),
            ApiError.Network(java.net.ConnectException("refused")),
            ApiError.Network(java.net.UnknownHostException("tokkl.example")),
            ApiError.Network(java.net.NoRouteToHostException("no route")),
            ApiError.UnexpectedStatus(500),
            ApiError.UnexpectedStatus(404),
            ApiError.AuthFailed,
            ApiError.RateLimited(30),
            ApiError.InvalidResponse(IllegalArgumentException("not json")),
            ApiError.HostNotAllowed("https://elsewhere.example"),
        )
        assertEquals(again.map { true }, again.map { it.worthAnotherTryAtOnce() })
        assertEquals(not.map { false }, not.map { it.worthAnotherTryAtOnce() })
    }

    @Test
    fun a5xxStopsTheDrainAndTheNextDrainResumesInOrder() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP.plusSeconds(10), stop = null, description = "Second")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())

        val outcome = run { sender.drain() }

        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)), outcome)
        assertEquals("nothing after the failed change was sent", 2, server.requestCount)
        assertEquals(listOf(LOCAL, -2L), store.changes.map { it.entryId })

        // A 503 may come after the server stored the create, so the next drain looks for it first.
        server.enqueue(entries())
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })
        List(2) { server.takeRequest() }
        assertEquals(
            listOf("First", "Second"),
            List(5) { server.takeRequest() }.filter { it.method == "POST" }.map { it.json()!!.string("description") },
        )
    }

    @Test
    fun a400HoldsTheChangeWithTheServersMessage() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        server.enqueue(MockResponse.Builder().code(400).body("\"Invalid project\"").build())

        assertEquals(ChangeSender.Outcome.Empty(sent = 0), run { sender.drain() })
        assertEquals(1, store.changes.size)
        assertTrue(store.changes.single().held)
        assertEquals("Invalid project", store.changes.single().refusal)
    }

    @Test
    fun a409OnAStopLeavesTheQueueSilently() = runTest {
        store.queue(ChangeKind.STOP, 55, TAP, stop = TAP.plusSeconds(60), description = "Stopped elsewhere")
        server.enqueue(MockResponse.Builder().code(409).build())

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })
        assertTrue(store.changes.isEmpty())
        assertTrue(store.answers.isEmpty())
        assertFalse(sender.signInNeeded.value)
    }

    @Test
    fun a404DiscardsEveryChangeToTheMissingEntryAndContinues() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Gone")
        store.queue(ChangeKind.STOP, 55, TAP, stop = TAP.plusSeconds(90), description = "Gone")
        store.queue(ChangeKind.UPDATE, 56, TAP, stop = TAP.plusSeconds(60), description = "Other")
        var discarded = 0
        sender = changeSender(
            store,
            { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope,
            Dispatchers.IO,
            onDiscarded = { discarded += it },
        )
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(entry(56, "Other", stop = "2026-10-19T08:01:00Z"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 3), run { sender.drain() })
        assertEquals(2, discarded)
        assertTrue(store.changes.isEmpty())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun a404ForAValueDoesNotDiscardAnEntryTheServerStillHas() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Keep me")
        server.enqueue(MockResponse.Builder().code(404).body("\"Project not found\"").build())
        server.enqueue(entry(55, "Keep me", stop = "2026-10-19T08:01:00Z"))
        assertEquals(ChangeSender.Outcome.Empty(0), run { sender.drain() })
        assertTrue(store.changes.single().held)
        assertEquals("Project not found", store.changes.single().refusal)
    }

    @Test
    fun a404LookupPreservesAnAuthenticationFailureOrARetryableFailure() = runTest {
        for (status in listOf(503, 429, 403)) {
            store.changes.clear()
            store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Keep me")
            server.enqueue(MockResponse.Builder().code(404).build())
            server.enqueue(MockResponse.Builder().code(status).addHeader("Retry-After", "30").build())
            val result = run { sender.drain() }
            when (status) {
                503 -> assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.UnexpectedStatus(503)), result)

                429 -> {
                    assertEquals(ChangeSender.Outcome.Waiting(0, TAP.plusSeconds(30)), result)
                    clock.now = TAP.plusSeconds(30)
                }

                403 -> {
                    assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), result)
                    assertTrue(sender.signInNeeded.value)
                }
            }
            assertEquals(1, store.changes.size)
            assertFalse(store.changes.single().held)
        }
    }

    @Test
    fun a400BlocksOnlyItsEntryAndAReleasedChangeCanGoAgain() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Bad")
        store.queue(ChangeKind.STOP, 55, TAP, stop = TAP.plusSeconds(90), description = "Later")
        store.queue(ChangeKind.UPDATE, 56, TAP, stop = TAP.plusSeconds(60), description = "Other")
        server.enqueue(MockResponse.Builder().code(400).body("\"Bad value\"").build())
        server.enqueue(entry(56, "Other", stop = "2026-10-19T08:01:00Z"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })
        assertEquals(listOf(55L, 55L), store.changes.map { it.entryId })
        assertEquals("Bad value", store.changes.first().refusal)
        assertEquals(2, server.requestCount)
        store.changes.replaceAll { it.copy(held = false, refusal = null) }
        server.enqueue(entry(55, "Fixed", stop = "2026-10-19T08:01:00Z"))
        server.enqueue(entry(55, "Later", stop = "2026-10-19T08:01:30Z"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })
    }

    @Test
    fun a403PausesAllRequestsUntilSignInResumesTheQueue() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "First")
        store.queue(ChangeKind.UPDATE, 56, TAP, stop = TAP.plusSeconds(60), description = "Second")
        server.enqueue(MockResponse.Builder().code(403).build())
        assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), run { sender.drain() })
        assertTrue(sender.signInNeeded.value)
        assertEquals(ChangeSender.Outcome.Stopped(0, ApiError.AuthFailed), run { sender.drain() })
        assertEquals(1, server.requestCount)
        assertEquals(2, store.changes.size)

        server.enqueue(entry(55, "First", stop = "2026-10-19T08:01:00Z"))
        server.enqueue(entry(56, "Second", stop = "2026-10-19T08:01:00Z"))
        sender.resume()
        run { sender.sendAndWait() }
        assertFalse(sender.signInNeeded.value)
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun a429WaitsForRetryAfterBeforeTheNextTry() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())

        assertEquals(
            ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plusSeconds(30)),
            run { sender.drain() },
        )

        clock.now = TAP.plusSeconds(29)
        assertEquals(
            ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plusSeconds(30)),
            run { sender.drain() },
        )
        assertEquals("no request before Retry-After has passed", 2, server.requestCount)
        assertEquals(2, store.changes.size)

        clock.now = TAP.plusSeconds(30)
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })
        List(2) { server.takeRequest() }
        assertEquals(
            listOf(
                "GET /api/v9/me/time_entries/current",
                "POST /api/v9/workspaces/7/time_entries",
                "PUT /api/v9/workspaces/7/time_entries/101",
            ),
            List(3) { server.takeRequest() }.map { "${it.method} ${it.url.encodedPath}" },
        )
    }

    @Test
    fun a429WithoutRetryAfterWaitsTheDefault() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(429).build())

        assertEquals(
            ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plus(RetryAfter.DEFAULT)),
            run { sender.drain() },
        )
    }

    @Test
    fun sendTriesAgainByItselfOnceRetryAfterHasPassed() = runTest {
        val realTime = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "1").build())
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))

        realTime.send()

        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        val started = System.nanoTime()
        assertNotNull("the retry came", server.takeRequest(5, TimeUnit.SECONDS))
        assertTrue("the retry waited about a second", System.nanoTime() - started > TimeUnit.MILLISECONDS.toNanos(900))
        // The retry's drain is the one running, so this joins it.
        run { realTime.sendAndWait() }
        assertTrue("the retry sent the change", store.changes.isEmpty())
    }

    @Test
    fun signedOutSendsNothing() = runTest {
        signedIn = false
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")

        assertEquals(ChangeSender.Outcome.SignedOut(sent = 0), run { sender.drain() })
        assertEquals(0, server.requestCount)
        assertEquals(1, store.changes.size)
    }

    @Test
    fun aChangeQueuedDuringADrainIsSentByThatDrain() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        store.onFirstSent = { store.queue(ChangeKind.CREATE, -2, TAP, stop = null, description = "Second") }

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })
        assertNull(store.first())
    }

    /**
     * A send() call during a drain makes it drain once more before it ends. Here the first pass sends
     * one change and stops at a 503, and the second pass sends the rest, so the job takes two
     * passes. onSent runs once for the job, after the second pass has sent its change.
     */
    @Test
    fun onSentRunsOncePerDrainJobAfterItsLastPass() = runTest {
        val calls = mutableListOf<String>()
        lateinit var withOnSent: ChangeSender
        withOnSent = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onSent = {
                synchronized(
                    calls,
                ) { calls += "onSent after ${server.requestCount} requests, ${store.changes.size} queued" }
            },
            clock = clock,
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP, stop = null, description = "Second")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(entries())
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        store.onFirstSent = { withOnSent.send() }

        run { withOnSent.sendAndWait() }

        assertEquals(listOf("onSent after 7 requests, 0 queued"), synchronized(calls) { calls.toList() })
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun aDrainThatSendsNothingDoesNotRunOnSent() = runTest {
        var calls = 0
        val withOnSent = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onSent = { calls++ },
            clock = clock,
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())

        run { withOnSent.sendAndWait() }

        assertEquals(0, calls)
        assertEquals(1, store.changes.size)
    }

    /**
     * A drain that sends the queue and leaves it empty runs onEmptied once, before onSent, so the
     * app can cancel the background send's waiting retry. One that stops at a failure or sends
     * nothing does not.
     */
    @Test
    fun onEmptiedRunsOnceWhenADrainSendsTheQueueEmpty() = runTest {
        val calls = mutableListOf<String>()
        val withOnEmptied = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onSent = { synchronized(calls) { calls += "onSent" } },
            onEmptied = { synchronized(calls) { calls += "onEmptied with ${store.changes.size} queued" } },
            clock = clock,
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP, stop = null, description = "Second")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())

        run { withOnEmptied.sendAndWait() }
        assertEquals("a drain that stops at a failure", listOf("onSent"), synchronized(calls) { calls.toList() })

        server.enqueue(entries())
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        run { withOnEmptied.sendAndWait() }
        assertEquals(listOf("onSent", "onEmptied with 0 queued", "onSent"), synchronized(calls) { calls.toList() })

        run { withOnEmptied.sendAndWait() }
        assertEquals("a drain of an empty queue sends nothing", 3, synchronized(calls) { calls.size })
    }

    @Test
    fun aSendDuringQueueCleanupJoinsTheDrainUntilCancellationFinishes() = runTest {
        val cleaning = CountDownLatch(1)
        val finishCleanup = CountDownLatch(1)
        val withCleanup = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onEmptied = {
                cleaning.countDown()
                assertTrue("cleanup was released", finishCleanup.await(10, TimeUnit.SECONDS))
            },
            clock = clock,
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))

        val draining = scope.async { withCleanup.sendAndWait() }
        try {
            assertTrue("the queue emptied and cleanup started", cleaning.await(10, TimeUnit.SECONDS))
            store.queue(ChangeKind.UPDATE, 101, TAP, stop = TAP.plusSeconds(60), description = "Edited during cleanup")
            server.enqueue(entry(101, "Edited during cleanup", stop = "2026-10-19T08:01:00+00:00"))
            withCleanup.send()
            assertFalse("the drain includes its cleanup", draining.isCompleted)
        } finally {
            finishCleanup.countDown()
            run { draining.await() }
        }
        assertTrue("the joined drain also sends changes queued during cleanup", store.changes.isEmpty())
        assertEquals("the create and the later edit each reach the server", 2, server.requestCount)
    }

    /**
     * The background send learns how the drain ended. A 503 may come after the server stored the
     * create, so the next drain looks for it before posting it again.
     */
    @Test
    fun sendAndWaitReturnsHowTheDrainEnded() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(
            ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)),
            run { sender.sendAndWait() },
        )
        assertEquals(1, store.changes.size)

        server.enqueue(entries())
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.sendAndWait() })
        assertTrue(store.changes.isEmpty())
    }

    /**
     * A sendAndWait call during a drain joins it and returns how its last pass ended, not the pass
     * it came in. Here the first pass stops at a 503 and the second sends the rest.
     */
    @Test
    fun sendAndWaitDuringADrainReturnsItsLastPass() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP, stop = TAP.plusSeconds(60), description = "Second")
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(entries())
        server.enqueue(entry(102, "Second", stop = "2026-10-19T08:01:00+00:00"))
        var joined: Deferred<ChangeSender.Outcome?>? = null
        store.onFirstSent = { joined = scope.async(start = CoroutineStart.UNDISPATCHED) { sender.sendAndWait() } }

        run { sender.sendAndWait() }

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { checkNotNull(joined).await() })
        assertEquals(4, server.requestCount)
        assertTrue(store.changes.isEmpty())
    }

    /**
     * Sign-out's stop waits for the drain that runs, so nothing that drain writes lands after the
     * cache is cleared. The drain here is held after its first change until stop has had time to
     * return.
     */
    @Test
    fun stopWaitsForTheRunningDrainToEnd() {
        val sending = CountDownLatch(1)
        val release = CountDownLatch(1)
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP, stop = TAP.plusSeconds(60), description = "Second")
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        server.enqueue(entry(102, "Second", stop = "2026-10-19T08:01:00+00:00"))
        store.onFirstSent = {
            sending.countDown()
            assertTrue("the drain was released", release.await(10, TimeUnit.SECONDS))
        }

        sender.send()
        assertTrue("the drain sent its first change", sending.await(10, TimeUnit.SECONDS))
        val stopping = scope.async { sender.stop() }
        try {
            // A real thread holds the drain, so this waits in real time.
            val early = runBlocking { withTimeoutOrNull(300) { stopping.await() } }
            assertNull("stop returned while the drain still ran", early)
        } finally {
            release.countDown()
        }
        runBlocking { stopping.await() }

        assertTrue("the drain had ended when stop returned", store.changes.isEmpty())
    }

    /** Stop forgets the wait after a 429 and the paused sign-in, so the next account sends at once. */
    @Test
    fun stopForgetsTheWaitAfterA429AndThePausedSignIn() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        assertEquals(
            ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plusSeconds(30)),
            run { sender.sendAndWait() },
        )
        sender.signInRefused()

        run { sender.stop() }

        assertFalse(sender.signInNeeded.value)
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })
    }

    @Test
    fun aQueuedStartGoesStoppedWhereALaterRunningTimerStartsAndThatTimerStaysInTheCache() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP.plusMillis(400), stop = null, description = "Phone")
        server.enqueue(runs(900, start = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:30:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        val requests = List(2) { server.takeRequest() }
        assertEquals(
            "one GET of the running entry, then the start",
            listOf("GET /api/v9/me/time_entries/current", "POST /api/v9/workspaces/7/time_entries"),
            requests.map { "${it.method} ${it.url.encodedPath}" },
        )
        val body = requests[1].json()!!
        assertEquals("2026-10-19T08:00:00Z", body.string("start"))
        assertEquals("2026-10-19T08:30:00Z", body.string("stop"))
        assertEquals("1800", body.string("duration"))
        assertEquals("the server's running entry goes to the cache", listOf(900L), store.later.map { it.id })
        assertEquals("no more requests", 2, server.requestCount)
    }

    @Test
    fun aQueuedStartGoesAsQueuedWhenNothingRuns() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "Phone", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        assertEquals(
            "GET /api/v9/me/time_entries/current",
            server.takeRequest().let { "${it.method} ${it.url.encodedPath}" },
        )
        val body = server.takeRequest().json()!!
        assertTrue(body["stop"] is JsonNull)
        assertEquals("-1", body.string("duration"))
        assertEquals(2, server.requestCount)
        assertTrue(store.later.isEmpty())
    }

    @Test
    fun aQueuedStartGoesAsQueuedWhenTheRunningTimerStartedEarlierOrAtTheSameSecond() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "After an earlier timer")
        store.queue(
            ChangeKind.CREATE,
            -2,
            TAP.plusSeconds(60).plusMillis(900),
            stop = null,
            description = "At the same second",
        )
        server.enqueue(runs(900, start = "2026-10-19T07:00:00+00:00"))
        server.enqueue(entry(101, "After an earlier timer", stop = null))
        server.enqueue(runs(901, start = "2026-10-19T08:01:00+00:00"))
        server.enqueue(entry(102, "At the same second", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })

        val posts = List(4) { server.takeRequest() }.filter { it.method == "POST" }
        assertTrue(posts.all { it.json()!!["stop"] is JsonNull })
        assertEquals(4, server.requestCount)
        assertTrue(store.later.isEmpty())
    }

    @Test
    fun aQueuedStopBeforeTheLaterTimerEndsTheStartInstead() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(600), description = "Phone")
        server.enqueue(runs(900, start = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:10:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:10:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })

        val requests = List(3) { server.takeRequest() }
        assertEquals(
            listOf("2026-10-19T08:10:00Z", "2026-10-19T08:10:00Z"),
            requests.drop(1).map { it.json()!!.string("stop") },
        )
    }

    @Test
    fun laterChangesThatWouldRunOrStopTheEntryAfterTheLaterTimerStopItWhereThatTimerStarts() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        store.queue(ChangeKind.UPDATE, LOCAL, TAP, stop = null, description = "Edited while it ran")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(2700), description = "Edited while it ran")
        store.queue(
            ChangeKind.UPDATE,
            LOCAL,
            TAP.plusSeconds(3600),
            stop = null,
            description = "Moved after the later timer",
        )
        server.enqueue(runs(900, start = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Edited while it ran", stop = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Edited while it ran", stop = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Moved after the later timer", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 4), run { sender.drain() })

        val bodies = List(5) { server.takeRequest() }.drop(1).map { it.json()!! }
        assertEquals(
            listOf("2026-10-19T08:30:00Z", "2026-10-19T08:30:00Z", "2026-10-19T08:30:00Z", null),
            bodies.map { (it["stop"] as? JsonPrimitive)?.takeIf { stop -> stop.isString }?.content },
        )
    }

    @Test
    fun aQueuedStartAndItsLaterChangesEndOnTheGridWhereTheLaterTimersStartSnaps() = runTest {
        val gridded = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            clock = clock,
            spans = object : ServerSpans {
                override fun switched(start: Instant, stop: Instant) = stopSpan(
                    start,
                    stop,
                    15.0,
                    ZoneId.of("UTC"),
                    oneStep = false,
                )
            },
        )
        // 08:08 snaps to 08:15 and the later timer's 08:20 to 08:15 too, so the phone's entry is
        // stored with no length. Sent as 08:08 to 08:20, Tokkl would store 08:15 to 08:30.
        store.queue(ChangeKind.CREATE, LOCAL, TAP.plusSeconds(480).plusMillis(400), stop = null, description = "Phone")
        store.queue(
            ChangeKind.STOP,
            LOCAL,
            TAP.plusSeconds(480).plusMillis(400),
            stop = TAP.plusSeconds(2700),
            description = "Phone",
        )
        server.enqueue(runs(900, start = "2026-10-19T08:20:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:15:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:15:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { gridded.drain() })

        val bodies = List(3) { server.takeRequest() }.drop(1).map { it.json()!! }
        for (body in bodies) {
            assertEquals("2026-10-19T08:15:00Z", body.string("start"))
            assertEquals("2026-10-19T08:15:00Z", body.string("stop"))
            assertEquals("0", body.string("duration"))
        }
    }

    @Test
    fun aFailedReadOfTheRunningEntryKeepsTheStartQueuedAndSendsNothing() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(
            ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)),
            run { sender.drain() },
        )

        assertEquals(1, server.requestCount)
        assertEquals(listOf(ChangeKind.CREATE), store.changes.map { it.kind })
        assertNull(store.changes.single().stop)
    }

    @Test
    fun aStoppedCreateReadsNoRunningEntry() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aCreateWhoseAnswerIsLostStaysMarkedAndOkHttpDoesNotPostItAgain() = runTest {
        // The update leaves a connection in OkHttp's pool, which the create then reuses.
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"))
        // The server reads the create, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        // The drain's second try looks for the entry the post made, and that read fails.
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:01:00+00:00"))

        val outcome = run { sender.drain() }

        assertEquals(ChangeSender.Outcome.Stopped(sent = 1, ApiError.UnexpectedStatus(503)), outcome)
        assertEquals("the create went out once", listOf("PUT", "POST", "GET"), remainingRequests().map { it.method })
        assertTrue("the server may hold it", store.changes.single().maybeSent)
    }

    @Test
    fun aCreateWhoseAnswerIsLostIsLookedUpInTheSameDrainInsteadOfPostedAgain() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"))
        // The server reads the create, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(
            entries(listed(101, "Manual", start = "2026-10-19T08:00:00+00:00", stop = "2026-10-19T08:01:00+00:00")),
        )

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })

        assertEquals("one post, then the lookup", listOf("PUT", "POST", "GET"), remainingRequests().map { it.method })
        assertEquals(listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun theNextDrainTakesTheEntryALostCreateMadeAndSendsLaterChangesToIt() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual", maybeSent = true)
        store.queue(ChangeKind.UPDATE, LOCAL, TAP, stop = TAP.plusSeconds(120), description = "Edited")
        server.enqueue(
            entries(
                listed(100, "Manual", start = "2026-10-19T08:00:00+00:00", project = 4),
                listed(101, "Manual", start = "2026-10-19T08:00:00+00:00"),
            ),
        )
        server.enqueue(entry(101, "Edited", stop = "2026-10-19T08:02:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })

        val (lookup, edit) = List(2) { server.takeRequest() }
        assertEquals("GET /api/v9/me/time_entries", "${lookup.method} ${lookup.url.encodedPath}")
        assertEquals("2026-10-19T07:00:00Z", lookup.url.queryParameter("start_date"))
        assertEquals("2026-10-19T09:00:00Z", lookup.url.queryParameter("end_date"))
        assertEquals("PUT /api/v9/workspaces/7/time_entries/101", "${edit.method} ${edit.url.encodedPath}")
        assertEquals("the matching entry, not the one in another project", listOf(LOCAL to 101L), store.mapped)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aLostDuplicateIsPostedInsteadOfTakingItsSource() = runTest {
        // A duplicate has every field and time of its source, entry 100, which the server held
        // when the phone queued the copy, so the lookup for a lost copy must leave it out.
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            TAP,
            stop = TAP.plusSeconds(1800),
            description = "Manual",
            maybeSent = true,
            existingIds = listOf(100),
        )
        server.enqueue(entries(listed(100, "Manual", start = "2026-10-19T08:00:00+00:00")))
        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:30:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        assertEquals("a lookup and a post", 2, server.requestCount)
        val (lookup, post) = List(2) { server.takeRequest() }
        assertEquals("GET /api/v9/me/time_entries", "${lookup.method} ${lookup.url.encodedPath}")
        assertEquals("POST /api/v9/workspaces/7/time_entries", "${post.method} ${post.url.encodedPath}")
        assertEquals("the copy, not its source", listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun aLostDuplicateTakesTheCopyItsPostMadeBesideItsSource() = runTest {
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            TAP,
            stop = TAP.plusSeconds(1800),
            description = "Manual",
            maybeSent = true,
            existingIds = listOf(100),
        )
        server.enqueue(
            entries(
                listed(100, "Manual", start = "2026-10-19T08:00:00+00:00"),
                listed(101, "Manual", start = "2026-10-19T08:00:00+00:00"),
            ),
        )

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        assertEquals(listOf(LOCAL to 101L), store.mapped)
        assertEquals("no second post", 1, server.requestCount)
    }

    @Test
    fun aLostCreateMatchesTheStartWhereTheServerSnappedIt() = runTest {
        val snapping = changeSender(
            store,
            client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            clock = clock,
            spans = object : ServerSpans {
                override fun start(start: Instant): Instant = start.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
            },
        )
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            TAP.plusSeconds(120).plusMillis(300),
            stop = TAP.plusSeconds(600),
            description = "Manual",
            maybeSent = true,
        )
        server.enqueue(entries(listed(101, "Manual", start = "2026-10-19T08:00:00+00:00")))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { snapping.drain() })

        assertEquals(listOf(LOCAL to 101L), store.mapped)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aLostCreateIsPostedWhenNoUnmappedEntryMatchesIt() = runTest {
        store.mapped += -5L to 101L
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual", maybeSent = true)
        server.enqueue(
            entries(
                listed(101, "Manual", start = "2026-10-19T08:00:00+00:00"),
                listed(102, "Other", start = "2026-10-19T08:00:00+00:00"),
                listed(103, "Manual", start = "2026-10-19T08:00:01+00:00"),
                listed(104, "Manual", start = "2026-10-19T08:00:00+00:00", tags = """["focus"]"""),
            ),
        )
        server.enqueue(entry(105, "Manual", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        assertEquals(listOf("GET", "POST"), List(2) { server.takeRequest().method })
        assertEquals(LOCAL to 105L, store.mapped.last())
    }

    /**
     * Tokkl stores a no-time create at midnight of its day in the account's timezone as the server
     * has it, which need not be the midnight the phone stored. The lookup finds the no-time entry
     * the lost post made by its flag and fields instead of posting it again.
     */
    @Test
    fun aNoTimeCreateWhoseAnswerIsLostIsFoundInsteadOfPostedAgain() = runTest {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        store.queue(
            ChangeKind.CREATE,
            LOCAL,
            MIDNIGHT,
            stop = MIDNIGHT.plusSeconds(5400),
            description = "Workshop",
            allDay = true,
            day = DAY,
        )
        server.enqueue(entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"))
        // The server reads the create, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(
            entries(
                listed(
                    100,
                    "Workshop",
                    start = "2026-10-18T22:30:00+00:00",
                    stop = "2026-10-19T00:00:00+00:00",
                    allDay = false,
                ),
                listed(
                    101,
                    "Workshop",
                    start = "2026-10-18T22:30:00+00:00",
                    stop = "2026-10-19T00:00:00+00:00",
                    allDay = true,
                ),
            ),
        )

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { noTimeSender().drain() })

        val requests = remainingRequests()
        assertEquals("one post, then the lookup", listOf("PUT", "POST", "GET"), requests.map { it.method })
        assertEquals("all_day", "true", requests[1].json()?.string("all_day"))
        assertEquals("2026-10-18T21:00:00Z", requests[2].url.queryParameter("start_date"))
        assertEquals("2026-10-18T23:00:00Z", requests[2].url.queryParameter("end_date"))
        assertEquals("the no-time entry, not the timed one", listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun aNoTimeCreateMatchesANoTimeEntryWithItsFieldsWithinTheLookupWindow() {
        val create = PendingChange(
            kind = ChangeKind.CREATE,
            entryId = LOCAL,
            workspaceId = 7,
            projectId = 3,
            taskId = null,
            description = "Workshop",
            start = MIDNIGHT,
            stop = MIDNIGHT.plusSeconds(
                5400,
            ),
            billable = true, tags = emptyList(), tagIds = emptyList(),
            allDay = true, day = DAY,
        )
        fun listedEntry(start: String, allDay: Boolean?, description: String = "Workshop") =
            ApiJson.decodeFromString(ServerTimeEntry.serializer(), listed(101, description, start, allDay = allDay))
        val starts = setOf(MIDNIGHT)

        assertTrue(create.couldHaveMade(listedEntry("2026-10-18T21:00:00+00:00", allDay = true), starts))
        assertTrue(create.couldHaveMade(listedEntry("2026-10-18T23:00:00+00:00", allDay = true), starts))
        assertFalse(
            "after the window",
            create.couldHaveMade(listedEntry("2026-10-18T23:00:01+00:00", allDay = true), starts),
        )
        assertFalse(
            "another description",
            create.couldHaveMade(listedEntry("2026-10-18T22:00:00+00:00", allDay = true, "Other"), starts),
        )
        assertFalse(
            "a timed entry at another start",
            create.couldHaveMade(listedEntry("2026-10-18T22:30:00+00:00", allDay = false), starts),
        )
        assertTrue(
            "sent while the flag was off, it went timed at its midnight",
            create.couldHaveMade(listedEntry("2026-10-18T22:00:00+00:00", allDay = null), starts),
        )
        assertFalse(
            "a timed create still needs its start",
            create.copy(
                allDay = false,
                day = null,
            ).couldHaveMade(listedEntry("2026-10-18T22:30:00+00:00", allDay = true), starts),
        )
    }

    @Test
    fun aStartWhoseAnswerWasLostIsLookedUpBeforeTheRunningEntry() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone", maybeSent = true)
        server.enqueue(entries(listed(101, "Phone", start = "2026-10-19T08:00:00+00:00", stop = null)))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })

        assertEquals("/api/v9/me/time_entries", server.takeRequest().url.encodedPath)
        assertEquals("no read of the running entry and no post", 1, server.requestCount)
        assertEquals(listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun aRefusedCreateIsNotMarkedAndGoesWithoutALookupNextTime() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(MockResponse.Builder().code(400).build())

        assertEquals(ChangeSender.Outcome.Empty(sent = 0), run { sender.drain() })
        assertFalse(store.changes.single().maybeSent)
        assertTrue(store.changes.single().held)
        store.changes.replaceAll { it.copy(held = false, refusal = null) }

        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })
        assertEquals(listOf("POST", "POST"), List(2) { server.takeRequest().method })
    }

    @Test
    fun aCreateAnswered5xxOrUnreadableStaysMarked() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(MockResponse.Builder().code(502).build())
        run { sender.drain() }
        assertTrue("a proxy's 502 may come after the server stored it", store.changes.single().maybeSent)

        store.changes.replaceAll { it.copy(maybeSent = false) }
        server.enqueue(MockResponse.Builder().code(201).body("not json").build())
        val outcome = run { sender.drain() }
        assertTrue(
            outcome.toString(),
            outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.InvalidResponse,
        )
        assertTrue("the server answered 2xx, so it stored the entry", store.changes.single().maybeSent)
    }

    @Test
    fun onlyRefusalsAndRequestsThatNeverLeftThePhoneLeaveNothingOnTheServer() {
        val nothing = listOf(
            ApiError.UnexpectedStatus(400),
            ApiError.UnexpectedStatus(404),
            ApiError.AuthFailed,
            ApiError.RateLimited(30),
            ApiError.Network(java.net.ConnectException("refused")),
            ApiError.Network(java.net.UnknownHostException("tokkl.example")),
            ApiError.Network(java.net.NoRouteToHostException("no route")),
            ApiError.HostNotAllowed("https://elsewhere.example"),
        )
        val maybe = listOf(
            ApiError.UnexpectedStatus(500),
            ApiError.UnexpectedStatus(504),
            ApiError.Network(java.net.SocketTimeoutException("timeout")),
            ApiError.Network(java.io.IOException("unexpected end of stream")),
            ApiError.InvalidResponse(IllegalArgumentException("not json")),
        )
        assertEquals(nothing.map { true }, nothing.map { it.leftNothingOnTheServer() })
        assertEquals(maybe.map { false }, maybe.map { it.leftNothingOnTheServer() })
    }

    @Test
    fun undoOfAnUnsentDeleteTakesItOutOfTheQueueWithNoRequest() = runTest {
        store.queue(ChangeKind.UPDATE, 54, TAP, stop = TAP.plusSeconds(60), description = "Other")
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")

        assertEquals(UndoOutcome.Cancelled, run { undo.undoDelete(EntryId(55)) })

        assertEquals("no request", 0, server.requestCount)
        assertEquals("the other change stays queued", listOf(54L), store.changes.map { it.entryId })
        assertEquals(
            "the entry is back as the delete found it",
            listOf(55L to "Deleted"),
            store.putBack.map { it.id to it.description },
        )
    }

    @Test
    fun undoOfAnUnsentDeleteOfAnEntryMadeOnThePhoneLeavesItsCreateQueued() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")
        store.queue(ChangeKind.DELETE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")

        assertEquals(UndoOutcome.Cancelled, run { undo.undoDelete(EntryId(LOCAL)) })

        assertEquals(0, server.requestCount)
        assertEquals(listOf(ChangeKind.CREATE), store.changes.map { it.kind })
        assertEquals(listOf(LOCAL), store.putBack.map { it.id })
    }

    @Test
    fun undoOfASentDeleteRestoresTheEntryThroughTokklsRestoreEndpoint() = runTest {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(ChangeSender.Outcome.Empty(sent = 1), run { sender.drain() })
        server.enqueue(entry(55, "Deleted", stop = "2026-10-19T08:01:30+00:00"))

        assertEquals(UndoOutcome.Restored, run { undo.undoDelete(EntryId(55)) })

        val (delete, restore) = List(2) { server.takeRequest() }
        assertEquals("DELETE /api/v9/workspaces/7/time_entries/55", "${delete.method} ${delete.url.encodedPath}")
        assertEquals("POST /api/app/time_entries/55/restore", "${restore.method} ${restore.url.encodedPath}")
        assertEquals(
            "Basic ${java.util.Base64.getEncoder().encodeToString("tok-123:api_token".toByteArray())}",
            restore.headers["Authorization"],
        )
        val (id, answer) = store.restores.single()
        assertEquals(55L, id)
        assertEquals("the cache gets the entry as the server restored it", "Deleted", answer?.description)
    }

    @Test
    fun undoOfASentDeleteOfAnEntryMadeOnThePhoneRestoresItUnderTheServerId() = runTest {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")
        store.queue(ChangeKind.DELETE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")
        server.enqueue(entry(101, "Made offline", stop = "2026-10-19T08:01:00+00:00"))
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), run { sender.drain() })
        server.enqueue(entry(101, "Made offline", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(UndoOutcome.Restored, run { undo.undoDelete(EntryId(LOCAL)) })

        val restore = remainingRequests().last()
        assertEquals("POST /api/app/time_entries/101/restore", "${restore.method} ${restore.url.encodedPath}")
    }

    @Test
    fun undoOfADeleteWhoseAnswerWasLostAsksTheServerAndTakesItOutOfTheQueue() = runTest {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        // The server reads the delete, then the connection drops before the answer, on the first
        // try and on the drain's one more.
        repeat(2) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }
        val outcome = run { sender.drain() }
        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.Network)
        assertTrue("the server may have deleted it", store.changes.single().maybeSent)
        server.enqueue(entry(55, "Deleted", stop = "2026-10-19T08:01:30+00:00"))

        assertEquals(UndoOutcome.Restored, run { undo.undoDelete(EntryId(55)) })

        val restore = remainingRequests().last()
        assertEquals("POST /api/app/time_entries/55/restore", "${restore.method} ${restore.url.encodedPath}")
        assertTrue("the delete never goes out again", store.changes.isEmpty())
        assertTrue("nothing put back without the server", store.putBack.isEmpty())
    }

    @Test
    fun aDeleteThatFailedWithoutReachingTheServerStaysUnmarkedSoUndoNeedsNoRequest() = runTest {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        server.enqueue(MockResponse.Builder().code(400).build())
        assertEquals(ChangeSender.Outcome.Empty(sent = 0), run { sender.drain() })
        assertFalse("a 4xx deleted nothing", store.changes.single().maybeSent)

        assertEquals(UndoOutcome.Cancelled, run { undo.undoDelete(EntryId(55)) })

        assertEquals("only the refused delete", 1, server.requestCount)
    }

    @Test
    fun aDeleteThatUndoTookBackBeforeItsMarkNeverGoesOut() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        store.queue(ChangeKind.UPDATE, 54, TAP, stop = TAP.plusSeconds(60), description = "Other")
        // Undo runs after the sender read the delete and before it marked it.
        store.afterFirst = {
            runBlocking {
                assertEquals(
                    UndoOutcome.Cancelled,
                    undo.undoDelete(EntryId(it.entryId)),
                )
            }
        }
        server.enqueue(entry(54, "Other", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), runBlocking { sender.drain() })

        assertEquals(
            listOf("PUT /api/v9/workspaces/7/time_entries/54"),
            remainingRequests().map { "${it.method} ${it.url.encodedPath}" },
        )
    }

    @Test
    fun undoOfASentDeleteNeedsTheServerOfflineAndSignedOut() = runTest {
        // The delete went out in an earlier drain, so the queue holds nothing. The first request
        // opens a new connection, which OkHttp does not try again after it fails.
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(429).build())

        assertEquals("no network", UndoOutcome.NeedsServer, run { undo.undoDelete(EntryId(55)) })
        assertEquals(
            "a proxy whose server is down",
            UndoOutcome.NeedsServer,
            run { undo.undoDelete(EntryId(55)) },
        )
        assertEquals("too many requests", UndoOutcome.NeedsServer, run { undo.undoDelete(EntryId(55)) })
        signedIn = false
        assertEquals("signed out", UndoOutcome.NeedsServer, run { undo.undoDelete(EntryId(55)) })

        assertTrue(store.restores.isEmpty())
    }

    @Test
    fun aRestoreTheServerRefusesFailsWithItsStatus() = runTest {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        server.enqueue(MockResponse.Builder().code(200).build())
        run { sender.drain() }
        server.enqueue(
            MockResponse.Builder().code(
                404,
            ).addHeader("Content-Type", "application/json").body("\"Time entry not found\"").build(),
        )
        server.enqueue(MockResponse.Builder().code(403).build())

        assertEquals(UndoOutcome.Failed(404), run { undo.undoDelete(EntryId(55)) })
        assertEquals(UndoOutcome.Failed(403), run { undo.undoDelete(EntryId(55)) })
        assertTrue(store.restores.isEmpty())
    }

    @Test
    fun aRestoreAnsweredWithAnUnreadableBodyStillCountsAsRestored() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("[]").build(),
        )

        assertEquals(UndoOutcome.Restored, run { undo.undoDelete(EntryId(55)) })

        assertEquals(listOf(55L to null), store.restores)
    }

    @Test
    fun aRestoreRefreshesTheAccountOnceAndACancelledDeleteDoesNot() = runTest {
        val refreshes = java.util.concurrent.atomic.AtomicInteger()
        val requests = AccountRefreshRequests()
        val counting = object : CacheRefresh {
            override fun refresh(): Job = Job()

            override fun refreshAfterChange(): Job = Job().also { refreshes.incrementAndGet() }
        }
        val forwarding = CoroutineScope(Dispatchers.Unconfined).launch { requests.forward(lazy { counting }) }
        val refreshing = DeleteUndoer(
            store,
            entries = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            io = Dispatchers.IO,
            refreshes = requests,
            scope = scope,
        )
        store.queue(ChangeKind.DELETE, 54, TAP, stop = TAP.plusSeconds(60), description = "Unsent")
        assertEquals(UndoOutcome.Cancelled, run { refreshing.undoDelete(EntryId(54)) })
        assertEquals(0, refreshes.get())

        server.enqueue(entry(55, "Deleted", stop = "2026-10-19T08:01:30+00:00"))
        assertEquals(UndoOutcome.Restored, run { refreshing.undoDelete(EntryId(55)) })
        assertEquals(1, refreshes.get())
        forwarding.cancel()
    }

    @Test
    fun undoOfAnEntryMadeOnThePhoneThatNeitherQueueNorServerHasFails() = runTest {
        assertEquals(UndoOutcome.Failed(status = null), run { undo.undoDelete(EntryId(LOCAL)) })
        assertEquals(0, server.requestCount)
    }

    /**
     * Leaves a connection in OkHttp's pool that the server has closed, with no `Connection: close`,
     * as Tokkl does to a connection idle for 5 seconds. OkHttp checks a pooled connection only once
     * it has been idle for 10 seconds, so the next request goes out on this one.
     */
    private fun poolAConnectionTheServerCloses() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("{}")
                .onResponseEnd(SocketEffect.CloseSocket())
                .build(),
        )
        val read = runBlocking {
            testApi.client(
                url,
                ApiCredentials.ApiToken("tok-123"),
            ).get(url.api("me"), JsonObject.serializer())
        }
        assertTrue(read.toString(), read is ApiResult.Success)
        assertEquals("GET", server.takeRequest().method)
    }

    private fun entry(id: Long, description: String, stop: String?) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(
            """{"id":$id,"workspace_id":7,"project_id":3,"description":"$description",""" +
                """"start":"2026-10-19T08:00:00+00:00","stop":${stop?.let { "\"$it\"" } ?: "null"},""" +
                """"billable":true,"tags":[],"tag_ids":[]}""",
        )
        .build()

    private fun entries(vararg json: String) = MockResponse.Builder().code(
        200,
    ).addHeader("Content-Type", "application/json").body(json.joinToString(",", "[", "]")).build()

    /** One entry in a GET /me/time_entries answer, as Tokkl lists it, in workspace 7. */
    private fun listed(
        id: Long,
        description: String,
        start: String,
        stop: String? = "2026-10-19T08:30:00+00:00",
        project: Long = 3,
        tags: String = "[]",
        allDay: Boolean? = null,
    ) = """{"id":$id,"workspace_id":7,"project_id":$project,"task_id":null,"description":"$description",""" +
        """"start":"$start","stop":${stop?.let { "\"$it\"" } ?: "null"},"billable":false,"tags":$tags,"tag_ids":[]""" +
        (allDay?.let { ",\"all_day\":$it" } ?: "") + "}"

    /** A sender for a server that stores no-time entries, as `Capabilities.allDay` says. */
    private fun noTimeSender() = changeSender(
        store,
        client = { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
        scope = scope,
        io = Dispatchers.IO,
        clock = clock,
        allDay = true,
    )

    private fun nothingRuns() = MockResponse.Builder().code(
        200,
    ).addHeader("Content-Type", "application/json").body("null").build()

    /** The answer to GET /me/time_entries/current while the entry [id], started at [start], runs. */
    private fun runs(id: Long, start: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(
            """{"id":$id,"workspace_id":7,"project_id":null,"description":"Laptop","start":"$start","stop":null,""" +
                """"billable":false,"tags":[],"tag_ids":[]}""",
        )
        .build()

    private fun RecordedRequest.json(): JsonObject? =
        body?.utf8()?.takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    /** Every request the server has received and the test has not taken yet. */
    private fun remainingRequests(): List<RecordedRequest> =
        generateSequence { server.takeRequest(100, TimeUnit.MILLISECONDS) }.toList()

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    /** The queue in memory, with the id mapping [RoomChangeStore] does. */
    private class InMemoryChangeStore : ChangeStore {
        val changes = mutableListOf<PendingChange>()
        val mapped = mutableListOf<Pair<Long, Long>>()
        val answers = mutableListOf<CachedTimeEntry>()
        val later = mutableListOf<CachedTimeEntry>()
        val putBack = mutableListOf<CachedTimeEntry>()
        val restores = mutableListOf<Pair<Long, CachedTimeEntry?>>()
        var onFirstSent: (() -> Unit)? = null

        /** Runs once, after the next read of the oldest change and outside the store's lock. */
        var afterFirst: ((PendingChange) -> Unit)? = null
        private var sequence = 0L

        fun queue(
            kind: ChangeKind,
            entryId: Long,
            start: Instant,
            stop: Instant?,
            description: String,
            tags: List<String> = emptyList(),
            maybeSent: Boolean = false,
            existingIds: List<Long> = emptyList(),
            capturedStop: Instant? = null,
            shortStop: Boolean = false,
            allDay: Boolean = false,
            day: LocalDate? = null,
        ) {
            changes += PendingChange(
                sequence = ++sequence,
                kind = kind,
                entryId = entryId,
                workspaceId = 7,
                projectId = 3,
                taskId = null,
                description = description,
                start = start,
                stop = stop,
                billable = true,
                tags = tags,
                tagIds = emptyList(),
                maybeSent = maybeSent,
                existingIds = existingIds,
                capturedStop = capturedStop,
                shortStop = shortStop,
                allDay = allDay,
                day = day,
            )
        }

        override fun first(): PendingChange? {
            val change = synchronized(this) {
                val heldEntries = changes.filter { it.held }.map { it.entryId }.toSet()
                changes.firstOrNull { it.entryId !in heldEntries }
            }
            if (change != null) afterFirst?.also { afterFirst = null }?.invoke(change)
            return change
        }

        @Synchronized
        override fun sent(change: PendingChange, serverId: Long, answer: CachedTimeEntry?) {
            changes.removeAll { it.sequence == change.sequence }
            if (change.kind == ChangeKind.CREATE) {
                mapped += change.entryId to serverId
                changes.replaceAll { if (it.entryId == change.entryId) it.copy(entryId = serverId) else it }
            }
            answer?.let(answers::add)
            onFirstSent?.also { onFirstSent = null }?.invoke()
        }

        /** Rewrites the queue as [RoomChangeStore] does, and keeps [later] for the test. */
        @Synchronized
        override fun endBefore(
            start: PendingChange,
            later: CachedTimeEntry,
            onGrid: (start: Instant, stop: Instant) -> SnappedSpan,
        ): PendingChange {
            val forEntry = changes.filter { it.entryId == start.entryId }
            val ended = start.endedAt(later.start, forEntry).placed(onGrid)
            changes.replaceAll { change ->
                when {
                    change.sequence == start.sequence -> ended

                    change.entryId == start.entryId && change.sequence > start.sequence -> change.stoppedBy(
                        later.start,
                    )?.placed(onGrid) ?: change

                    else -> change
                }
            }
            this.later += later
            return ended
        }

        @Synchronized
        override fun maybeSent(change: PendingChange, maybeSent: Boolean): Boolean {
            val queued = changes.any { it.sequence == change.sequence }
            changes.replaceAll { if (it.sequence == change.sequence) it.copy(maybeSent = maybeSent) else it }
            return queued
        }

        @Synchronized
        override fun isMapped(serverId: Long): Boolean = mapped.any { it.second == serverId }

        @Synchronized
        override fun hold(change: PendingChange, refusal: String?) {
            changes.replaceAll { if (it.sequence == change.sequence) it.copy(held = true, refusal = refusal) else it }
        }

        @Synchronized
        override fun discardEntry(change: PendingChange): Int {
            val count = changes.count { it.entryId == change.entryId }
            changes.removeAll { it.entryId == change.entryId }
            return count
        }

        /** Takes a delete back as [RoomChangeStore] does, and keeps the entry it puts back for the test. */
        @Synchronized
        override fun takeBackDelete(entryId: EntryId): TakenBack {
            val id = entryId.local?.let { local -> mapped.firstOrNull { it.first == local.value }?.second }
                ?: entryId.value
            val delete = changes.lastOrNull { it.entryId == id && it.kind == ChangeKind.DELETE }
            return when {
                delete != null && !delete.maybeSent -> {
                    changes.remove(delete)
                    putBack += delete.entry()
                    TakenBack.Unsent
                }

                id > 0 -> TakenBack.OnServer(id)

                else -> TakenBack.Unknown
            }
        }

        @Synchronized
        override fun restored(serverId: Long, answer: CachedTimeEntry?) {
            changes.removeAll { it.entryId == serverId && it.kind == ChangeKind.DELETE }
            restores += serverId to answer
        }
    }

    private companion object {
        val TAP: Instant = Instant.parse("2026-10-19T08:00:00Z")
        const val LOCAL = -1L

        /** A no-time entry's day, and its midnight in Europe/Madrid, as the phone stores them. */
        val DAY: LocalDate = LocalDate.parse("2026-10-19")
        val MIDNIGHT: Instant = Instant.parse("2026-10-18T22:00:00Z")
    }
}

/**
 * A [ChangeSender] with what a test leaves out: no-op events, the system clock, unsnapped spans
 * and no no-time entries.
 */
private fun changeSender(
    store: ChangeStore,
    client: ApiClientProvider,
    scope: CoroutineScope,
    io: CoroutineContext,
    onSent: () -> Unit = {},
    onEmptied: () -> Unit = {},
    onDiscarded: (Int) -> Unit = {},
    clock: Clock = Clock.systemUTC(),
    spans: ServerSpans = object : ServerSpans {},
    allDay: Boolean = false,
    undo: DeleteUndoer = DeleteUndoer(store, client.entries(), io, refreshes = AccountRefreshRequests(), scope = scope),
) = ChangeSender(
    store,
    client.entries(),
    scope,
    io,
    object : ChangeSenderEvents {
        override fun sent() = onSent()

        override fun emptied() = onEmptied()

        override fun discarded(count: Int) = onDiscarded(count)
    },
    clock,
    spans,
    CapabilitiesSource(
        InMemoryValueStore(Capabilities.NONE.copy(allDay = allDay)),
        CoroutineScope(Dispatchers.Unconfined),
        Dispatchers.Unconfined,
    ),
    undo,
)

/** The entry calls over [this] client, as the app opens them. */
private fun ApiClientProvider.entries(): suspend () -> TokklApi? = { this() }
