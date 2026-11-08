package com.github.e0ipso.tokkl.tags

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The tags page's model against a MockWebServer with one tag, Work, in workspace 7. Unconfined
 * runs each request to the end inside the call.
 */
class TagsViewModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private lateinit var model: TagsViewModel
    private lateinit var screen: TestScreen

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath}"
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/workspaces/7/tags" -> MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body("""[{"id":3,"workspace_id":7,"name":"Work"}]""")
                        .build()

                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        testApi.stored = url
        testApi.trust = InMemoryServerTrust(url)
        model = TagsViewModel(
            repository = TagsRepository { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            workspaceId = { WorkspaceId(7) },
            refreshCache = {},
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedTags = flowOf(emptyList()),
            offline = MutableStateFlow(false),
            app = TestApp(scheduler).app,
        )
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Another activity hides the page for longer than its state is shared. The open rename form
     * stays, and the page loads the tags again when it shows.
     */
    @Test
    fun theOpenFormSurvivesTheScreenHiddenForLonger() {
        screen.show()
        val work = model.state.value.data!!.tags.single()
        model.askToRename(work)

        screen.leave()
        screen.show()

        assertEquals(TagsOverlay.Rename(work), model.state.value.overlay)
        assertEquals(listOf(work), model.state.value.data?.tags)
        assertEquals(2, requests.count { it == "GET /api/v9/workspaces/7/tags" })
    }
}
