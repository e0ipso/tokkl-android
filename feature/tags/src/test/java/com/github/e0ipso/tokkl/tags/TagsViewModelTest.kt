package com.github.e0ipso.tokkl.tags

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.accountSettingsIn
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The tags page's model over a MockWebServer whose workspace 7 has one tag, Work, and which
 * answers no change. Unconfined runs each load to the end inside the call.
 */
class TagsViewModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()

    /** The times the page asked for the tags. */
    private val loads = AtomicInteger()
    private lateinit var repository: ServerTagsRepository
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private lateinit var model: TagsViewModel
    private lateinit var screen: TestScreen

    private val saved = SavedStateHandle()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/workspaces/7/tags" -> {
                        loads.incrementAndGet()
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "application/json")
                            .body("""[{"id":3,"workspace_id":7,"name":"Work"}]""")
                            .build()
                    }

                    else -> MockResponse.Builder().code(404).build()
                }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
        repository = ServerTagsRepository { testApi.api(url) }
        model = tagsModel(saved)
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun tagsModel(saved: SavedStateHandle) = TagsViewModel(
        repository = repository,
        settings = accountSettingsIn(WorkspaceId(7)),
        changes = ServerChanges(scope, Dispatchers.Unconfined, CountingRefresh()),
        scope = scope,
        io = Dispatchers.Unconfined,
        cachedTags = flowOf(emptyList()),
        offline = MutableStateFlow(false),
        app = TestApp(scheduler).app,
        saved = saved,
    )

    /** The page as a new process restores it, from what the route's saved state holds now. */
    private fun tagsModelAfterProcessDeath() =
        tagsModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))

    /** The rename form and the delete question come back after process death, about the same tag. */
    @Test
    fun aNewProcessOpensTheRenameFormOrTheDeleteQuestionAgain() {
        screen.show()
        val work = model.state.value.data!!.tags.single()

        model.askToRename(work)
        assertEquals(TagsOverlay.Rename(work), tagsModelAfterProcessDeath().state.value.overlay)

        model.dismiss()
        model.askToDelete(work)
        assertEquals(TagsOverlay.ConfirmDelete(work), tagsModelAfterProcessDeath().state.value.overlay)

        model.dismiss()
        assertNull(tagsModelAfterProcessDeath().state.value.overlay)
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
        assertEquals(2, loads.get())
    }
}
