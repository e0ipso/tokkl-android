package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A change a page sends at once, in an application scope on an unconfined test dispatcher, with
 * the server's answer held until the test gives it.
 */
class ServerChangesTest {

    private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    private val application = CoroutineScope(Job() + dispatcher)
    private val refresh = CountingRefresh()
    private val changes = ServerChanges(application, dispatcher, refresh)
    private val answer = CompletableDeferred<ApiResult<String>>()

    /** The page goes while the server answers: the accepted change still refreshes the cache. */
    @Test
    fun anAcceptedChangeRefreshesTheCacheAfterThePageWent() {
        val page = CoroutineScope(Job() + dispatcher)
        val sent = changes.send { answer.await() }
        var shown: ApiResult<String>? = null
        page.launch { shown = sent.await() }

        page.cancel()
        answer.complete(ApiResult.Success("Website"))

        assertEquals(1, refresh.afterChanges)
        assertEquals(ApiResult.Success("Website"), runBlocking { sent.await() })
        assertEquals("the page that went shows nothing", null, shown)
    }

    @Test
    fun aRefusedChangeOrOneThatAsksNoneRefreshesNothing() {
        val refused = changes.send { ApiResult.Failure(ApiError.UnexpectedStatus(400, "Name has already been taken")) }
        val quiet = changes.send(refresh = false) { ApiResult.Success(1) }

        assertTrue(refused.isCompleted && quiet.isCompleted)
        assertEquals(0, refresh.afterChanges)
    }
}
