package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.ClassReferences
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.cache.CREATED_WITH
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The timer actions against a stand-in Tokkl server: what each request carries, which answers
 * reach the account's timer state, and that a failed action changes nothing on the phone.
 */
class TimerActionsTest {

    private val server = MockWebServer()
    private val credentials = ApiCredentials.ApiToken("tok-123")
    private val cache = RecordingCache()
    private var saves = 0
    private lateinit var url: ServerUrl
    private lateinit var holder: TimerStateSource
    private var signedIn = true
    private var trustListener: TrustListener? = null
    private lateinit var actions: TimerActions

    @Before
    fun setUp() {
        server.start()
        // The server sits under a path, as a Tokkl behind a reverse proxy can.
        url = ServerUrl.parse(server.url("/tokkl/").toString())!!
        ConfiguredServer.readStoredFrom { url }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(url))
        holder = TimerStateSource(initial = null, save = { saves++ }, cache = cache)
        actions = TimerActions(
            client = { if (signedIn) TogglApiClient(url, credentials, trustListener = trustListener) else null },
            source = { holder },
            io = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun revisionedActionsSendTheRevisionTheyWereShownAtNotTheNewerHeldOne() {
        // The phone holds revision 9, and a notification posted at revision 7 is tapped.
        hold(state(revision = 9))
        val shown = 7L
        val sent: List<Pair<String, suspend () -> TimerActionResult>> = listOf(
            "/tokkl/api/app/timer/resume" to suspend { actions.resume(shown) },
            "/tokkl/api/app/timer/discard" to suspend { actions.discard(shown) },
            "/tokkl/api/app/pomodoro/next" to suspend { actions.nextPhase(shown) },
            "/tokkl/api/app/pomodoro/end" to suspend { actions.endSession(shown) },
        )

        for ((path, action) in sent) {
            server.enqueue(answer(409, state(revision = 9)))

            assertEquals(path, TimerActionResult.Refused, runBlocking { action() })

            val request = server.takeRequest()
            assertEquals(path, "POST" to path, request.method to request.url.encodedPath)
            assertEquals(path, credentials.authorizationHeader, request.headers["Authorization"])
            assertEquals(path, json("""{"revision":7}"""), json(request.body!!.utf8()))
        }
    }

    @Test
    fun pauseAndSessionStartSendTheBodiesTheReleaseDefines() {
        server.enqueue(answer(200, state(revision = 1)))
        server.enqueue(answer(200, state(revision = 2)))
        val fields = EntryFields(description = "Write the report", projectId = 5, taskId = null, tags = listOf("deep", "writing"), billable = true)

        assertEquals(TimerActionResult.Done, runBlocking { actions.pause() })
        assertEquals(
            TimerActionResult.Done,
            runBlocking { actions.startSession(workspaceId = 7, fields = fields, lengths = SessionLengths(50, 10, 30, 3)) },
        )

        val pause = server.takeRequest()
        assertEquals("POST" to "/tokkl/api/app/timer/pause", pause.method to pause.url.encodedPath)
        // Pause reads no field, but the start, next, end, resume and discard routes need an object.
        assertEquals(json("{}"), json(pause.body!!.utf8()))

        val start = server.takeRequest()
        assertEquals("POST" to "/tokkl/api/app/pomodoro", start.method to start.url.encodedPath)
        assertEquals(credentials.authorizationHeader, start.headers["Authorization"])
        assertEquals(
            json(
                """
                {"workspace_id":7,"created_with":"$CREATED_WITH","description":"Write the report","project_id":5,
                 "task_id":null,"tags":["deep","writing"],"billable":true,
                 "work_minutes":50,"short_rest_minutes":10,"long_rest_minutes":30,"long_rest_every":3}
                """,
            ),
            json(start.body!!.utf8()),
        )
    }

    @Test
    fun anAnswerOrARefusalPutsTheServersStateInTheHolder() {
        val running = serverEntry(40, "Focus")
        server.enqueue(answer(200, state(revision = 3)))
        server.enqueue(answer(200, state(revision = 4, running = running)))
        server.enqueue(answer(409, state(revision = 6, running = running)))

        assertEquals(TimerActionResult.Done, runBlocking { actions.read() })
        assertEquals("GET" to "/tokkl/api/app/timer", server.takeRequest().let { it.method to it.url.encodedPath })
        assertEquals(3L, holder.state.value?.state?.revision)

        assertEquals(TimerActionResult.Done, runBlocking { actions.resume(3) })
        assertEquals(4L, holder.state.value?.state?.revision)
        assertEquals("the running entry reaches the cache", listOf(40L), cache.written.filterNotNull().map { it.id })

        val refused = runBlocking { actions.nextPhase(4) }
        assertEquals(TimerActionResult.Refused, refused)
        assertEquals(R.string.timer_action_refused, refused.message)
        assertEquals("the refusal's state is held", 6L, holder.state.value?.state?.revision)
    }

    @Test
    fun anAnswerOlderThanTheHeldStateDoesNotReplaceIt() {
        // The stream delivered revision 15 while the actions were on their way.
        hold(state(revision = 15))
        server.enqueue(answer(200, state(revision = 14)))
        server.enqueue(answer(409, state(revision = 13)))

        assertEquals(TimerActionResult.Done, runBlocking { actions.pause() })
        assertEquals(TimerActionResult.Refused, runBlocking { actions.endSession(13) })

        assertEquals(15L, holder.state.value?.state?.revision)
    }

    @Test
    fun aLostAnswerSaysTheServerIsUnreachableChangesNothingAndIsNotSentAgain() {
        // A read leaves its connection in the pool, so the action reuses it, as a phone often does.
        server.enqueue(answer(200, state(revision = 8, pause = pause())))
        runBlocking { actions.read() }
        server.takeRequest()
        val held = holder.state.value
        val savesBefore = saves
        val writesBefore = cache.written.size
        // The server reads the request, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(answer(200, state(revision = 9)))

        val result = runBlocking { actions.resume(8) }

        assertEquals(TimerActionResult.Offline, result)
        assertEquals(R.string.timer_action_offline, result.message)
        assertEquals("the resume went out once", listOf("/tokkl/api/app/timer/resume"), remainingRequests().map { it.url.encodedPath })
        assertSame("the shown state stays", held, holder.state.value)
        assertEquals("nothing is stored", savesBefore, saves)
        assertEquals("the cache is untouched", writesBefore, cache.written.size)

        // No server at all: the same message, and still nothing changes.
        server.close()
        assertEquals(TimerActionResult.Offline, runBlocking { actions.discard(8) })
        assertSame(held, holder.state.value)
        assertEquals(savesBefore, saves)
    }

    @Test
    fun otherFailuresKeepTheAppsErrorsAndChangeNothing() {
        hold(state(revision = 2))
        val held = holder.state.value
        server.enqueue(answer(403, "\"Incorrect username and/or password\""))
        server.enqueue(answer(400, "\"Invalid work_minutes: expected an integer from 1 to 180\""))
        server.enqueue(answer(409, "\"Time entry already stopped\""))
        server.enqueue(answer(200, """{"revision":"not a state"}"""))
        server.enqueue(answer(409, """{"revision":"not a state"}"""))

        assertEquals(TimerActionResult.Failed(ApiError.AuthFailed), runBlocking { actions.pause() })
        assertEquals(
            TimerActionResult.Failed(ApiError.UnexpectedStatus(400, "Invalid work_minutes: expected an integer from 1 to 180")),
            runBlocking { actions.startSession(7, EntryFields(), SessionLengths(25, 5, 15, 4)) },
        )
        assertEquals(TimerActionResult.Failed(ApiError.UnexpectedStatus(409, "Time entry already stopped")), runBlocking { actions.nextPhase(2) })
        assertTrue(runBlocking { actions.endSession(2) }.let { it is TimerActionResult.Failed && it.error is ApiError.InvalidResponse })
        assertTrue(runBlocking { actions.resume(2) }.let { it is TimerActionResult.Failed && it.error is ApiError.InvalidResponse })
        assertSame(held, holder.state.value)

        // A server the user has not confirmed gets nothing, and the session's trust prompt hears why.
        val refusals = mutableListOf<ApiError.TrustRefused>()
        trustListener = object : TrustListener {
            override fun refused(refusal: ApiError.TrustRefused) {
                refusals += refusal
            }

            override fun answered() = Unit
        }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        val requestsBefore = server.requestCount
        assertEquals(TimerActionResult.Failed(ApiError.CleartextNotConfirmed(url)), runBlocking { actions.discard(2) })
        assertEquals(listOf<ApiError.TrustRefused>(ApiError.CleartextNotConfirmed(url)), refusals)

        // Signed out, there is no account to send for.
        signedIn = false
        assertEquals(TimerActionResult.Failed(ApiError.AuthFailed), runBlocking { actions.pause() })
        assertEquals("nothing reached the server", requestsBefore, server.requestCount)
        assertSame(held, holder.state.value)
    }

    @Test
    fun actionsUseNeitherTheChangeQueueNorTheLocalPhaseRules() {
        val classes = ClassReferences.app.filterKeys { name -> OWN.any { name == it || name.startsWith("$it$") } }
        assertTrue("the scan found the actions", "$PACKAGE/timerstate/TimerActions" in classes)

        val offenders = classes.flatMap { (name, references) ->
            (references.classes + references.members.map { it.owner })
                .filter { reference -> FORBIDDEN.any { reference.startsWith("$PACKAGE/$it") } }
                .map { "$name uses $it" }
        }

        assertTrue("Timer actions go to the server only:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Puts [state] in the holder, as an earlier answer or event would have. */
    private fun hold(state: TimerState) {
        holder.accept(holder.begin(), state)
    }

    private fun answer(code: Int, state: TimerState) = answer(code, ApiJson.encodeToString(TimerState.serializer(), state))

    private fun answer(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun json(text: String): JsonElement = ApiJson.parseToJsonElement(text)

    /** Every request the server has received and the test has not taken yet. */
    private fun remainingRequests(): List<RecordedRequest> =
        generateSequence { server.takeRequest(100, TimeUnit.MILLISECONDS) }.toList()

    private fun state(revision: Long, running: ServerTimeEntry? = null, pause: TimerPause? = null) =
        TimerState(revision, SERVER_TIME, running, pause, pomodoro = null, discardedPomodoro = null)

    private fun serverEntry(id: Long, description: String) = ServerTimeEntry(
        id = id,
        workspaceId = 7,
        description = description,
        start = "2026-10-28T08:00:00+00:00",
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private fun pause() = TimerPause(
        paused = true,
        seconds = 600,
        workspaceId = 7,
        description = "Writing",
        projectId = null,
        taskId = null,
        tagIds = emptyList(),
        tags = emptyList(),
        billable = false,
        cloneEntryId = null,
    )

    /** A cache with no running entry that records each running entry a state writes to it. */
    private class RecordingCache : CurrentEntryCache {
        val written = mutableListOf<CachedTimeEntry?>()

        override fun runningCheckStart() = RunningCheckStart(emptyList(), null)

        override fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?): RunningCheckWrites? {
            written += server
            return RunningCheckWrites(upsert = server)
        }
    }

    private companion object {
        val SERVER_TIME: Instant = Instant.parse("2026-10-28T09:00:00Z")
        const val PACKAGE = "com/github/e0ipso/tokkl"

        /** The classes of the actions and their results. */
        val OWN = listOf("$PACKAGE/timerstate/TimerActions", "$PACKAGE/timerstate/TimerActionResult", "$PACKAGE/timerstate/SessionLengths")

        /** The change queue, its store and sender, and the local session with its phase rules. */
        val FORBIDDEN = listOf(
            "cache/ChangeQueue",
            "cache/ChangeStore",
            "cache/RoomChangeStore",
            "cache/ChangeDao",
            "cache/ChangeSender",
            "cache/PendingChange",
            "cache/BackgroundSend",
            "cache/EntryChanges",
            "pomodoro/PomodoroSessionStore",
            "time/Pomodoro",
        )
    }
}
