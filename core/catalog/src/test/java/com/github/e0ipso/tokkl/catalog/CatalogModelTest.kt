package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.CountingRefresh
import com.github.e0ipso.tokkl.session.ServerChanges
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.session.accountSettingsIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * A catalog page's model as its view model's shared state drives it, with a repository that
 * answers from [server] and counts the loads. Unconfined runs each load to the end inside the
 * call.
 */
class CatalogModelTest {

    /** What the server lists, or null when it is out of reach. */
    private var server: List<String>? = listOf("Website")
    private var loads = 0

    /** When set, the next load waits for this answer instead of reading [server]. */
    private var held: CompletableDeferred<ApiResult<List<String>>>? = null

    private val repository = object : CatalogRepository<List<String>> {
        override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = ApiResult.Success(
            checkNotNull(stored),
        )

        override suspend fun load(workspace: WorkspaceId): ApiResult<List<String>> {
            assertEquals(WorkspaceId(7), workspace)
            loads++
            held?.let { answer ->
                held = null
                return answer.await()
            }
            return server?.let {
                ApiResult.Success(
                    it,
                )
            } ?: ApiResult.Failure(ApiError.Network(IOException("unreachable")))
        }
    }

    private val model = CatalogModel<List<String>, String>(
        repository = repository,
        settings = accountSettingsIn(WorkspaceId(7)),
        changes = ServerChanges(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, CountingRefresh()),
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
    )

    @Test
    fun comingBackToTheForegroundLoadsTheListAgain() {
        model.shown()
        assertEquals(1, loads)

        server = listOf("Web site")
        model.returned()

        assertEquals("coming back loads once", 2, loads)
        assertEquals(listOf("Web site"), model.state.value.data)
    }

    @Test
    fun theReturnKeepsTheFormOverTheList() {
        model.shown()
        model.show("the form")

        model.returned()

        assertEquals(2, loads)
        assertEquals("the form stays open", "the form", model.state.value.overlay)
    }

    @Test
    fun showingAgainLoadsAndKeepsTheForm() {
        model.shown()
        model.show("the form")
        server = listOf("Web site")

        model.shown()

        assertEquals(2, loads)
        assertEquals(listOf("Web site"), model.state.value.data)
        assertEquals("the form stays open", "the form", model.state.value.overlay)
    }

    @Test
    fun aClosedListLoadsNothingAndForgetsItsList() {
        model.shown()
        model.close()
        model.retry()
        model.reconnected()

        assertEquals(1, loads)
        assertNull(model.state.value.data)
    }

    /** As when the page opens offline, nothing can change until a load gets through. */
    @Test
    fun backOfflineTheListStaysWithEveryActionOffUntilTheNetworkReturns() {
        model.shown()
        server = null
        model.returned()

        assertEquals(2, loads)
        assertEquals("the list stays", listOf("Website"), model.state.value.data)
        assertEquals(ServerProblem.Unreachable, model.state.value.loadProblem)
        assertEquals(false, model.state.value.canChange)

        server = listOf("Website", "Intranet")
        model.reconnected()
        assertEquals(listOf("Website", "Intranet"), model.state.value.data)
        assertEquals(true, model.state.value.canChange)
    }

    /**
     * A reload that read the list before a change the server then accepted answers after that
     * change: the list keeps the change, and the page reads the list again.
     */
    @Test
    fun aReloadThatReadTheListBeforeAnAcceptedChangeKeepsTheChange() {
        model.shown()
        val stale = CompletableDeferred<ApiResult<List<String>>>()
        held = stale
        model.returned()

        model.send(
            request = { ApiResult.Success("Intranet") },
            apply = { list, created -> list + created },
            done = { CatalogChange.Created(it) },
        )
        assertEquals(listOf("Website", "Intranet"), model.state.value.data)

        server = listOf("Website", "Intranet")
        stale.complete(ApiResult.Success(listOf("Website")))

        assertEquals("the change stays", listOf("Website", "Intranet"), model.state.value.data)
        assertEquals("the page read the list again", 3, loads)
        assertEquals(false, model.state.value.loading)
    }

    /**
     * A reload that started after a create was sent and read the list with the new item answers
     * before the create does: the item shows once, and the page reads the list again.
     */
    @Test
    fun aReloadThatLandsBeforeTheCreatesAnswerShowsTheItemOnce() {
        model.shown()
        val created = CompletableDeferred<ApiResult<String>>()
        model.send(
            request = { created.await() },
            apply = { list, item -> list + item },
            done = { CatalogChange.Created(it) },
        )
        server = listOf("Website", "Intranet")
        model.returned()
        assertEquals(listOf("Website", "Intranet"), model.state.value.data)

        created.complete(ApiResult.Success("Intranet"))

        assertEquals("the item shows once", listOf("Website", "Intranet"), model.state.value.data)
        assertEquals(CatalogChange.Created("Intranet"), model.state.value.done)
        assertEquals(false, model.state.value.sending)
        assertEquals("the page read the list again", 3, loads)
    }
}
