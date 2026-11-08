package com.github.e0ipso.tokkl.clients

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.catalog.CatalogChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    private var refreshes = 0
    private lateinit var model: ClientsViewModel

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}".trim()
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
            repository = ServerClientsRepository { testApi.client(url, ApiCredentials.ApiToken("tok-123")) },
            workspaceId = { 7L },
            refreshCache = { refreshes++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            cachedClients = flowOf(emptyList()),
            offline = MutableStateFlow(false),
            appEvents = emptyFlow(),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Archiving asks first, since it archives the client's projects too. */
    @Test
    fun archivingAsksFirstThenArchivesTheClientAndRefreshesTheCache() {
        model.open()
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
        assertEquals("the cache's projects changed too", 1, refreshes)
    }

    /** Restoring brings the client's projects back too, as the web app does. */
    @Test
    fun restoringBringsTheProjectsBack() {
        model.open()
        val acme = model.state.value.data!!.clients.single()
        val opened = requests.size

        model.restore(acme.copy(archived = true))

        assertEquals("""POST /api/v9/workspaces/7/clients/1/restore {"restore_all_projects":true}""", requests[opened])
        assertEquals(CatalogChange.Restored("Acme"), model.state.value.done)
        assertEquals(false, model.state.value.data!!.clients.single().archived)
        assertEquals(1, refreshes)
    }

    @Test
    fun aNewClientGoesWithItsNameTrimmedAndBlankNotesAsNone() {
        model.open()
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
            repository = ServerClientsRepository {
                testApi.client(checkNotNull(testApi.stored), ApiCredentials.ApiToken("tok-123"))
            },
            workspaceId = { 7L },
            refreshCache = {},
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedClients = MutableStateFlow(listOf(CACHED_ACME, CachedClient(9, 8, "Elsewhere", archived = false))),
            offline = offline,
            appEvents = emptyFlow(),
        )
        val reader = scope.launch { page.page.collect {} }
        page.open()
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
        val CACHED_ACME = CachedClient(1, 7, "Acme", archived = false)
    }
}
