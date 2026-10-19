package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
        // The start's read of the running entry is the drain's first request.
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

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
    fun a5xxStopsTheDrainAndTheNextDrainResumesInOrder() {
        store.queue(ChangeKind.CREATE, LOCAL, TAP, stop = null, description = "First")
        store.queue(ChangeKind.CREATE, -2, TAP.plusSeconds(10), stop = null, description = "Second")
        server.enqueue(nothingRuns())
        server.enqueue(MockResponse.Builder().code(503).build())

        val outcome = sender.drain()

        assertEquals(ChangeSender.Outcome.Stopped(sent = 0, ApiError.UnexpectedStatus(503)), outcome)
        assertEquals("nothing after the failed change was sent", 2, server.requestCount)
        assertEquals(listOf(LOCAL, -2L), store.changes.map { it.entryId })

        server.enqueue(nothingRuns())
        server.enqueue(entry(101, "First", stop = null))
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        assertEquals(ChangeSender.Outcome.Empty(sent = 2), sender.drain())
        List(2) { server.takeRequest() }
        assertEquals(
            listOf("First", "Second"),
            List(4) { server.takeRequest() }.filter { it.method == "POST" }.map { it.json()!!.string("description") },
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
        server.enqueue(nothingRuns())
        server.enqueue(entry(102, "Second", stop = null))
        var joined: Job? = null
        store.onFirstSent = { joined = withOnSent.send() }

        val job = withOnSent.send()
        runBlocking { job.join() }

        assertSame("the call during the drain joined it", job, joined)
        assertEquals(listOf("onSent after 6 requests, 0 queued"), synchronized(calls) { calls.toList() })
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
        var onFirstSent: (() -> Unit)? = null
        private var sequence = 0L

        fun queue(
            kind: ChangeKind,
            entryId: Long,
            start: Instant,
            stop: Instant?,
            description: String,
            tags: List<String> = emptyList(),
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
            )
        }

        @Synchronized
        override fun first(): PendingChange? = changes.firstOrNull()

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
    }

    private companion object {
        val TAP: Instant = Instant.parse("2026-10-19T08:00:00Z")
        const val LOCAL = -1L
    }
}
