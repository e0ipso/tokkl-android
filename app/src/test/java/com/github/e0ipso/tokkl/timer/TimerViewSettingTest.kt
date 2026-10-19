package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.MePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The Timer page's List or Calendar choice against a MockWebServer that answers
 * `POST /api/v9/me/preferences`. The refreshes hand the setting their preferences directly, as
 * AccountSettingsSource's onRefresh does. Unconfined runs every write to the end inside the call.
 */
class TimerViewSettingTest {

    private val server = MockWebServer()
    private val saved = mutableListOf<StoredTimerView>()
    private lateinit var client: TogglApiClient

    @Before
    fun setUp() {
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        // The shared client sends requests only to the configured server.
        ConfiguredServer.readStoredFrom { configured }
        // The user has confirmed the MockWebServer's plain http URL.
        ConfiguredTrust.readFrom(InMemoryServerTrust(configured))
        client = TogglApiClient(configured, ApiCredentials.ApiToken("tok-123"))
    }

    @After
    fun tearDown() {
        ConfiguredServer.readStoredFrom { null }
        ConfiguredTrust.readFrom(InMemoryServerTrust())
        server.close()
    }

    @Test
    fun aColdStartOpensTheStoredViewBeforeAnyRefresh() {
        assertEquals(TimerView.LIST, setting(initial = null).view.value)
        assertEquals(TimerView.CALENDAR, setting(initial = StoredTimerView(TimerView.CALENDAR, unsent = false)).view.value)
        assertEquals(0, server.requestCount)
    }

    /** As TimerPage.tsx reads timerView: calendar opens Calendar, and anything else List. */
    @Test
    fun eachRefreshOpensTheServersViewAndListWhenItIsMissingOrUnknown() {
        val setting = setting()
        val cases = listOf(
            """{"timerView":"calendar"}""" to TimerView.CALENDAR,
            """{"timerView":"grid"}""" to TimerView.LIST,
            """{"timerView":"calendar"}""" to TimerView.CALENDAR,
            """{}""" to TimerView.LIST,
            """{"timerView":"calendar"}""" to TimerView.CALENDAR,
            """{"timerView":3}""" to TimerView.LIST,
            """{"timerView":"calendar"}""" to TimerView.CALENDAR,
            """{"timerView":null}""" to TimerView.LIST,
        )
        for ((preferences, view) in cases) {
            setting.refreshed(preferences(preferences), keepsTimerView = true)

            assertEquals(preferences, view, setting.view.value)
            assertEquals(preferences, StoredTimerView(view, unsent = false), saved.last())
        }
        assertEquals("reading sends nothing", 0, server.requestCount)
    }

    /**
     * A tap shows the view at once and posts it. The next refresh may have read the preferences
     * before the tap, so it keeps the tap; the one after it opens what the server has again.
     */
    @Test
    fun aTapPostsTheViewAndTheRefreshRightAfterItKeepsTheTap() {
        server.enqueue(ok())
        val setting = setting()

        setting.choose(TimerView.CALENDAR)

        assertEquals(TimerView.CALENDAR, setting.view.value)
        val request = server.takeRequest()
        assertEquals("POST /api/v9/me/preferences", "${request.method} ${request.url.encodedPath}")
        assertEquals("""{"timerView":"calendar"}""", request.body?.utf8())
        assertEquals(StoredTimerView(TimerView.CALENDAR, unsent = false), saved.last())

        setting.refreshed(preferences("""{"timerView":"list"}"""), keepsTimerView = true)
        assertEquals("the refresh may predate the tap", TimerView.CALENDAR, setting.view.value)

        setting.refreshed(preferences("""{"timerView":"list"}"""), keepsTimerView = true)
        assertEquals("the web app chose List since", TimerView.LIST, setting.view.value)
    }

    /** Offline, the choice stays on the phone, marked unsent, and the next refresh sends it. */
    @Test
    fun anOfflineTapStaysUnsentAndTheNextRefreshSendsItInsteadOfReadingTheServers() {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        val setting = setting()

        setting.choose(TimerView.CALENDAR)

        assertEquals(TimerView.CALENDAR, setting.view.value)
        assertEquals(StoredTimerView(TimerView.CALENDAR, unsent = true), saved.last())
        val failed = server.requestCount

        server.enqueue(ok())
        setting.refreshed(preferences("""{"timerView":"list"}"""), keepsTimerView = true)

        assertEquals(TimerView.CALENDAR, setting.view.value)
        assertEquals("the refresh posted the choice", failed + 1, server.requestCount)
        assertEquals(StoredTimerView(TimerView.CALENDAR, unsent = false), saved.last())
    }

    @Test
    fun aStoredUnsentChoiceGoesWithTheFirstRefreshAfterAColdStart() {
        server.enqueue(ok())
        val setting = setting(initial = StoredTimerView(TimerView.CALENDAR, unsent = true))

        setting.refreshed(preferences("""{}"""), keepsTimerView = true)

        assertEquals(TimerView.CALENDAR, setting.view.value)
        assertEquals("""{"timerView":"calendar"}""", server.takeRequest().body?.utf8())
        assertEquals(StoredTimerView(TimerView.CALENDAR, unsent = false), saved.last())
    }

    /** A server without Tokkl's preferences, such as Toggl Track, gets nothing. */
    @Test
    fun withoutTokklsPreferencesTheChoiceStaysOnThePhone() {
        val setting = setting(keepsTimerView = false)

        setting.choose(TimerView.CALENDAR)
        setting.refreshed(preferences("""{}"""), keepsTimerView = false)

        assertEquals(TimerView.CALENDAR, setting.view.value)
        assertEquals(StoredTimerView(TimerView.CALENDAR, unsent = false), saved.last())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aFailedPreferencesReadKeepsTheView() {
        val setting = setting(initial = StoredTimerView(TimerView.CALENDAR, unsent = false))

        setting.refreshed(null, keepsTimerView = true)

        assertEquals(TimerView.CALENDAR, setting.view.value)
        assertEquals(0, server.requestCount)
    }

    private fun setting(initial: StoredTimerView? = null, keepsTimerView: Boolean = true) = TimerViewSetting(
        initial = initial,
        save = { saved += it },
        client = { client },
        keepsTimerView = { keepsTimerView },
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
    )

    private fun preferences(body: String) = MePreferences(Json.parseToJsonElement(body).jsonObject)

    private fun ok() = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{"timerView":"calendar"}""")
        .build()
}
