package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.catalog.CatalogChange
import com.github.e0ipso.tokkl.model.Client
import com.github.e0ipso.tokkl.model.ClientId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.platform.TestApp
import com.github.e0ipso.tokkl.platform.TestScreen
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.accountSettingsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The clients page's model against a MockWebServer with one client, Acme, in workspace 7, and one
 * project of Acme's. Unconfined runs each request to the end inside the call.
 */
class ClientsViewModelTest {

    private val testApi = TestApi(null)

    private val server = MockWebServer()

    /** Each request's method, path and body, in order. */
    private val requests = CopyOnWriteArrayList<String>()
    private val refresh = CountingRefresh()
    private val offline = MutableStateFlow(false)

    /** While true, the server answers every request with a 503. */
    @Volatile
    private var failing = false
    private lateinit var model: ClientsViewModel
    private lateinit var screen: TestScreen
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val app = TestApp(scheduler)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}".trim()
                if (failing) return MockResponse.Builder().code(503).build()
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/v9/workspaces/7/clients" -> json("""[{"id":1,"wid":7,"name":"Acme","archived":false}]""")

                    "GET /api/v9/workspaces/7/projects" -> json("""[{"id":5,"client_id":1}]""")

                    "POST /api/v9/workspaces/7/clients" -> json(
                        """{"id":2,"wid":7,"name":"Surf school","archived":false}""",
                    )

                    "POST /api/v9/workspaces/7/clients/1/archive" -> json("[5]")

                    "POST /api/v9/workspaces/7/clients/1/restore" -> json(
                        """{"id":1,"wid":7,"name":"Acme","archived":false}""",
                    )

                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        testApi.stored = url
        // The user has confirmed the MockWebServer's plain http URL.
        testApi.trust = InMemoryServerTrust(url)
        model = ClientsViewModel(
            repository = ClientsRepository { testApi.api(url, ApiCredentials.ApiToken("tok-123")) },
            settings = accountSettingsIn(WorkspaceId(7)),
            refreshes = refresh,
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedClients = flowOf(emptyList()),
            offline = offline,
            app = app.app,
        )
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * A page that could not load loads again when the network comes back. A rotation loads
     * nothing, and a page that loaded or went asks nothing.
     */
    @Test
    fun theNetworksReturnLoadsAgainWhatFailed() {
        failing = true
        screen.show()
        assertEquals("the first load", 1, clientLoads())
        assertNull(model.state.value.data)

        app.reconnect()
        assertEquals("the network came back", 2, clientLoads())

        screen.hide()
        screen.show()
        assertEquals("a rotation loads nothing", 2, clientLoads())

        failing = false
        app.reconnect()
        assertEquals(3, clientLoads())
        assertEquals("Acme", model.state.value.data!!.clients.single().name)
        app.reconnect()
        assertEquals("a page that loaded asks nothing", 3, clientLoads())

        screen.leave()
        app.reconnect()
        assertEquals("a page that went asks nothing", 3, clientLoads())
    }

    private fun clientLoads(): Int = requests.count { it == "GET /api/v9/workspaces/7/clients" }

    /**
     * Another activity hides the page for longer than its state is shared. The open form stays,
     * and the page loads the clients again when it shows.
     */
    @Test
    fun theOpenFormSurvivesTheScreenHiddenForLonger() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        model.edit(acme)

        screen.leave()
        screen.show()

        assertEquals(ClientsOverlay.Edit(acme), model.state.value.overlay)
        assertEquals("Acme", model.state.value.data!!.clients.single().name)
        assertEquals(2, clientLoads())
    }

    /** Archiving asks first, since it archives the client's projects too. */
    @Test
    fun archivingAsksFirstThenArchivesTheClientAndRefreshesTheCache() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        assertEquals(1, model.state.value.data!!.projectCount(acme))

        model.askToArchive(acme)
        assertEquals(ClientsOverlay.ConfirmArchive(acme), model.state.value.overlay)
        val asked = requests.size
        model.archive()

        assertEquals("POST /api/v9/workspaces/7/clients/1/archive {}", requests[asked])
        assertEquals(CatalogChange.Archived("Acme"), model.state.value.done)
        assertNull("the question closes", model.state.value.overlay)
        assertEquals(true, model.state.value.data!!.clients.single().archived)
        assertEquals("the cache's projects changed too", 1, refresh.afterChanges)
    }

    /** Restoring brings the client's projects back too, as the web app does. */
    @Test
    fun restoringBringsTheProjectsBack() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        val opened = requests.size

        model.restore(acme.copy(archived = true))

        assertEquals("""POST /api/v9/workspaces/7/clients/1/restore {"restore_all_projects":true}""", requests[opened])
        assertEquals(CatalogChange.Restored("Acme"), model.state.value.done)
        assertEquals(false, model.state.value.data!!.clients.single().archived)
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun aNewClientGoesWithItsNameTrimmedAndBlankNotesAsNone() {
        screen.show()
        model.newClient()
        val opened = requests.size

        model.save("  Surf school ", "   ")

        assertEquals("""POST /api/v9/workspaces/7/clients {"name":"Surf school","notes":null}""", requests[opened])
        assertEquals(CatalogChange.Created("Surf school"), model.state.value.done)
        assertEquals("sorted by name", listOf("Acme", "Surf school"), model.state.value.data!!.clients.map { it.name })
    }

    @Test
    fun thePageListsTheCachedClientsOfItsWorkspaceAndSaysWhenThePhoneIsOffline() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val offline = MutableStateFlow(false)
        val page = ClientsViewModel(
            repository = ClientsRepository {
                testApi.api(checkNotNull(testApi.stored), ApiCredentials.ApiToken("tok-123"))
            },
            settings = accountSettingsIn(WorkspaceId(7)),
            refreshes = CountingRefresh(),
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedClients = MutableStateFlow(
                listOf(CACHED_ACME, Client(ClientId(9), WorkspaceId(8), "Elsewhere", archived = false)),
            ),
            offline = offline,
            app = TestApp(testScheduler).app,
        )
        val reader = scope.launch { page.page.collect {} }
        offline.value = true

        val shown = page.page.first { it.offline }
        assertEquals(listOf(CACHED_ACME), shown.cachedClients)
        assertEquals(listOf("Acme"), shown.catalog.data!!.clients.map { it.name })
        reader.cancel()
        scope.cancel()
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        val CACHED_ACME = Client(ClientId(1), WorkspaceId(7), "Acme", archived = false)
    }
}
