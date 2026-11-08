package com.github.e0ipso.tokkl.preferences

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReminderSettingTest {

    private val testApi = TestApi(null)
    private val server = MockWebServer()
    private val saved = mutableListOf<StoredReminderPreferences>()
    private lateinit var client: TokklApi

    @Before fun setUp() {
        server.start()
        val url = ServerUrl.parse(server.url("/").toString())!!
        testApi.stored = url
        testApi.trust = InMemoryServerTrust(url)
        client = testApi.api(url, ApiCredentials.ApiToken("test"))
    }

    @After fun cleanUp() {
        server.close()
        testApi.stored = null
        testApi.trust = InMemoryServerTrust()
    }
    private fun setting(initial: StoredReminderPreferences? = null) = ReminderSetting(
        InMemoryValueStore(initial) { saved += it },
        { client },
        CoroutineScope(Dispatchers.Unconfined),
    )
    private fun prefs(body: String = """{"long_timer_reminder_enabled":true,"long_timer_reminder_hours":12}""") =
        MePreferences(Json.parseToJsonElement(body).jsonObject)
    private fun answer(code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body("{}").build())

    @Test fun readsBothExactKeysAndPostsOnlyThem() = runTest {
        val setting = setting()
        setting.refreshed(prefs(), true)
        assertEquals(ReminderPreferences(true, 12), setting.state.value)
        answer()
        setting.change(ReminderPreferences(false, 4))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v9/me/preferences", request.target)
        assertEquals("""{"long_timer_reminder_enabled":false,"long_timer_reminder_hours":4}""", request.body!!.utf8())
        assertFalse(saved.last().unsent)
        setting.refreshed(prefs(), true)
        assertEquals("stale refresh cannot revert a change", ReminderPreferences(false, 4), setting.state.value)
        setting.refreshed(prefs(), true)
        assertEquals(ReminderPreferences(true, 12), setting.state.value)
    }

    @Test fun failedWriteSurvivesRestartAndRetriesOnRefresh() = runTest {
        val setting = setting()
        setting.refreshed(prefs(), true)
        answer(503)
        setting.change(ReminderPreferences(false, 2))
        assertTrue(saved.last().unsent)
        val restarted = setting(saved.last())
        answer()
        restarted.refreshed(prefs(), true)
        assertFalse(saved.last().unsent)
        assertEquals(ReminderPreferences(false, 2), restarted.state.value)
        assertEquals(2, server.requestCount)
    }

    @Test fun oldTokklAndTogglKeepLocalDefaultsAndChanges() = runTest {
        for (tokkl in listOf(true, false)) {
            val setting = setting()
            setting.refreshed(prefs("{}"), tokkl)
            assertEquals(ReminderPreferences(), setting.state.value)
            setting.change(ReminderPreferences(false, 10))
            setting.refreshed(prefs("{}"), tokkl)
            assertEquals(ReminderPreferences(false, 10), setting.state.value)
            assertFalse(saved.last().unsent)
            assertFalse(saved.last().shared)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun rangesRejectMalformedServerValuesAndClampLocalValues() = runTest {
        for (hours in listOf("0", "169", "1.5", "null", "true", "\"9\"", "{}")) {
            assertEquals(
                ReminderPreferences(),
                prefs(
                    """{"long_timer_reminder_enabled":"false","long_timer_reminder_hours":$hours}""",
                ).reminderPreferences(),
            )
        }
        val setting = setting()
        setting.change(ReminderPreferences(hours = 0))
        assertEquals(1, setting.state.value.hours)
        setting.change(ReminderPreferences(hours = 169))
        assertEquals(168, setting.state.value.hours)
        assertEquals(0, server.requestCount)
    }

    @Test fun discoveringTheKeysSendsALocalChangeMadeDuringTheFirstRefresh() = runTest {
        val setting = setting()
        setting.change(ReminderPreferences(false, 6))
        answer()
        setting.refreshed(prefs(), true)
        assertEquals(ReminderPreferences(false, 6), setting.state.value)
        assertEquals(1, server.requestCount)
        assertFalse(saved.last().unsent)
    }

    @Test fun anInFlightPreferenceSendCannotPersistAfterSignOut() = runTest {
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertTrue(release.await(10, TimeUnit.SECONDS))
                return MockResponse.Builder().code(200).body("{}").build()
            }
        }
        val setting = ReminderSetting(
            InMemoryValueStore(null) { saved += it },
            { client },
            CoroutineScope(Dispatchers.IO),
        )
        runBlocking { setting.refreshed(prefs(), true).join() }
        val job = setting.change(ReminderPreferences(false, 6))
        try {
            assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            setting.forget()
            val writes = saved.size
            release.countDown()
            runBlocking { job.join() }
            assertEquals("no old send recreates the cleared file", writes, saved.size)
            assertEquals(ReminderPreferences(), setting.state.value)
        } finally {
            release.countDown()
        }
    }

    @Test fun failedReadKeepsValuesAndSignOutClearsThem() = runTest {
        val setting = setting(StoredReminderPreferences(ReminderPreferences(false, 24), false, true))
        setting.refreshed(null, true)
        assertEquals(ReminderPreferences(false, 24), setting.state.value)
        setting.forget()
        assertEquals(ReminderPreferences(), setting.state.value)
    }
}
