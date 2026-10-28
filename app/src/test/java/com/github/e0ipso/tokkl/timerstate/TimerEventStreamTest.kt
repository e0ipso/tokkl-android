package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The timer event stream as Tokkl 1.10.0 sends it: the hand-written framing however reads cut it,
 * which events reach the account's timer state, and the request the shared client sends.
 */
class TimerEventStreamTest {

    private val server = MockWebServer()
    private val credentials = ApiCredentials.ApiToken("tok-123")
    private val cache = RecordingCache()
    private lateinit var url: ServerUrl
    private lateinit var holder: TimerStateSource
    private val link = RecordingLink()

    @Before
    fun setUp() {
        server.start()
        // The server sits under a path, as a Tokkl behind a reverse proxy can.
        url = ServerUrl.parse(server.url("/tokkl/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        holder = TimerStateSource(initial = null, save = {}, cache = cache)
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun readsCutAnywhereGiveTheSameEventsAsOneRead() {
        // The second state's description has two- and three-byte UTF-8 characters to cut inside.
        val transcript = timerEvent(state(1)) + KEEP_ALIVE + ": a proxy's comment\n\n" + timerEvent(state(2, running = entry(40, "Café ☕ notes")))
        val bytes = transcript.toByteArray(Charsets.UTF_8)
        val whole = EventStreamParser().feed(bytes, bytes.size)

        assertEquals(
            listOf(
                StreamItem.Event("timer", json(state(1))),
                StreamItem.Event("keep-alive", ""),
                StreamItem.Comment,
                StreamItem.Event("timer", json(state(2, running = entry(40, "Café ☕ notes")))),
            ),
            whole,
        )
        for (cut in 1 until bytes.size) {
            val parser = EventStreamParser()
            val items = parser.feed(bytes.copyOfRange(0, cut), cut) + parser.feed(bytes.copyOfRange(cut, bytes.size), bytes.size - cut)
            assertEquals("cut at byte $cut", whole, items)
        }
        val parser = EventStreamParser()
        assertEquals("one byte per read", whole, bytes.flatMap { parser.feed(byteArrayOf(it), 1) })
    }

    @Test
    fun crAndCrlfEndLinesAsLfDoesEvenWhenAReadEndsBetweenThem() {
        val lf = "event: timer\ndata: {\"a\":1}\n\n: comment\nevent: keep-alive\ndata:\n\n"
        val expected = EventStreamParser().feed(lf.toByteArray(), lf.length)

        for (lineEnd in listOf("\r\n", "\r")) {
            val bytes = lf.replace("\n", lineEnd).toByteArray()
            assertEquals(lineEnd, expected, EventStreamParser().feed(bytes, bytes.size))
            val parser = EventStreamParser()
            assertEquals("byte reads, $lineEnd", expected, bytes.flatMap { parser.feed(byteArrayOf(it), 1) })
        }
    }

    @Test
    fun anEventDispatchesOnlyAtTheBlankLineThatEndsItsBlock() {
        val parser = EventStreamParser()
        val open = "event: timer\ndata: {\"revision\":1}\n".toByteArray()

        assertEquals(emptyList<StreamItem>(), parser.feed(open, open.size))
        assertEquals(listOf(StreamItem.Event("timer", "{\"revision\":1}")), parser.feed("\n".toByteArray(), 1))
        // A block with no data line dispatches nothing, and the next block does not inherit its name.
        val noData = "event: timer\n\ndata: x\n\n".toByteArray()
        assertEquals(listOf(StreamItem.Event("message", "x")), parser.feed(noData, noData.size))
    }

    @Test
    fun timerEventsReachTheHolderAndEveryEventOrCommentCountsAsAlive() {
        val body = timerEvent(state(3)) + KEEP_ALIVE + ": keep-alive\n\n" + "event: something-new\ndata: {}\n\n" + timerEvent(state(4, running = entry(40, "Focus")))

        val end = readTimerEvents(chunks(body, 7), holder, link.link, holder.begin())

        assertEquals(StreamEnd.Ended, end)
        assertEquals(4L, holder.state.value?.state?.revision)
        assertEquals("the running entry reaches the cache", listOf(40L), cache.written.filterNotNull().map { it.id })
        assertEquals("two states, a keep-alive, a comment and an unknown event", 5, link.alive)
    }

    @Test
    fun aMalformedOrTruncatedStateNeverReplacesTheHeldOne() {
        holder.accept(holder.begin(), state(5))
        val held = holder.state.value

        val malformed = readTimerEvents(chunks("event: timer\ndata: {\"revision\":\"six\"}\n\n" + timerEvent(state(7)), 64), holder, link.link, holder.begin())
        assertTrue(malformed.toString(), malformed is StreamEnd.Failed && malformed.error is ApiError.InvalidResponse)
        assertEquals("the read stopped at the bad state", held, holder.state.value)

        // The connection ends in the middle of a state: the cut block is never dispatched.
        val cut = timerEvent(state(8)).dropLast(30)
        assertEquals(StreamEnd.Ended, readTimerEvents(chunks(cut, 16), holder, link.link, holder.begin()))
        assertEquals(held, holder.state.value)
    }

    @Test
    fun aDroppedConnectionKeepsTheLastStateForTheOfflinePhone() {
        val dropping = object : InputStream() {
            private val first = ByteArrayInputStream(timerEvent(state(9)).toByteArray())

            override fun read(): Int = throw UnsupportedOperationException()

            override fun read(b: ByteArray, off: Int, len: Int): Int = first.read(b, off, len).takeIf { it != -1 } ?: throw IOException("Connection reset")
        }

        try {
            readTimerEvents(dropping, holder, link.link, holder.begin())
            fail("the drop reaches the stream's owner")
        } catch (e: IOException) {
            assertEquals("Connection reset", e.message)
        }
        assertEquals(9L, holder.state.value?.state?.revision)
    }

    @Test
    fun aCancelledLinkTakesNoFurtherStateAndARetiredAccountTakesNone() {
        link.link.cancel()
        assertEquals(StreamEnd.Ended, readTimerEvents(chunks(timerEvent(state(1)), 8), holder, link.link, holder.begin()))
        assertNull("a cancelled stream publishes nothing", holder.state.value)

        // Sign-out or a new sign-in retired this account while its stream was still reading.
        val read = holder.begin()
        holder.retire()
        val live = RecordingLink()
        assertEquals(StreamEnd.Ended, readTimerEvents(chunks(timerEvent(state(2, running = entry(40, "Other account"))), 8), holder, live.link, read))
        assertNull(holder.state.value)
        assertEquals("the cache keeps nothing of it", emptyList<CachedTimeEntry?>(), cache.written)
    }

    @Test
    fun theStreamUsesTheAccountsServerPathAndCredentialWithNoReadTimeout() {
        // Same settings as the shared client, with a read timeout far shorter than the body's delay.
        val impatient = SharedHttpClient.instance.newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val api = TogglApiClient(url, credentials, impatient)
        server.enqueue(eventStream(timerEvent(state(12))).bodyDelay(600, TimeUnit.MILLISECONDS).build())
        // The same delay times out a plain request on that client.
        server.enqueue(eventStream("{}").bodyDelay(600, TimeUnit.MILLISECONDS).build())

        val end = holdTimerEvents(api, holder, link.link)

        assertEquals(StreamEnd.Ended, end)
        assertEquals(12L, holder.state.value?.state?.revision)
        assertEquals(1, link.connected)
        val request = server.takeRequest()
        assertEquals("GET" to "/tokkl/api/app/timer/events", request.method to request.url.encodedPath)
        assertEquals(credentials.authorizationHeader, request.headers["Authorization"])
        val plain = api.get(url.app("timer"), JsonObject.serializer())
        assertTrue(plain.toString(), plain is ApiResult.Failure && (plain.error as? ApiError.Network)?.cause is SocketTimeoutException)
    }

    @Test
    fun aRefusedCredentialEndsTheStreamBeforeAnyEventAndOtherFailuresRetry() {
        val api = TogglApiClient(url, credentials)
        server.enqueue(answer(403, "application/json", "\"Incorrect username and/or password\""))
        server.enqueue(answer(401, "text/html", "<p>Unauthorized</p>"))
        server.enqueue(answer(502, "text/html", "<p>Bad gateway</p>"))
        server.enqueue(answer(200, "application/json", json(state(1))))

        assertEquals(StreamEnd.Refused, holdTimerEvents(api, holder, link.link))
        assertEquals("a proxy's 401", StreamEnd.Refused, holdTimerEvents(api, holder, link.link))
        assertEquals(StreamEnd.Failed(ApiError.UnexpectedStatus(502)), holdTimerEvents(api, holder, link.link))
        val notAStream = holdTimerEvents(api, holder, link.link)
        assertTrue(notAStream.toString(), notAStream is StreamEnd.Failed && notAStream.error is ApiError.InvalidResponse)
        assertNull("no state came from any of them", holder.state.value)
        assertEquals(0, link.connected)

        assertEquals(StreamEnd.SignedOut, holdTimerEvents(null, holder, link.link))
        server.close()
        val unreachable = holdTimerEvents(api, holder, link.link)
        assertTrue(unreachable.toString(), unreachable is StreamEnd.Failed && unreachable.error is ApiError.Network)
    }

    /** [text] in reads of at most [size] bytes, as a slow connection delivers it. */
    private fun chunks(text: String, size: Int): InputStream = object : InputStream() {
        private val bytes = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

        override fun read(): Int = bytes.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = bytes.read(b, off, minOf(len, size))
    }

    private fun eventStream(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/event-stream; charset=utf-8")
        .addHeader("Cache-Control", "no-store")
        .body(body)

    private fun answer(code: Int, type: String, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", type).body(body).build()

    private fun json(state: TimerState) = ApiJson.encodeToString(TimerState.serializer(), state)

    /** One `timer` event as Tokkl 1.10.0 writes it. */
    private fun timerEvent(state: TimerState) = "event: timer\ndata: ${json(state)}\n\n"

    private fun state(revision: Long, running: ServerTimeEntry? = null) =
        TimerState(revision, SERVER_TIME, running, pause = null, pomodoro = null, discardedPomodoro = null)

    private fun entry(id: Long, description: String) = ServerTimeEntry(
        id = id,
        workspaceId = 7,
        description = description,
        start = "2026-10-28T08:00:00+00:00",
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    /** A link that counts what the stream told it. */
    private class RecordingLink {
        var connected = 0
        var alive = 0
        val link = TimerSyncLink(onConnected = { connected++ }, onAlive = { alive++ }, onEnded = { _, _ -> })
    }

    private class RecordingCache : CurrentEntryCache {
        val written = mutableListOf<CachedTimeEntry?>()

        override fun runningCheckStart() = RunningCheckStart(emptyList())

        override fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?): RunningCheckWrites? {
            written += server
            return RunningCheckWrites(upsert = server)
        }
    }

    private companion object {
        val SERVER_TIME: Instant = Instant.parse("2026-10-28T09:00:00.000Z")

        /** Tokkl's keep-alive: an event with an empty data line, not a comment. */
        const val KEEP_ALIVE = "event: keep-alive\ndata:\n\n"
    }
}
