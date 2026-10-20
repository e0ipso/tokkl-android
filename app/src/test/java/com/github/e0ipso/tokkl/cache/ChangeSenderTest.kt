package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChangeSenderTest {

    private val server = MockWebServer()
    private val store = InMemoryChangeStore()
    private val clock = MutableClock(TAP)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var url: ServerUrl
    private var signedIn = true
    private lateinit var sender: ChangeSender

    @Before
    fun setUp() {
        server.start()
        url = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        sender = ChangeSender(
            store,
            client = { if (signedIn) TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) else null },
            scope = scope,
            io = Dispatchers.IO,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun sendsCreateEditStopAndDeleteOfAnOfflineEntryInOrderUnderTheServerId() {
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

        assertEquals(ChangeSender.Outcome.Empty(sent = 5), sender.drain())

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
    fun aRunningCreateSendsTheTapTimeAsItsStartAndTagsByName() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP.plusMillis(750), stop = null, description = "Offline", tags = listOf("focus", "new tag"))
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "Offline", stop = null))

        sender.drain()

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
    fun aStopSendsEveryFieldWithTheTapTimeAndAMatchingDuration() {
        store.queue(ChangeKind.STOP, 55, TAP, stop = TAP.plusSeconds(90).plusMillis(400), description = "Running")
        server.enqueue(entry(55, "Running", stop = "2026-10-19T08:01:30+00:00"))

        sender.drain()

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
    fun aDeleteSendsNoBody() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(60), description = "Gone")
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun theServersAnswerReachesTheStore() {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        server.enqueue(entry(55, "Edited on the server", stop = "2026-10-19T08:05:00+00:00"))

        sender.drain()

        assertEquals("Edited on the server", store.answers.single().description)
        assertEquals(Instant.parse("2026-10-19T08:05:00Z"), store.answers.single().stop)
    }

    @Test
    fun aNetworkFailureStopsTheDrainAndKeepsTheChangeForTheNextDrain() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        // The start's read of the running entry is the drain's first request. A dropped connection
        // gets one more try at once, so it drops twice.
        repeat(2) { server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }

        val outcome = sender.drain()

        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.Network)
        assertEquals(listOf(ChangeKind.CREATE, ChangeKind.STOP), store.changes.map { it.kind })

        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())
        val sent = remainingRequests().takeLast(2)
        assertEquals(
            listOf("POST /api/v9/workspaces/7/time_entries", "PUT /api/v9/workspaces/7/time_entries/101"),
            sent.map { "${it.method} ${it.url.encodedPath}" },
        )
    }

    @Test
    fun aChangeWhosePooledConnectionTheServerClosedGoesOutOnANewOneInTheSameDrain() {
        for ((kind, method) in listOf(ChangeKind.UPDATE to "PUT", ChangeKind.DELETE to "DELETE", ChangeKind.CREATE to "POST")) {
            poolAConnectionTheServerCloses()
            store.queue(kind, if (kind == ChangeKind.CREATE) LOCAL else 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
            // A create that may have reached the server looks for the entry it made first.
            if (kind == ChangeKind.CREATE) server.enqueue(entries())
            server.enqueue(
                if (kind == ChangeKind.DELETE) MockResponse.Builder().code(200).build() else entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"),
            )

            assertEquals(kind.name, ChangeSender.Outcome.Empty(sent = 1), sender.drain())

            assertEquals(kind.name, 1, remainingRequests().count { it.method == method })
            assertTrue(kind.name, store.changes.isEmpty())
        }
    }

    @Test
    fun aConnectionThatDropsAfterTheServerAnsweredLeadsToOneMoreTryAtMost() {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        // The server reads the update, then the connection drops before the answer, twice.
        repeat(2) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }

        val outcome = sender.drain()

        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.Network)
        assertEquals("the first try and one more, each once", listOf("PUT", "PUT"), remainingRequests().map { it.method })
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
    fun a5xxStopsTheDrainAndTheNextDrainResumesInOrder() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP.plusSeconds(10), stop = null, description = "Second")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())

        val outcome = sender.drain()

        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)), outcome)
        assertEquals("nothing after the failed change was sent", 2, server.requestCount)
        assertEquals(listOf(LOCAL, -2L), store.changes.map { it.entryId })

        // A 503 may come after the server stored the create, so the next drain looks for it first.
        server.enqueue(entries())
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())
        List(2) { server.takeRequest() }
        assertEquals(
            listOf("First", "Second"),
            List(5) { server.takeRequest() }.filter { it.method == "POST" }.map { it.json()!!.string("description") },
        )
    }

    @Test
    fun anyOtherFailureStopsTheDrainAndKeepsTheChange() {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        server.enqueue(MockResponse.Builder().code(400).build())

        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(400)), sender.drain())
        assertEquals(1, store.changes.size)
    }

    @Test
    fun a429WaitsForRetryAfterBeforeTheNextTry() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())

        assertEquals(ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plusSeconds(30)), sender.drain())

        clock.now = TAP.plusSeconds(29)
        assertEquals(ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plusSeconds(30)), sender.drain())
        assertEquals("no request before Retry-After has passed", 2, server.requestCount)
        assertEquals(2, store.changes.size)

        clock.now = TAP.plusSeconds(30)
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())
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
    fun a429WithoutRetryAfterWaitsTheDefault() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(429).build())

        assertEquals(
            ChangeSender.Outcome.Waiting(sent = 0, until = TAP.plus(ChangeSender.DEFAULT_RETRY_AFTER)),
            sender.drain(),
        )
    }

    @Test
    fun sendTriesAgainByItselfOnceRetryAfterHasPassed() {
        val realTime = ChangeSender(
            store,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
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
        waitUntil { store.changes.isEmpty() }
    }

    @Test
    fun signedOutSendsNothing() {
        signedIn = false
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")

        assertEquals(ChangeSender.Outcome.SignedOut(sent = 0), sender.drain())
        assertEquals(0, server.requestCount)
        assertEquals(1, store.changes.size)
    }

    @Test
    fun aChangeQueuedDuringADrainIsSentByThatDrain() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        store.onFirstSent = { store.queue(ChangeKind.CREATE, -2, TAP, stop = null, description = "Second") }

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())
        assertNull(store.first())
    }

    /**
     * A send() call during a drain joins it and makes it drain once more. Here the first pass sends
     * one change and stops at a 503, and the second pass sends the rest, so the job takes two
     * passes. onSent runs once for the job, after the second pass has sent its change.
     */
    @Test
    fun onSentRunsOncePerDrainJobAfterItsLastPass() {
        val calls = mutableListOf<String>()
        lateinit var withOnSent: ChangeSender
        withOnSent = ChangeSender(
            store,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onSent = { synchronized(calls) { calls += "onSent after ${server.requestCount} requests, ${store.changes.size} queued" } },
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
        var joined: Job? = null
        store.onFirstSent = { joined = withOnSent.send() }

        val job = withOnSent.send()
        runBlocking { job.join() }

        assertSame("the call during the drain joined it", job, joined)
        assertEquals(listOf("onSent after 7 requests, 0 queued"), synchronized(calls) { calls.toList() })
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun aDrainThatSendsNothingDoesNotRunOnSent() {
        var calls = 0
        val withOnSent = ChangeSender(
            store,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onSent = { calls++ },
            clock = clock,
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())

        runBlocking { withOnSent.send().join() }

        assertEquals(0, calls)
        assertEquals(1, store.changes.size)
    }

    /**
     * A drain that sends the queue and leaves it empty runs onEmptied once, before onSent, so the
     * app can cancel the background send's waiting retry. One that stops at a failure or sends
     * nothing does not.
     */
    @Test
    fun onEmptiedRunsOnceWhenADrainSendsTheQueueEmpty() {
        val calls = mutableListOf<String>()
        val withOnEmptied = ChangeSender(
            store,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
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

        runBlocking { withOnEmptied.send().join() }
        assertEquals("a drain that stops at a failure", listOf("onSent"), synchronized(calls) { calls.toList() })

        server.enqueue(entries())
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        runBlocking { withOnEmptied.send().join() }
        assertEquals(listOf("onSent", "onEmptied with 0 queued", "onSent"), synchronized(calls) { calls.toList() })

        runBlocking { withOnEmptied.send().join() }
        assertEquals("a drain of an empty queue sends nothing", 3, synchronized(calls) { calls.size })
    }

    /**
     * The background send learns how the drain ended. A 503 may come after the server stored the
     * create, so the next drain looks for it before posting it again.
     */
    @Test
    fun sendAndWaitReturnsHowTheDrainEnded() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(
            ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)),
            runBlocking { sender.sendAndWait() },
        )
        assertEquals(1, store.changes.size)

        server.enqueue(entries())
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), runBlocking { sender.sendAndWait() })
        assertTrue(store.changes.isEmpty())
    }

    /**
     * A sendAndWait call during a drain joins it and returns how its last pass ended, not the pass
     * it came in. Here the first pass stops at a 503 and the second sends the rest.
     */
    @Test
    fun sendAndWaitDuringADrainReturnsItsLastPass() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP, stop = TAP.plusSeconds(60), description = "Second")
        server.enqueue(entry(101, "First", stop = "2026-10-19T08:01:00+00:00"))
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(entries())
        server.enqueue(entry(102, "Second", stop = "2026-10-19T08:01:00+00:00"))
        var joined: Deferred<ChangeSender.Outcome?>? = null
        store.onFirstSent = { joined = scope.async(start = CoroutineStart.UNDISPATCHED) { sender.sendAndWait() } }

        runBlocking { sender.send().join() }

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), runBlocking { checkNotNull(joined).await() })
        assertEquals(4, server.requestCount)
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun sendAndWaitWhenSignedOutSendsNothing() {
        signedIn = false
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")

        assertEquals(ChangeSender.Outcome.SignedOut(sent = 0), runBlocking { sender.sendAndWait() })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aQueuedStartGoesStoppedWhereALaterRunningTimerStartsAndThatTimerStaysInTheCache() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP.plusMillis(400), stop = null, description = "Phone")
        server.enqueue(runs(900, start = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:30:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

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
    fun aQueuedStartGoesAsQueuedWhenNothingRuns() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "Phone", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals("GET /api/v9/me/time_entries/current", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        val body = server.takeRequest().json()!!
        assertTrue(body["stop"] is JsonNull)
        assertEquals("-1", body.string("duration"))
        assertEquals(2, server.requestCount)
        assertTrue(store.later.isEmpty())
    }

    @Test
    fun aQueuedStartGoesAsQueuedWhenTheRunningTimerStartedEarlierOrAtTheSameSecond() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "After an earlier timer")
        store.queue(ChangeKind.CREATE, -2, TAP.plusSeconds(60).plusMillis(900), stop = null, description = "At the same second")
        server.enqueue(runs(900, start = "2026-10-19T07:00:00+00:00"))
        server.enqueue(entry(101, "After an earlier timer", stop = null))
        server.enqueue(runs(901, start = "2026-10-19T08:01:00+00:00"))
        server.enqueue(entry(102, "At the same second", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())

        val posts = List(4) { server.takeRequest() }.filter { it.method == "POST" }
        assertTrue(posts.all { it.json()!!["stop"] is JsonNull })
        assertEquals(4, server.requestCount)
        assertTrue(store.later.isEmpty())
    }

    @Test
    fun aQueuedStopBeforeTheLaterTimerEndsTheStartInstead() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(600), description = "Phone")
        server.enqueue(runs(900, start = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:10:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:10:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())

        val requests = List(3) { server.takeRequest() }
        assertEquals(listOf("2026-10-19T08:10:00Z", "2026-10-19T08:10:00Z"), requests.drop(1).map { it.json()!!.string("stop") })
    }

    @Test
    fun laterChangesThatWouldRunOrStopTheEntryAfterTheLaterTimerStopItWhereThatTimerStarts() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        store.queue(ChangeKind.UPDATE, LOCAL, TAP, stop = null, description = "Edited while it ran")
        store.queue(ChangeKind.STOP, LOCAL, TAP, stop = TAP.plusSeconds(2700), description = "Edited while it ran")
        store.queue(ChangeKind.UPDATE, LOCAL, TAP.plusSeconds(3600), stop = null, description = "Moved after the later timer")
        server.enqueue(runs(900, start = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Phone", stop = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Edited while it ran", stop = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Edited while it ran", stop = "2026-10-19T08:30:00+00:00"))
        server.enqueue(entry(101, "Moved after the later timer", stop = null))

        assertEquals(ChangeSender.Outcome.Empty(sent = 4), sender.drain())

        val bodies = List(5) { server.takeRequest() }.drop(1).map { it.json()!! }
        assertEquals(
            listOf("2026-10-19T08:30:00Z", "2026-10-19T08:30:00Z", "2026-10-19T08:30:00Z", null),
            bodies.map { (it["stop"] as? JsonPrimitive)?.takeIf { stop -> stop.isString }?.content },
        )
    }

    @Test
    fun aFailedReadOfTheRunningEntryKeepsTheStartQueuedAndSendsNothing() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone")
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)), sender.drain())

        assertEquals(1, server.requestCount)
        assertEquals(listOf(ChangeKind.CREATE), store.changes.map { it.kind })
        assertNull(store.changes.single().stop)
    }

    @Test
    fun aStoppedCreateReadsNoRunningEntry() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aCreateWhoseAnswerIsLostStaysMarkedAndOkHttpDoesNotPostItAgain() {
        // The update leaves a connection in OkHttp's pool, which the create then reuses.
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"))
        // The server reads the create, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        // The drain's second try looks for the entry the post made, and that read fails.
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:01:00+00:00"))

        val outcome = sender.drain()

        assertEquals(ChangeSender.Outcome.Stopped(sent = 1, ApiError.UnexpectedStatus(503)), outcome)
        assertEquals("the create went out once", listOf("PUT", "POST", "GET"), remainingRequests().map { it.method })
        assertTrue("the server may hold it", store.changes.single().maybeSent)
    }

    @Test
    fun aCreateWhoseAnswerIsLostIsLookedUpInTheSameDrainInsteadOfPostedAgain() {
        store.queue(ChangeKind.UPDATE, 55, TAP, stop = TAP.plusSeconds(60), description = "Edited")
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(entry(55, "Edited", stop = "2026-10-19T08:01:00+00:00"))
        // The server reads the create, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(entries(listed(101, "Manual", start = "2026-10-19T08:00:00+00:00", stop = "2026-10-19T08:01:00+00:00")))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())

        assertEquals("one post, then the lookup", listOf("PUT", "POST", "GET"), remainingRequests().map { it.method })
        assertEquals(listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun theNextDrainTakesTheEntryALostCreateMadeAndSendsLaterChangesToIt() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual", maybeSent = true)
        store.queue(ChangeKind.UPDATE, LOCAL, TAP, stop = TAP.plusSeconds(120), description = "Edited")
        server.enqueue(entries(listed(100, "Manual", start = "2026-10-19T08:00:00+00:00", project = 4), listed(101, "Manual", start = "2026-10-19T08:00:00+00:00")))
        server.enqueue(entry(101, "Edited", stop = "2026-10-19T08:02:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())

        val (lookup, edit) = List(2) { server.takeRequest() }
        assertEquals("GET /api/v9/me/time_entries", "${lookup.method} ${lookup.url.encodedPath}")
        assertEquals("2026-10-19T07:00:00Z", lookup.url.queryParameter("start_date"))
        assertEquals("2026-10-19T09:00:00Z", lookup.url.queryParameter("end_date"))
        assertEquals("PUT /api/v9/workspaces/7/time_entries/101", "${edit.method} ${edit.url.encodedPath}")
        assertEquals("the matching entry, not the one in another project", listOf(LOCAL to 101L), store.mapped)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aLostDuplicateIsPostedInsteadOfTakingItsSource() {
        // A duplicate has every field and time of its source, entry 100, which the server held
        // when the phone queued the copy, so the lookup for a lost copy must leave it out.
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(1800), description = "Manual", maybeSent = true, existingIds = listOf(100))
        server.enqueue(entries(listed(100, "Manual", start = "2026-10-19T08:00:00+00:00")))
        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:30:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals("a lookup and a post", 2, server.requestCount)
        val (lookup, post) = List(2) { server.takeRequest() }
        assertEquals("GET /api/v9/me/time_entries", "${lookup.method} ${lookup.url.encodedPath}")
        assertEquals("POST /api/v9/workspaces/7/time_entries", "${post.method} ${post.url.encodedPath}")
        assertEquals("the copy, not its source", listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun aLostDuplicateTakesTheCopyItsPostMadeBesideItsSource() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(1800), description = "Manual", maybeSent = true, existingIds = listOf(100))
        server.enqueue(
            entries(listed(100, "Manual", start = "2026-10-19T08:00:00+00:00"), listed(101, "Manual", start = "2026-10-19T08:00:00+00:00")),
        )

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals(listOf(LOCAL to 101L), store.mapped)
        assertEquals("no second post", 1, server.requestCount)
    }

    @Test
    fun aLostCreateMatchesTheStartWhereTheServerSnappedIt() {
        val snapping = ChangeSender(
            store,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            clock = clock,
            serverStart = { it.truncatedTo(java.time.temporal.ChronoUnit.HOURS) },
        )
        store.queue(ChangeKind.CREATE, LOCAL, TAP.plusSeconds(120).plusMillis(300), stop = TAP.plusSeconds(600), description = "Manual", maybeSent = true)
        server.enqueue(entries(listed(101, "Manual", start = "2026-10-19T08:00:00+00:00")))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), snapping.drain())

        assertEquals(listOf(LOCAL to 101L), store.mapped)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aLostCreateIsPostedWhenNoUnmappedEntryMatchesIt() {
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

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals(listOf("GET", "POST"), List(2) { server.takeRequest().method })
        assertEquals(LOCAL to 105L, store.mapped.last())
    }

    @Test
    fun aStartWhoseAnswerWasLostIsLookedUpBeforeTheRunningEntry() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "Phone", maybeSent = true)
        server.enqueue(entries(listed(101, "Phone", start = "2026-10-19T08:00:00+00:00", stop = null)))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals("/api/v9/me/time_entries", server.takeRequest().url.encodedPath)
        assertEquals("no read of the running entry and no post", 1, server.requestCount)
        assertEquals(listOf(LOCAL to 101L), store.mapped)
    }

    @Test
    fun aRefusedCreateIsNotMarkedAndGoesWithoutALookupNextTime() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(MockResponse.Builder().code(400).build())

        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(400)), sender.drain())
        assertFalse(store.changes.single().maybeSent)

        server.enqueue(entry(101, "Manual", stop = "2026-10-19T08:01:00+00:00"))
        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())
        assertEquals(listOf("POST", "POST"), List(2) { server.takeRequest().method })
    }

    @Test
    fun aCreateAnswered5xxOrUnreadableStaysMarked() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Manual")
        server.enqueue(MockResponse.Builder().code(502).build())
        sender.drain()
        assertTrue("a proxy's 502 may come after the server stored it", store.changes.single().maybeSent)

        store.changes.replaceAll { it.copy(maybeSent = false) }
        server.enqueue(MockResponse.Builder().code(201).body("not json").build())
        val outcome = sender.drain()
        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.InvalidResponse)
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
    fun undoOfAnUnsentDeleteTakesItOutOfTheQueueWithNoRequest() {
        store.queue(ChangeKind.UPDATE, 54, TAP, stop = TAP.plusSeconds(60), description = "Other")
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")

        assertEquals(ChangeSender.UndoOutcome.Cancelled, runBlocking { sender.undoDelete(55) })

        assertEquals("no request", 0, server.requestCount)
        assertEquals("the other change stays queued", listOf(54L), store.changes.map { it.entryId })
        assertEquals("the entry is back as the delete found it", listOf(55L to "Deleted"), store.putBack.map { it.id to it.description })
    }

    @Test
    fun undoOfAnUnsentDeleteOfAnEntryMadeOnThePhoneLeavesItsCreateQueued() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")
        store.queue(ChangeKind.DELETE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")

        assertEquals(ChangeSender.UndoOutcome.Cancelled, runBlocking { sender.undoDelete(LOCAL) })

        assertEquals(0, server.requestCount)
        assertEquals(listOf(ChangeKind.CREATE), store.changes.map { it.kind })
        assertEquals(listOf(LOCAL), store.putBack.map { it.id })
    }

    @Test
    fun undoOfASentDeleteRestoresTheEntryThroughTokklsRestoreEndpoint() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())
        server.enqueue(entry(55, "Deleted", stop = "2026-10-19T08:01:30+00:00"))

        assertEquals(ChangeSender.UndoOutcome.Restored, runBlocking { sender.undoDelete(55) })

        val (delete, restore) = List(2) { server.takeRequest() }
        assertEquals("DELETE /api/v9/workspaces/7/time_entries/55", "${delete.method} ${delete.url.encodedPath}")
        assertEquals("POST /api/app/time_entries/55/restore", "${restore.method} ${restore.url.encodedPath}")
        assertEquals("Basic ${java.util.Base64.getEncoder().encodeToString("tok-123:api_token".toByteArray())}", restore.headers["Authorization"])
        val (id, answer) = store.restores.single()
        assertEquals(55L, id)
        assertEquals("the cache gets the entry as the server restored it", "Deleted", answer?.description)
    }

    @Test
    fun undoOfASentDeleteOfAnEntryMadeOnThePhoneRestoresItUnderTheServerId() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")
        store.queue(ChangeKind.DELETE, LOCAL, TAP, stop = TAP.plusSeconds(60), description = "Made offline")
        server.enqueue(entry(101, "Made offline", stop = "2026-10-19T08:01:00+00:00"))
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())
        server.enqueue(entry(101, "Made offline", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.UndoOutcome.Restored, runBlocking { sender.undoDelete(LOCAL) })

        val restore = remainingRequests().last()
        assertEquals("POST /api/app/time_entries/101/restore", "${restore.method} ${restore.url.encodedPath}")
    }

    @Test
    fun undoOfADeleteWhoseAnswerWasLostAsksTheServerAndTakesItOutOfTheQueue() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        // The server reads the delete, then the connection drops before the answer, on the first
        // try and on the drain's one more.
        repeat(2) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }
        val outcome = sender.drain()
        assertTrue(outcome.toString(), outcome is ChangeSender.Outcome.Stopped && outcome.error is ApiError.Network)
        assertTrue("the server may have deleted it", store.changes.single().maybeSent)
        server.enqueue(entry(55, "Deleted", stop = "2026-10-19T08:01:30+00:00"))

        assertEquals(ChangeSender.UndoOutcome.Restored, runBlocking { sender.undoDelete(55) })

        val restore = remainingRequests().last()
        assertEquals("POST /api/app/time_entries/55/restore", "${restore.method} ${restore.url.encodedPath}")
        assertTrue("the delete never goes out again", store.changes.isEmpty())
        assertTrue("nothing put back without the server", store.putBack.isEmpty())
    }

    @Test
    fun aDeleteThatFailedWithoutReachingTheServerStaysUnmarkedSoUndoNeedsNoRequest() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(404)), sender.drain())
        assertFalse("a 4xx deleted nothing", store.changes.single().maybeSent)

        assertEquals(ChangeSender.UndoOutcome.Cancelled, runBlocking { sender.undoDelete(55) })

        assertEquals("only the refused delete", 1, server.requestCount)
    }

    @Test
    fun aDeleteThatUndoTookBackBeforeItsMarkNeverGoesOut() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        store.queue(ChangeKind.UPDATE, 54, TAP, stop = TAP.plusSeconds(60), description = "Other")
        // Undo runs after the sender read the delete and before it marked it.
        store.afterFirst = { runBlocking { assertEquals(ChangeSender.UndoOutcome.Cancelled, sender.undoDelete(it.entryId)) } }
        server.enqueue(entry(54, "Other", stop = "2026-10-19T08:01:00+00:00"))

        assertEquals(ChangeSender.Outcome.Empty(sent = 1), sender.drain())

        assertEquals(listOf("PUT /api/v9/workspaces/7/time_entries/54"), remainingRequests().map { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun undoOfASentDeleteNeedsTheServerOfflineAndSignedOut() {
        // The delete went out in an earlier drain, so the queue holds nothing. The first request
        // opens a new connection, which OkHttp does not try again after it fails.
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(429).build())

        assertEquals("no network", ChangeSender.UndoOutcome.NeedsServer, runBlocking { sender.undoDelete(55) })
        assertEquals("a proxy whose server is down", ChangeSender.UndoOutcome.NeedsServer, runBlocking { sender.undoDelete(55) })
        assertEquals("too many requests", ChangeSender.UndoOutcome.NeedsServer, runBlocking { sender.undoDelete(55) })
        signedIn = false
        assertEquals("signed out", ChangeSender.UndoOutcome.NeedsServer, runBlocking { sender.undoDelete(55) })

        assertTrue(store.restores.isEmpty())
    }

    @Test
    fun aRestoreTheServerRefusesFailsWithItsStatus() {
        store.queue(ChangeKind.DELETE, 55, TAP, stop = TAP.plusSeconds(90), description = "Deleted")
        server.enqueue(MockResponse.Builder().code(200).build())
        sender.drain()
        server.enqueue(MockResponse.Builder().code(404).addHeader("Content-Type", "application/json").body("\"Time entry not found\"").build())
        server.enqueue(MockResponse.Builder().code(403).build())

        assertEquals(ChangeSender.UndoOutcome.Failed(404), runBlocking { sender.undoDelete(55) })
        assertEquals(ChangeSender.UndoOutcome.Failed(403), runBlocking { sender.undoDelete(55) })
        assertTrue(store.restores.isEmpty())
    }

    @Test
    fun aRestoreAnsweredWithAnUnreadableBodyStillCountsAsRestored() {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("[]").build())

        assertEquals(ChangeSender.UndoOutcome.Restored, runBlocking { sender.undoDelete(55) })

        assertEquals(listOf(55L to null), store.restores)
    }

    @Test
    fun aRestoreRefreshesTheAccountOnceAndACancelledDeleteDoesNot() {
        val refreshes = java.util.concurrent.atomic.AtomicInteger()
        val refreshing = ChangeSender(
            store,
            client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
            scope = scope,
            io = Dispatchers.IO,
            onSent = { refreshes.incrementAndGet() },
            clock = clock,
        )
        store.queue(ChangeKind.DELETE, 54, TAP, stop = TAP.plusSeconds(60), description = "Unsent")
        assertEquals(ChangeSender.UndoOutcome.Cancelled, runBlocking { refreshing.undoDelete(54) })
        assertEquals(0, refreshes.get())

        server.enqueue(entry(55, "Deleted", stop = "2026-10-19T08:01:30+00:00"))
        assertEquals(ChangeSender.UndoOutcome.Restored, runBlocking { refreshing.undoDelete(55) })
        assertEquals(1, refreshes.get())
    }

    @Test
    fun undoOfAnEntryMadeOnThePhoneThatNeitherQueueNorServerHasFails() {
        assertEquals(ChangeSender.UndoOutcome.Failed(status = null), runBlocking { sender.undoDelete(LOCAL) })
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
        val read = TogglApiClient(url, ApiCredentials.ApiToken("tok-123")).get(url.api("me"), JsonObject.serializer())
        assertTrue(read.toString(), read is ApiResult.Success)
        assertEquals("GET", server.takeRequest().method)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out")
            Thread.sleep(20)
        }
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

    private fun entries(vararg json: String) =
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(json.joinToString(",", "[", "]")).build()

    /** One entry in a GET /me/time_entries answer, as Tokkl lists it, in workspace 7. */
    private fun listed(
        id: Long,
        description: String,
        start: String,
        stop: String? = "2026-10-19T08:30:00+00:00",
        project: Long = 3,
        tags: String = "[]",
    ) = """{"id":$id,"workspace_id":7,"project_id":$project,"task_id":null,"description":"$description",""" +
        """"start":"$start","stop":${stop?.let { "\"$it\"" } ?: "null"},"billable":false,"tags":$tags,"tag_ids":[]}"""

    private fun nothingRuns() = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("null").build()

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
            )
        }

        override fun first(): PendingChange? {
            val change = synchronized(this) { changes.firstOrNull() }
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
        override fun endBefore(start: PendingChange, later: CachedTimeEntry): PendingChange {
            val forEntry = changes.filter { it.entryId == start.entryId }
            val ended = start.endedAt(later.start, forEntry)
            changes.replaceAll { change ->
                when {
                    change.sequence == start.sequence -> ended
                    change.entryId == start.entryId && change.sequence > start.sequence -> change.stoppedBy(later.start) ?: change
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

        /** Takes a delete back as [RoomChangeStore] does, and keeps the entry it puts back for the test. */
        @Synchronized
        override fun takeBackDelete(entryId: Long): TakenBack {
            val id = if (entryId < 0) mapped.firstOrNull { it.first == entryId }?.second ?: entryId else entryId
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
    }
}
