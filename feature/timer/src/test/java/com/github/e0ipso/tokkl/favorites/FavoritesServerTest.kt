package com.github.e0ipso.tokkl.favorites

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.session.CurrentAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A favorites save against a MockWebServer that answers `POST /api/v9/me/preferences` once released. */
class FavoritesServerTest {

    private val server = MockWebServer()
    private val received = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val cached = CopyOnWriteArrayList<List<Favorite>>()
    private val app = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val account = CurrentAccount(app)
    private lateinit var testApi: TestApi

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown()
                release.await(TIMEOUT_S, TimeUnit.SECONDS)
                return MockResponse.Builder().code(200).body("{}").addHeader("Content-Type", "application/json").build()
            }
        }
        server.start()
        testApi = TestApi(ServerUrl.parse(server.url("/").toString())!!)
    }

    @After
    fun tearDown() {
        app.cancel()
        server.close()
    }

    private fun favorites(cacheFavorites: (List<Favorite>) -> Unit = { cached += it }) = FavoritesServer(
        client = { testApi.api() },
        account = account,
        cacheFavorites = cacheFavorites,
        scope = app,
        io = Dispatchers.IO,
    )

    @Test
    fun aSaveTheServerAnswersAfterTheAccountEndedLeavesTheCacheAlone() = runBlocking {
        val saving = app.async { favorites().save(listOf(EntryFields(description = "Write"))) }
        assertTrue("the request reached the server", received.await(TIMEOUT_S, TimeUnit.SECONDS))

        account.begin()
        release.countDown()
        saving.await()

        assertEquals("nothing of the account before reaches the cache", emptyList<List<Favorite>>(), cached)
    }

    @Test
    fun aSaveOfTheCurrentAccountReachesTheCache() = runBlocking {
        release.countDown()

        val result = favorites().save(listOf(EntryFields(description = "Write")))

        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("Write"), cached.single().map { it.description })
    }

    @Test
    fun theNextSignInWaitsForACacheWriteOnItsWay() = runBlocking {
        val writing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val favorites = favorites { rows ->
            writing.countDown()
            finish.await(TIMEOUT_S, TimeUnit.SECONDS)
            cached += rows
        }
        release.countDown()
        val saving = app.async { favorites.save(listOf(EntryFields(description = "Write"))) }
        assertTrue("the cache write started", writing.await(TIMEOUT_S, TimeUnit.SECONDS))

        val signIn = thread { account.begin() }
        while (signIn.isAlive && signIn.state != Thread.State.BLOCKED) Thread.onSpinWait()
        assertTrue("the account does not end during the write", signIn.isAlive)

        finish.countDown()
        signIn.join()
        assertTrue(saving.await() is ApiResult.Success)
        assertEquals(1, cached.size)
    }

    private companion object {
        const val TIMEOUT_S = 10L
    }
}
