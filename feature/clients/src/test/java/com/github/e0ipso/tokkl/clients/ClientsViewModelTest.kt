package com.github.e0ipso.tokkl.clients

import androidx.lifecycle.SavedStateHandle
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
import com.github.e0ipso.tokkl.session.ServerChanges
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * The clients page's model over a MockWebServer whose workspace 7 has one client, Acme, with one
 * project. The server answers the update, the archive and the restore of Acme and the create of
 * Surf school, and fails the list as a proxy whose server is down while [unreachable]. Unconfined
 * runs each call to the end inside the call.
 */
class ClientsViewModelTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()

    /** Each change the page sent: method, path and body, in order. */
    private val changes = CopyOnWriteArrayList<String>()

    /** The times the page asked for the clients. */
    private val loads = AtomicInteger()

    @Volatile
    private var unreachable = false
    private lateinit var repository: ServerClientsRepository
    private val refresh = CountingRefresh()
    private val offline = MutableStateFlow(false)
    private lateinit var model: ClientsViewModel
    private lateinit var screen: TestScreen
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))
    private val app = TestApp(scheduler)
    private val saved = SavedStateHandle()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val call = "${request.method} ${request.url.encodedPath}"
                val body = request.body?.utf8().orEmpty()
                if (request.method != "GET") changes += "$call $body".trim()
                return when (call) {
                    "GET /api/v9/workspaces/7/clients" -> {
                        loads.incrementAndGet()
                        if (unreachable) MockResponse.Builder().code(503).build() else json("[$ACME]")
                    }

                    "GET /api/v9/workspaces/7/projects" -> json("""[{"id":5,"client_id":1}]""")

                    "POST /api/v9/workspaces/7/clients" -> json("""{"id":2,"wid":7,"name":"Surf school"}""")

                    // The client as the page sent it.
                    "PUT /api/v9/workspaces/7/clients/1" -> json("""{"id":1,"wid":7,""" + body.removePrefix("{"))

                    "POST /api/v9/workspaces/7/clients/1/archive" -> json("[5]")

                    "POST /api/v9/workspaces/7/clients/1/restore" -> json(ACME)

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
        repository = ServerClientsRepository { testApi.api(url) }
        model = clientsModel(saved)
        screen = TestScreen(model.page, scope, scheduler)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun clientsModel(saved: SavedStateHandle) = ClientsViewModel(
        repository = repository,
        settings = accountSettingsIn(WorkspaceId(7)),
        changes = ServerChanges(scope, Dispatchers.Unconfined, refresh),
        scope = scope,
        io = Dispatchers.Unconfined,
        cachedClients = flowOf(emptyList()),
        offline = offline,
        app = app.app,
        saved = saved,
    )

    /** The page's model in a new process, which gets back only what the old one's saved state held. */
    private fun clientsModelAfterProcessDeath(): ClientsViewModel =
        clientsModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))

    /**
     * A page that could not load loads again when the network comes back. A rotation loads
     * nothing, and a page that loaded or went asks nothing.
     */
    @Test
    fun theNetworksReturnLoadsAgainWhatFailed() {
        unreachable = true
        screen.show()
        assertEquals("the first load", 1, loads.get())
        assertNull(model.state.value.data)

        app.reconnect()
        assertEquals("the network came back", 2, loads.get())

        screen.hide()
        screen.show()
        assertEquals("a rotation loads nothing", 2, loads.get())

        unreachable = false
        app.reconnect()
        assertEquals(3, loads.get())
        assertEquals("Acme", model.state.value.data!!.clients.single().name)
        app.reconnect()
        assertEquals("a page that loaded asks nothing", 3, loads.get())

        screen.leave()
        app.reconnect()
        assertEquals("a page that went asks nothing", 3, loads.get())
    }

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
        assertEquals(2, loads.get())
    }

    /**
     * The process dies with Acme's form open. The page that a new process restores opens that
     * form again with what was typed, before the list loads, and Save sends it once it has.
     */
    @Test
    fun aNewProcessOpensTheEditFormAsItWasTyped() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        model.edit(acme)
        model.editDraft(ClientDraft("Acme Corp", "Pays late"))

        val restored = clientsModelAfterProcessDeath()

        val typed = ClientsOverlay.Edit(acme, ClientDraft("Acme Corp", "Pays late"))
        assertEquals("open before the list loads", typed, restored.state.value.overlay)
        TestScreen(restored.page, scope, scheduler).show()
        assertEquals(typed, restored.state.value.overlay)
        restored.save("Acme Corp", "Pays late")
        assertEquals(listOf("""PUT /api/v9/workspaces/7/clients/1 {"name":"Acme Corp","notes":"Pays late"}"""), changes)
    }

    @Test
    fun aNewProcessOpensTheNewClientFormAsItWasTypedAndNoClosedOne() {
        screen.show()
        model.newClient()
        model.editDraft(ClientDraft("Surf", ""))

        assertEquals(ClientsOverlay.New(ClientDraft("Surf", "")), clientsModelAfterProcessDeath().state.value.overlay)

        model.dismiss()
        assertNull(clientsModelAfterProcessDeath().state.value.overlay)
    }

    @Test
    fun aNewProcessAsksAgainWhetherToArchiveTheClient() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        model.askToArchive(acme)

        val restored = clientsModelAfterProcessDeath()

        assertEquals(ClientsOverlay.ConfirmArchive(acme), restored.state.value.overlay)
        TestScreen(restored.page, scope, scheduler).show()
        restored.archive()
        assertEquals(listOf("POST /api/v9/workspaces/7/clients/1/archive {}"), changes)
    }

    /** Archiving asks first, since it archives the client's projects too. */
    @Test
    fun archivingAsksFirstThenArchivesTheClientAndRefreshesTheCache() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        assertEquals(1, model.state.value.data!!.projectCount(acme))

        model.askToArchive(acme)
        assertEquals(ClientsOverlay.ConfirmArchive(acme), model.state.value.overlay)
        assertEquals("asking sends nothing", emptyList<String>(), changes)
        model.archive()

        assertEquals(listOf("POST /api/v9/workspaces/7/clients/1/archive {}"), changes)
        assertEquals(CatalogChange.Archived("Acme"), model.state.value.done)
        assertNull("the question closes", model.state.value.overlay)
        assertEquals(true, model.state.value.data!!.clients.single().archived)
        assertEquals("the cache's projects changed too", 1, refresh.afterChanges)
    }

    /** Restoring brings the client back as the server sent it, and the cache catches up. */
    @Test
    fun restoringBringsTheProjectsBack() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()

        model.restore(acme.copy(archived = true))

        assertEquals(listOf("""POST /api/v9/workspaces/7/clients/1/restore {"restore_all_projects":true}"""), changes)
        assertEquals(CatalogChange.Restored("Acme"), model.state.value.done)
        assertEquals(false, model.state.value.data!!.clients.single().archived)
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun aNewClientGoesWithItsNameTrimmedAndBlankNotesAsNone() {
        screen.show()
        model.newClient()

        model.save("  Surf school ", "   ")

        assertEquals(listOf("""POST /api/v9/workspaces/7/clients {"name":"Surf school","notes":null}"""), changes)
        assertEquals(CatalogChange.Created("Surf school"), model.state.value.done)
        assertEquals("sorted by name", listOf("Acme", "Surf school"), model.state.value.data!!.clients.map { it.name })
    }

    @Test
    fun thePageListsTheCachedClientsOfItsWorkspaceAndSaysWhenThePhoneIsOffline() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val offline = MutableStateFlow(false)
        val page = ClientsViewModel(
            repository = repository,
            settings = accountSettingsIn(WorkspaceId(7)),
            changes = ServerChanges(scope, Dispatchers.Unconfined, CountingRefresh()),
            scope = scope,
            io = Dispatchers.Unconfined,
            cachedClients = MutableStateFlow(
                listOf(CACHED_ACME, Client(ClientId(9), WorkspaceId(8), "Elsewhere", archived = false)),
            ),
            offline = offline,
            app = TestApp(testScheduler).app,
            saved = SavedStateHandle(),
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
        const val ACME = """{"id":1,"wid":7,"name":"Acme","archived":false}"""

        val CACHED_ACME = Client(ClientId(1), WorkspaceId(7), "Acme", archived = false)
    }
}
