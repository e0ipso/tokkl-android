package com.github.e0ipso.tokkl.trash

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.catalog.ServerProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The trash page's model against a MockWebServer that answers by method and path. Unconfined runs
 * each request to its end inside the call.
 */
class TrashModelTest {

    private val server = MockWebServer()
    private lateinit var client: TogglApiClient
    private val answers = mutableMapOf<String, MockResponse>()
    private val requests = mutableListOf<String>()
    private var cacheRefreshes = 0
    private var missing = 0

    private val model by lazy {
        TrashModel(
            client = { client },
            refreshCache = { cacheRefreshes++ },
            trashMissing = { missing++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )
    }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.url.encodedPath}"
                synchronized(requests) { requests += key }
                return answers[key] ?: MockResponse.Builder().code(404).body("\"Not found\"").build()
            }
        }
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { configured }
        ConfiguredTrust.readFrom(InMemoryServerTrust(configured))
        client = TogglApiClient(configured, ApiCredentials.ApiToken("tok-123"))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun openingListsTheDeletedEntries() {
        answers[LIST] = json("[${entry(1, "Write")},${entry(2, null)}]")

        model.open()

        assertEquals(listOf(1L, 2L), model.state.value.entries?.map { it.id })
        assertEquals("Write", model.state.value.entries?.first()?.description)
        assertEquals("Website", model.state.value.entries?.first()?.projectName)
        assertTrue(model.state.value.canChange)
    }

    @Test
    fun aServerWithoutTheTrashHidesItInsteadOfFailing() {
        model.open()

        assertEquals("the 404 told the capabilities", 1, missing)
        assertNull(model.state.value.entries)
    }

    @Test
    fun anAnswerThatIsNotAListHidesTheTrashToo() {
        answers[LIST] = MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html></html>").build()

        model.open()

        assertEquals(1, missing)
    }

    @Test
    fun anUnreachableServerTurnsEveryActionOff() {
        answers[LIST] = json("[${entry(1, "Write")}]")
        server.close()

        model.open()

        assertEquals(ServerProblem.Unreachable, model.state.value.loadProblem)
        assertFalse(model.state.value.canChange)
        assertEquals("an unreachable server is no missing trash", 0, missing)
    }

    @Test
    fun restoreTakesTheEntryOutOfTheTrashAndRefreshesTheCache() {
        answers[LIST] = json("[${entry(1, "Write")},${entry(2, "Read")}]")
        answers["POST /api/app/time_entries/1/restore"] = json(entry(1, "Write"))
        model.open()

        model.restore(1)

        assertEquals(listOf(2L), model.state.value.entries?.map { it.id })
        assertEquals(TrashChange.Restored, model.state.value.done)
        assertEquals(1, cacheRefreshes)
    }

    @Test
    fun purgeDeletesOneEntryAtOnce() {
        answers[LIST] = json("[${entry(1, "Write")},${entry(2, "Read")}]")
        answers["DELETE /api/app/trash/2"] = json("""{"deleted":1}""")
        model.open()

        model.purge(2)

        assertEquals(listOf(1L), model.state.value.entries?.map { it.id })
        assertEquals(TrashChange.Purged, model.state.value.done)
        assertEquals("a purge leaves the cache alone", 0, cacheRefreshes)
    }

    @Test
    fun aPurgeOfAnEntryNoLongerInTheTrashLoadsTheListAgain() {
        answers[LIST] = json("[${entry(1, "Write")}]")
        answers["DELETE /api/app/trash/1"] = MockResponse.Builder().code(404).body("\"Time entry not found in trash\"").build()
        model.open()
        answers[LIST] = json("[]")

        model.purge(1)

        assertEquals(ServerProblem.Refused("Time entry not found in trash"), model.state.value.changeProblem)
        assertEquals(emptyList<TrashEntry>(), model.state.value.entries)
        assertEquals("a missing entry is no missing trash", 0, missing)
    }

    @Test
    fun emptyingSendsNothingUntilTheQuestionIsAnswered() {
        answers[LIST] = json("[${entry(1, "Write")},${entry(2, "Read")}]")
        answers["DELETE /api/app/trash"] = json("""{"deleted":2}""")
        model.open()

        model.empty()
        assertFalse("DELETE /api/app/trash" in requests)

        model.askToEmpty()
        assertTrue(model.state.value.confirmingEmpty)
        model.empty()

        assertTrue("DELETE /api/app/trash" in requests)
        assertEquals(emptyList<TrashEntry>(), model.state.value.entries)
        assertFalse(model.state.value.confirmingEmpty)
        assertEquals(TrashChange.Emptied(2), model.state.value.done)
    }

    @Test
    fun anEmptyTrashAsksNothing() {
        answers[LIST] = json("[]")
        model.open()

        model.askToEmpty()

        assertFalse(model.state.value.confirmingEmpty)
    }

    @Test
    fun cancellingTheQuestionKeepsTheTrash() {
        answers[LIST] = json("[${entry(1, "Write")}]")
        model.open()
        model.askToEmpty()

        model.dismiss()
        model.empty()

        assertFalse("DELETE /api/app/trash" in requests)
        assertEquals(listOf(1L), model.state.value.entries?.map { it.id })
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    /** A deleted entry as Tokkl 1.3 lists it, with its meta fields. */
    private fun entry(id: Long, description: String?): String {
        val text = description?.let { "\"$it\"" } ?: "null"
        return """{"id":$id,"workspace_id":7,"project_id":3,"task_id":null,"billable":false,""" +
            """"start":"2026-10-19T09:00:00+00:00","stop":"2026-10-19T10:30:00+00:00","duration":5400,""" +
            """"description":$text,"tags":[],"tag_ids":[],"server_deleted_at":"2026-10-20T08:00:00+00:00",""" +
            """"project_name":"Website","project_color":"#06aaf5","client_name":null}"""
    }

    private companion object {
        const val LIST = "GET /api/app/trash"
    }
}
