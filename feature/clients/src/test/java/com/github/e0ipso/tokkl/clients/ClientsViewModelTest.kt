package com.github.e0ipso.tokkl.clients

import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The clients page's model over a repository with one client, Acme, in workspace 7, which has
 * one project. Unconfined runs each call to the end inside the call.
 */
class ClientsViewModelTest {

    private val repository = AcmeRepository()
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
        model = clientsModel(saved)
        screen = TestScreen(model.page, scope, scheduler)
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
        repository.unreachable = true
        screen.show()
        assertEquals("the first load", 1, repository.loads)
        assertNull(model.state.value.data)

        app.reconnect()
        assertEquals("the network came back", 2, repository.loads)

        screen.hide()
        screen.show()
        assertEquals("a rotation loads nothing", 2, repository.loads)

        repository.unreachable = false
        app.reconnect()
        assertEquals(3, repository.loads)
        assertEquals("Acme", model.state.value.data!!.clients.single().name)
        app.reconnect()
        assertEquals("a page that loaded asks nothing", 3, repository.loads)

        screen.leave()
        app.reconnect()
        assertEquals("a page that went asks nothing", 3, repository.loads)
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
        assertEquals(2, repository.loads)
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
        assertEquals(listOf("update 1 to Acme Corp, notes Pays late in 7"), repository.changes)
    }

    /** A new client's form comes back with what was typed, and a closed form stays closed. */
    @Test
    fun aNewProcessOpensTheNewClientFormAsItWasTypedAndNoClosedOne() {
        screen.show()
        model.newClient()
        model.editDraft(ClientDraft("Surf", ""))

        assertEquals(ClientsOverlay.New(ClientDraft("Surf", "")), clientsModelAfterProcessDeath().state.value.overlay)

        model.dismiss()
        assertNull(clientsModelAfterProcessDeath().state.value.overlay)
    }

    /** Archiving asks first, since it archives the client's projects too. */
    @Test
    fun archivingAsksFirstThenArchivesTheClientAndRefreshesTheCache() {
        screen.show()
        val acme = model.state.value.data!!.clients.single()
        assertEquals(1, model.state.value.data!!.projectCount(acme))

        model.askToArchive(acme)
        assertEquals(ClientsOverlay.ConfirmArchive(acme), model.state.value.overlay)
        assertEquals("asking sends nothing", emptyList<String>(), repository.changes)
        model.archive()

        assertEquals(listOf("archive 1 in 7"), repository.changes)
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

        assertEquals(listOf("restore 1 in 7"), repository.changes)
        assertEquals(CatalogChange.Restored("Acme"), model.state.value.done)
        assertEquals(false, model.state.value.data!!.clients.single().archived)
        assertEquals(1, refresh.afterChanges)
    }

    @Test
    fun aNewClientGoesWithItsNameTrimmedAndBlankNotesAsNone() {
        screen.show()
        model.newClient()

        model.save("  Surf school ", "   ")

        assertEquals(listOf("create Surf school, notes null, known [1] in 7"), repository.changes)
        assertEquals(CatalogChange.Created("Surf school"), model.state.value.done)
        assertEquals("sorted by name", listOf("Acme", "Surf school"), model.state.value.data!!.clients.map { it.name })
    }

    @Test
    fun thePageListsTheCachedClientsOfItsWorkspaceAndSaysWhenThePhoneIsOffline() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val offline = MutableStateFlow(false)
        val page = ClientsViewModel(
            repository = AcmeRepository(),
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

    /**
     * Lists Acme, client 1 of workspace 7 with one project, or fails as an unreachable server
     * while [unreachable]. Answers the update, the archive and the restore of Acme and the create
     * of Surf school, and records each change it is sent. Any other change fails the test.
     */
    private class AcmeRepository : ClientsRepository {
        var unreachable = false
        var loads = 0
        val changes = mutableListOf<String>()

        override suspend fun workspace(stored: WorkspaceId?) = ApiResult.Success(checkNotNull(stored))

        override suspend fun load(workspace: WorkspaceId): ApiResult<ClientsData> {
            loads++
            if (unreachable) return ApiResult.Failure(ApiError.Network(IOException("unreachable")))
            return ApiResult.Success(ClientsData(listOf(acme(workspace)), mapOf(ClientId(1) to 1)))
        }

        override suspend fun create(workspace: WorkspaceId, name: String, notes: String?, known: Set<ClientId>) =
            answer("create $name, notes $notes, known ${known.map { it.value }} in ${workspace.value}") {
                check(name == "Surf school") { "the test creates Surf school only, not $name" }
                Client(ClientId(2), workspace, name, archived = false)
            }

        override suspend fun update(workspace: WorkspaceId, id: ClientId, name: String, notes: String?) =
            answer("update ${id.value} to $name, notes $notes in ${workspace.value}") {
                checkAcme(id)
                Client(id, workspace, name, notes = notes)
            }

        override suspend fun archive(workspace: WorkspaceId, id: ClientId) =
            answer("archive ${id.value} in ${workspace.value}") { checkAcme(id) }

        override suspend fun restore(workspace: WorkspaceId, id: ClientId) =
            answer("restore ${id.value} in ${workspace.value}") {
                checkAcme(id)
                acme(workspace)
            }

        private fun <R> answer(change: String, value: () -> R): ApiResult<R> {
            changes += change
            return ApiResult.Success(value())
        }

        private fun checkAcme(id: ClientId) = check(id == ClientId(1)) { "the test changes Acme only, not $id" }

        private fun acme(workspace: WorkspaceId) = Client(ClientId(1), workspace, "Acme", archived = false)
    }

    private companion object {
        val CACHED_ACME = Client(ClientId(1), WorkspaceId(7), "Acme", archived = false)
    }
}
