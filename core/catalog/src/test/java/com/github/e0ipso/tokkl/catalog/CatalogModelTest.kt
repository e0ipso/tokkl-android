package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.model.WorkspaceId
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

    private val repository = object : CatalogRepository<List<String>> {
        override suspend fun workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = ApiResult.Success(
            checkNotNull(stored),
        )

        override suspend fun load(workspace: WorkspaceId): ApiResult<List<String>> {
            assertEquals(WorkspaceId(7), workspace)
            loads++
            return server?.let {
                ApiResult.Success(
                    it,
                )
            } ?: ApiResult.Failure(ApiError.Network(IOException("unreachable")))
        }
    }

    private val model = CatalogModel<List<String>, String>(
        repository = repository,
        workspaceId = { WorkspaceId(7) },
        refreshCache = {},
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
}
