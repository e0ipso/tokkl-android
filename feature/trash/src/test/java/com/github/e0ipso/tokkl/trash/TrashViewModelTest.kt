package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.capabilitiesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The trash page's model over a MockWebServer that answers each call as the test sets it, and
 * with a 404, as a server without the trash does, for anything it does not. Unconfined runs each
 * call to its end inside the call.
 */
class TrashViewModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()
    private val trash = AnsweringTrash()
    private val refresh = CountingRefresh()
    private val capabilities = capabilitiesOf(Capabilities.NONE.copy(trash = true, restore = true))

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val screen by lazy { TestScreen(model.page, scope, scheduler) }
    private val model by lazy {
        TrashViewModel(
            repository = ServerTrashRepository { testApi.api() },
            changes = ServerChanges(scope, Dispatchers.Unconfined, refresh),
            trashMissing = capabilities::trashMissing,
            scope = scope,
            io = Dispatchers.Unconfined,
            settings = MutableStateFlow(null),
            offline = MutableStateFlow(false),
            app = TestApp(scheduler).app,
        )
    }

    @Before
    fun setUp() {
        server.dispatcher = trash
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun openingListsTheDeletedEntries() {
        trash.list = list(entry(1, "Write"), entry(2, null))

        screen.show()

        assertEquals(listOf(1L, 2L), model.state.value.entries?.map { it.id.value })
        assertEquals("Write", model.state.value.entries?.first()?.description)
        assertEquals("Website", model.state.value.entries?.first()?.projectName)
        assertTrue(model.state.value.canChange)
    }

    @Test
    fun aServerWithoutTheTrashHidesItInsteadOfFailing() {
        screen.show()

        assertFalse("the 404 told the capabilities", capabilities.state.value.trash)
        assertNull(model.state.value.entries)
    }

    @Test
    fun anAnswerThatIsNotAListHidesTheTrashToo() {
        trash.list = json("""{"entries":[]}""")

        screen.show()

        assertFalse(capabilities.state.value.trash)
    }

    @Test
    fun anUnreachableServerTurnsEveryActionOff() {
        // The connection drops before any answer, as when the phone has no network.
        trash.list = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

        screen.show()

        assertEquals(ServerProblem.Unreachable, model.state.value.loadProblem)
        assertFalse(model.state.value.canChange)
        assertTrue("an unreachable server is no missing trash", capabilities.state.value.trash)
    }

    @Test
    fun restoreTakesTheEntryOutOfTheTrashAndRefreshesTheCache() {
        trash.list = list(entry(1, "Write"), entry(2, "Read"))
        trash.restored[1] = json(entry(1, "Write"))
        screen.show()

        model.restore(EntryId(1))

        assertEquals(listOf(2L), model.state.value.entries?.map { it.id.value })
        assertEquals(TrashChange.Restored, model.state.value.done)
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun purgeDeletesOneEntryAtOnce() {
        trash.list = list(entry(1, "Write"), entry(2, "Read"))
        trash.purged[2] = json("""{"deleted":1}""")
        screen.show()

        model.purge(EntryId(2))

        assertEquals(listOf(1L), model.state.value.entries?.map { it.id.value })
        assertEquals(TrashChange.Purged, model.state.value.done)
        assertEquals("a purge leaves the cache alone", 0, refresh.afterChanges)
    }

    @Test
    fun aPurgeOfAnEntryNoLongerInTheTrashLoadsTheListAgain() {
        trash.list = list(entry(1, "Write"))
        trash.purged[1] = MockResponse.Builder().code(404).body("\"Time entry not found in trash\"").build()
        screen.show()
        trash.list = list()

        model.purge(EntryId(1))

        assertEquals(ServerProblem.Refused("Time entry not found in trash"), model.state.value.changeProblem)
        assertEquals(emptyList<DeletedEntry>(), model.state.value.entries)
        assertTrue("a missing entry is no missing trash", capabilities.state.value.trash)
    }

    @Test
    fun emptyingSendsNothingUntilTheQuestionIsAnswered() {
        trash.list = list(entry(1, "Write"), entry(2, "Read"))
        trash.purgedAll = json("""{"deleted":2}""")
        screen.show()

        model.empty()
        assertFalse(PURGE_ALL in trash.calls)

        model.askToEmpty()
        assertTrue(model.state.value.confirmingEmpty)
        model.empty()

        assertTrue(PURGE_ALL in trash.calls)
        assertEquals(emptyList<DeletedEntry>(), model.state.value.entries)
        assertFalse(model.state.value.confirmingEmpty)
        assertEquals(TrashChange.Emptied(2), model.state.value.done)
    }

    @Test
    fun anEmptyTrashAsksNothing() {
        trash.list = list()
        screen.show()

        model.askToEmpty()

        assertFalse(model.state.value.confirmingEmpty)
    }

    @Test
    fun cancellingTheQuestionKeepsTheTrash() {
        trash.list = list(entry(1, "Write"))
        screen.show()
        model.askToEmpty()

        model.dismiss()
        model.empty()

        assertFalse(PURGE_ALL in trash.calls)
        assertEquals(listOf(1L), model.state.value.entries?.map { it.id.value })
    }

    /**
     * Another activity hides the page for longer than its state is shared. The question whether
     * to empty the trash stays, and the page loads the trash again when it shows.
     */
    @Test
    fun theQuestionSurvivesTheScreenHiddenForLonger() {
        trash.list = list(entry(1, "Write"))
        screen.show()
        model.askToEmpty()

        screen.leave()
        screen.show()

        assertTrue(model.state.value.confirmingEmpty)
        assertEquals(listOf(1L), model.state.value.entries?.map { it.id.value })
        assertEquals(2, trash.calls.count { it == LIST })
    }

    /** A deleted entry of Website, 90 minutes from 9:00 on 19 October, as `GET /api/app/trash` lists it. */
    private fun entry(id: Long, description: String?) = """{"id":$id,"description":""" +
        (description?.let { "\"$it\"" } ?: "null") +
        ""","start":"2026-10-19T09:00:00+00:00","stop":"2026-10-19T10:30:00+00:00","duration":5400,""" +
        """"project_id":3,"project_name":"Website","server_deleted_at":"2026-10-20T08:00:00Z"}"""

    /** The trash holding [entries]. */
    private fun list(vararg entries: String) = json(entries.joinToString(",", "[", "]"))

    /**
     * Answers each call as set, and a 404 as a server without the trash does otherwise. Records
     * each call's method and path.
     */
    private class AnsweringTrash : Dispatcher() {
        @Volatile
        var list: MockResponse = NOT_FOUND
        val restored = ConcurrentHashMap<Long, MockResponse>()
        val purged = ConcurrentHashMap<Long, MockResponse>()

        @Volatile
        var purgedAll: MockResponse = NOT_FOUND
        val calls = CopyOnWriteArrayList<String>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val call = "${request.method} ${request.url.encodedPath}"
            calls += call
            val id = request.url.pathSegments.firstNotNullOfOrNull { it.toLongOrNull() }
            val answer = when (call) {
                LIST -> list
                PURGE_ALL -> purgedAll
                "POST /api/app/time_entries/$id/restore" -> id?.let { restored[it] }
                "DELETE /api/app/trash/$id" -> id?.let { purged[it] }
                else -> null
            }
            return answer ?: NOT_FOUND
        }
    }

    private companion object {
        const val LIST = "GET /api/app/trash"
        const val PURGE_ALL = "DELETE /api/app/trash"
        val NOT_FOUND: MockResponse = MockResponse.Builder().code(404).body("\"Not found\"").build()

        fun json(body: String): MockResponse = MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
    }
}
