package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The background and foreground pair of a catalog page's model, with a fetch that answers from
 * [server] and counts the loads. The page's activity calls [CatalogModel.wentToBackground] when it
 * stops for a trip to the background, never for a configuration change, and
 * [CatalogModel.cameToForeground] on every start. Unconfined runs each load to the end inside the
 * call.
 */
class CatalogModelTest {

    /** What the server lists, or null when it is out of reach. */
    private var server: List<String>? = listOf("Website")
    private var loads = 0

    private val url = ServerUrl.parse("http://127.0.0.1:9/")!!

    private val model = CatalogModel<List<String>, String>(
        client = { TogglApiClient(url, ApiCredentials.ApiToken("tok-123")) },
        workspaceId = { 7L },
        refreshCache = {},
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
        fetch = { _, workspace ->
            assertEquals(7L, workspace)
            loads++
            server?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.Network(IOException("unreachable")))
        },
    )

    @Test
    fun aTripToTheBackgroundLoadsTheListAgainOnTheWayBack() {
        model.open()
        assertEquals(1, loads)

        model.wentToBackground()
        server = listOf("Web site")
        assertEquals("nothing loads while the app is away", 1, loads)
        model.cameToForeground()

        assertEquals("coming back loads once", 2, loads)
        assertEquals(listOf("Web site"), model.state.value.data)
    }

    /** A rotation stops and starts the activity too, but the page never hears of the stop. */
    @Test
    fun aStartWithoutATripLoadsNothing() {
        model.open()
        model.cameToForeground()
        model.open()

        assertEquals(1, loads)
    }

    @Test
    fun theTripLoadsOnceAndKeepsTheFormOverTheList() {
        model.open()
        model.show("the form")

        model.wentToBackground()
        model.cameToForeground()
        model.cameToForeground()

        assertEquals(2, loads)
        assertEquals("the form stays open", "the form", model.state.value.overlay)
    }

    @Test
    fun aClosedPageLoadsNothingOnTheWayBack() {
        model.open()
        model.wentToBackground()
        model.close()
        model.cameToForeground()

        assertEquals(1, loads)
        assertNull(model.state.value.data)
    }

    /** As when the page opens offline, nothing can change until a load gets through. */
    @Test
    fun backOfflineTheListStaysWithEveryActionOffUntilTheNetworkReturns() {
        model.open()
        model.wentToBackground()
        server = null
        model.cameToForeground()

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
