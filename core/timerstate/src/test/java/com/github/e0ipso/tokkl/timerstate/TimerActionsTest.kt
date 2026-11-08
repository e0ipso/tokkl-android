package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.api.TimerPause
import com.github.e0ipso.tokkl.api.TimerState
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.cache.CREATED_WITH
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.core.timerstate.R
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The timer actions against a stand-in Tokkl server: what each request carries, which answers
 * reach the account's timer state, and that a failed action changes nothing on the phone.
 */
class TimerActionsTest {

    private val testApi = TestApi(null)

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
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
        holder = TimerStateSource(
            initial = null,
            store = { saves++ },
            cache = cache,
            clock = Clock.systemUTC(),
            runningEnded = {},
        )
        actions = TimerActions(
            client = { if (signedIn) testApi.api(url, credentials, trustListener = trustListener) else null },
            states = { holder },
            io = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun revisionedActionsSendTheRevisionTheyWereShownAtNotTheNewerHeldOne() = runTest {
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

            assertEquals(path, TimerActionResult.Refused, run { action() })

            val request = server.takeRequest()
            assertEquals(path, "POST" to path, request.method to request.url.encodedPath)
            assertEquals(path, credentials.authorizationHeader, request.headers["Authorization"])
            assertEquals(path, json("""{"revision":7}"""), json(request.body!!.utf8()))
        }
    }

    @Test
    fun pauseAndSessionStartSendTheBodiesTheReleaseDefines() = runTest {
        server.enqueue(answer(200, state(revision = 1)))
        server.enqueue(answer(200, state(revision = 2)))
        val fields = EntryFields(
            description = "Write the report",
            projectId = ProjectId(5),
            taskId = null,
            tags = listOf("deep", "writing"),
            billable = true,
        )

        assertEquals(TimerActionResult.Done, run { actions.pause() })
        assertEquals(
            TimerActionResult.Done,
            run {
                actions.startSession(
                    workspaceId = WorkspaceId(7),
                    fields = fields,
                    lengths = SessionLengths(50, 10, 30, 3),
                )
            },
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
    fun anAnswerOrARefusalPutsTheServersStateInTheHolder() = runTest {
        val running = serverEntry(40, "Focus")
        server.enqueue(answer(200, state(revision = 3)))
        server.enqueue(answer(200, state(revision = 4, running = running)))
        server.enqueue(answer(409, state(revision = 6, running = running)))

        assertEquals(TimerActionResult.Done, run { actions.read() })
        assertEquals("GET" to "/tokkl/api/app/timer", server.takeRequest().let { it.method to it.url.encodedPath })
        assertEquals(3L, holder.state.value?.state?.revision)

        assertEquals(TimerActionResult.Done, run { actions.resume(3) })
        assertEquals(4L, holder.state.value?.state?.revision)
        assertEquals(
            "the running entry reaches the cache",
            listOf(EntryId(40)),
            cache.written.filterNotNull().map { it.id },
        )

        val refused = run { actions.nextPhase(4) }
        assertEquals(TimerActionResult.Refused, refused)
        assertEquals(R.string.timer_action_refused, refused.message)
        assertEquals("the refusal's state is held", 6L, holder.state.value?.state?.revision)
    }

    @Test
    fun anAnswerOlderThanTheHeldStateDoesNotReplaceIt() = runTest {
        // The stream delivered revision 15 while the actions were on their way.
        hold(state(revision = 15))
        server.enqueue(answer(200, state(revision = 14)))
        server.enqueue(answer(409, state(revision = 13)))

        assertEquals(TimerActionResult.Done, run { actions.pause() })
        assertEquals(TimerActionResult.Refused, run { actions.endSession(13) })

        assertEquals(15L, holder.state.value?.state?.revision)
    }

    @Test
    fun aLostAnswerSaysTheServerIsUnreachableChangesNothingAndIsNotSentAgain() = runTest {
        // A read leaves its connection in the pool, so the action reuses it, as a phone often does.
        server.enqueue(answer(200, state(revision = 8, pause = pause())))
        run { actions.read() }
        server.takeRequest()
        val held = holder.state.value
        val savesBefore = saves
        val writesBefore = cache.written.size
        // The server reads the request, then the connection drops before the answer.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(answer(200, state(revision = 9)))

        val result = run { actions.resume(8) }

        assertEquals(TimerActionResult.Offline, result)
        assertEquals(R.string.timer_action_offline, result.message)
        assertEquals(
            "the resume went out once",
            listOf("/tokkl/api/app/timer/resume"),
            remainingRequests().map { it.url.encodedPath },
        )
        assertSame("the shown state stays", held, holder.state.value)
        assertEquals("nothing is stored", savesBefore, saves)
        assertEquals("the cache is untouched", writesBefore, cache.written.size)

        // No server at all: the same message, and still nothing changes.
        server.close()
        assertEquals(TimerActionResult.Offline, run { actions.discard(8) })
        assertSame(held, holder.state.value)
        assertEquals(savesBefore, saves)
    }

    @Test
    fun otherFailuresKeepTheAppsErrorsAndChangeNothing() = runTest {
        hold(state(revision = 2))
        val held = holder.state.value
        server.enqueue(answer(403, "\"Incorrect username and/or password\""))
        server.enqueue(answer(400, "\"Invalid work_minutes: expected an integer from 1 to 180\""))
        server.enqueue(answer(409, "\"Time entry already stopped\""))
        server.enqueue(answer(200, """{"revision":"not a state"}"""))
        server.enqueue(answer(409, """{"revision":"not a state"}"""))

        assertEquals(TimerActionResult.Failed(ApiError.AuthFailed), run { actions.pause() })
        assertEquals(
            TimerActionResult.Failed(
                ApiError.UnexpectedStatus(400, "Invalid work_minutes: expected an integer from 1 to 180"),
            ),
            run { actions.startSession(WorkspaceId(7), EntryFields(), SessionLengths(25, 5, 15, 4)) },
        )
        assertEquals(
            TimerActionResult.Failed(ApiError.UnexpectedStatus(409, "Time entry already stopped")),
            run { actions.nextPhase(2) },
        )
        assertTrue(
            run {
                actions.endSession(
                    2,
                )
            }.let { it is TimerActionResult.Failed && it.error is ApiError.InvalidResponse },
        )
        assertTrue(
            run {
                actions.resume(
                    2,
                )
            }.let { it is TimerActionResult.Failed && it.error is ApiError.InvalidResponse },
        )
        assertSame(held, holder.state.value)

        // A server the user has not confirmed gets nothing, and the session's trust prompt hears why.
        val refusals = mutableListOf<ApiError.TrustRefused>()
        trustListener = object : TrustListener {
            override fun refused(refusal: ApiError.TrustRefused) {
                refusals += refusal
            }

            override fun answered() = Unit
        }
        testApi.trust = InMemoryServerTrust()
        val requestsBefore = server.requestCount
        assertEquals(TimerActionResult.Failed(ApiError.CleartextNotConfirmed(url)), run { actions.discard(2) })
        assertEquals(listOf<ApiError.TrustRefused>(ApiError.CleartextNotConfirmed(url)), refusals)

        // Signed out, there is no account to send for.
        signedIn = false
        assertEquals(TimerActionResult.Failed(ApiError.AuthFailed), run { actions.pause() })
        assertEquals("nothing reached the server", requestsBefore, server.requestCount)
        assertSame(held, holder.state.value)
    }

    /** Puts [state] in the holder, as an earlier answer or event would have. */
    private fun hold(state: TimerState) {
        holder.accept(holder.begin(), state)
    }

    private fun answer(code: Int, state: TimerState) = answer(
        code,
        ApiJson.encodeToString(TimerState.serializer(), state),
    )

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
        val written = mutableListOf<TimeEntry?>()

        override fun runningCheckStart() = RunningCheckStart(emptyList())

        override fun checkedRunning(before: RunningCheckStart, server: TimeEntry?): RunningCheckWrites? {
            written += server
            return RunningCheckWrites(upsert = server)
        }
    }

    private companion object {
        val SERVER_TIME: Instant = Instant.parse("2026-10-28T09:00:00Z")
    }
}
