package com.github.e0ipso.tokkl.pomodoro

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
 * The Pomodoro defaults against a MockWebServer that answers `POST /api/v9/me/preferences`. The
 * refreshes hand the setting their preferences directly, as AccountSettingsSource's onRefresh
 * does. Unconfined runs every write to the end inside the call.
 */
class PomodoroDefaultsSettingTest {

    private val server = MockWebServer()
    private val saved = mutableListOf<StoredPomodoroDefaults>()
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
    fun aColdStartHasTheStoredValuesOrTokklsDefaultsBeforeAnyRefresh() {
        assertEquals(PomodoroDefaults(25, 5, 15, 4), setting(initial = null).defaults.value)
        val stored = PomodoroDefaults(50, 10, 30, 3)
        assertEquals(stored, setting(initial = StoredPomodoroDefaults(stored, unsent = false)).defaults.value)
        assertEquals(
            PomodoroDefaults(1, 60, 1, 12),
            setting(initial = StoredPomodoroDefaults(PomodoroDefaults(0, 61, -1, 13), unsent = false)).defaults.value,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun eachRefreshTakesTheServersValues() {
        val setting = setting()

        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = true)

        assertEquals(PomodoroDefaults(50, 10, 30, 3), setting.defaults.value)
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(50, 10, 30, 3), unsent = false), saved.last())
        assertEquals("reading sends nothing", 0, server.requestCount)
    }

    /**
     * Tokkl hands out its default for a stored value it would refuse, so a key that is no integer
     * in its range reads as its default, and the other keys keep their own values.
     */
    @Test
    fun aMalformedKeyFallsBackToItsOwnDefaultAlone() {
        val setting = setting()
        val cases = listOf(
            """{"pomodoro_focus_interval_in_minutes":"50","pomodoro_break_interval_in_minutes":10,"pomodoro_long_break_interval_in_minutes":30,"pomodoro_long_break_every":3}"""
                to PomodoroDefaults(25, 10, 30, 3),
            """{"pomodoro_focus_interval_in_minutes":50,"pomodoro_break_interval_in_minutes":61,"pomodoro_long_break_interval_in_minutes":30,"pomodoro_long_break_every":3}"""
                to PomodoroDefaults(50, 5, 30, 3),
            """{"pomodoro_focus_interval_in_minutes":50,"pomodoro_break_interval_in_minutes":10,"pomodoro_long_break_interval_in_minutes":2.5,"pomodoro_long_break_every":3}"""
                to PomodoroDefaults(50, 10, 15, 3),
            """{"pomodoro_focus_interval_in_minutes":50,"pomodoro_break_interval_in_minutes":10,"pomodoro_long_break_interval_in_minutes":30,"pomodoro_long_break_every":true}"""
                to PomodoroDefaults(50, 10, 30, 4),
            """{"pomodoro_focus_interval_in_minutes":0,"pomodoro_break_interval_in_minutes":null,"pomodoro_long_break_interval_in_minutes":[30]}"""
                to PomodoroDefaults(25, 5, 15, 4),
            """{"pomodoro_focus_interval_in_minutes":180.0,"pomodoro_break_interval_in_minutes":1,"pomodoro_long_break_interval_in_minutes":60,"pomodoro_long_break_every":12}"""
                to PomodoroDefaults(180, 1, 60, 12),
        )
        for ((body, expected) in cases) {
            setting.refreshed(preferences(body), keepsPreferences = true)

            assertEquals(body, expected, setting.defaults.value)
        }
        assertEquals("reading sends nothing", 0, server.requestCount)
    }

    /**
     * A change shows at once and posts only the four keys. The next refresh may have read the
     * preferences before the change, so it keeps the change; the one after it takes the server's.
     */
    @Test
    fun aChangePostsOnlyTheFourKeysAndTheRefreshRightAfterItKeepsTheChange() {
        server.enqueue(ok())
        val setting = setting()

        setting.change(PomodoroDefaults(40, 8, 20, 5))

        assertEquals(PomodoroDefaults(40, 8, 20, 5), setting.defaults.value)
        val request = server.takeRequest()
        assertEquals("POST /api/v9/me/preferences", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            """{"pomodoro_focus_interval_in_minutes":40,"pomodoro_break_interval_in_minutes":8,"pomodoro_long_break_interval_in_minutes":20,"pomodoro_long_break_every":5}""",
            request.body?.utf8(),
        )
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = false), saved.last())

        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = true)
        assertEquals("the refresh may predate the change", PomodoroDefaults(40, 8, 20, 5), setting.defaults.value)

        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = true)
        assertEquals("the web app changed them since", PomodoroDefaults(50, 10, 30, 3), setting.defaults.value)
    }

    /** Tokkl answers 400 to a value outside its range, so the phone clamps each one before it posts. */
    @Test
    fun outOfRangeValuesAreClampedBeforeSending() {
        val cases = listOf(
            PomodoroDefaults(0, 0, 0, 0) to PomodoroDefaults(1, 1, 1, 1),
            PomodoroDefaults(-5, -1, Int.MIN_VALUE, -12) to PomodoroDefaults(1, 1, 1, 1),
            PomodoroDefaults(181, 61, 61, 13) to PomodoroDefaults(180, 60, 60, 12),
            PomodoroDefaults(Int.MAX_VALUE, 1000, 999, 100) to PomodoroDefaults(180, 60, 60, 12),
            PomodoroDefaults(180, 60, 60, 12) to PomodoroDefaults(180, 60, 60, 12),
            PomodoroDefaults(1, 1, 1, 1) to PomodoroDefaults(1, 1, 1, 1),
        )
        val setting = setting()
        for ((asked, sent) in cases) {
            server.enqueue(ok())

            setting.change(asked)

            assertEquals("$asked shows clamped", sent, setting.defaults.value)
            val body = server.takeRequest().body?.utf8()
            assertEquals(
                "$asked is sent clamped",
                """{"pomodoro_focus_interval_in_minutes":${sent.workMinutes},"pomodoro_break_interval_in_minutes":${sent.shortRestMinutes},""" +
                    """"pomodoro_long_break_interval_in_minutes":${sent.longRestMinutes},"pomodoro_long_break_every":${sent.longRestEvery}}""",
                body,
            )
            assertEquals("$asked is stored clamped", StoredPomodoroDefaults(sent, unsent = false), saved.last())
        }
    }

    /** Offline, a change stays on the phone, marked unsent, and the next refresh sends it. */
    @Test
    fun anOfflineChangeStaysUnsentAndTheNextRefreshSendsItInsteadOfReadingTheServers() {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        val setting = setting()

        setting.change(PomodoroDefaults(40, 8, 20, 5))

        assertEquals(PomodoroDefaults(40, 8, 20, 5), setting.defaults.value)
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true), saved.last())
        val failed = server.requestCount

        server.enqueue(ok())
        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = true)

        assertEquals(PomodoroDefaults(40, 8, 20, 5), setting.defaults.value)
        assertEquals("the refresh posted the change", failed + 1, server.requestCount)
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = false), saved.last())
    }

    @Test
    fun storedUnsentValuesGoWithTheFirstRefreshAfterAColdStart() {
        server.enqueue(ok())
        val setting = setting(initial = StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true))

        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = true)

        assertEquals(PomodoroDefaults(40, 8, 20, 5), setting.defaults.value)
        assertEquals(
            """{"pomodoro_focus_interval_in_minutes":40,"pomodoro_break_interval_in_minutes":8,"pomodoro_long_break_interval_in_minutes":20,"pomodoro_long_break_every":5}""",
            server.takeRequest().body?.utf8(),
        )
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = false), saved.last())
    }

    /** A server without Tokkl's preferences, such as Toggl Track, gets nothing, and the phone starts from 25, 5, 15 and 4. */
    @Test
    fun withoutTokklsPreferencesTheValuesStayOnThePhoneFromTokklsDefaults() {
        val setting = setting(keepsPreferences = false)

        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = false)
        assertEquals("the refresh's values do not count", PomodoroDefaults(25, 5, 15, 4), setting.defaults.value)
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(25, 5, 15, 4), unsent = false), saved.last())

        setting.change(PomodoroDefaults(40, 8, 0, 20))
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 1, 12), unsent = false), saved.last())
        setting.refreshed(preferences("""{}"""), keepsPreferences = false)

        assertEquals("clamped on the phone too", PomodoroDefaults(40, 8, 1, 12), setting.defaults.value)
        assertEquals(StoredPomodoroDefaults(PomodoroDefaults(40, 8, 1, 12), unsent = false), saved.last())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aFailedPreferencesReadKeepsTheValues() {
        val stored = PomodoroDefaults(50, 10, 30, 3)
        val setting = setting(initial = StoredPomodoroDefaults(stored, unsent = false))

        setting.refreshed(null, keepsPreferences = true)

        assertEquals(stored, setting.defaults.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun forgetGoesBackToTokklsDefaultsWithNothingUnsent() {
        val setting = setting(initial = StoredPomodoroDefaults(PomodoroDefaults(40, 8, 20, 5), unsent = true))

        setting.forget()
        setting.refreshed(preferences(SERVER_VALUES), keepsPreferences = true)

        assertEquals("nothing was left to send", 0, server.requestCount)
        assertEquals(PomodoroDefaults(50, 10, 30, 3), setting.defaults.value)
    }

    private fun setting(initial: StoredPomodoroDefaults? = null, keepsPreferences: Boolean = true) = PomodoroDefaultsSetting(
        initial = initial,
        save = { saved += it },
        client = { client },
        keepsPreferences = { keepsPreferences },
        scope = CoroutineScope(Dispatchers.Unconfined),
        io = Dispatchers.Unconfined,
    )

    private fun preferences(body: String) = MePreferences(Json.parseToJsonElement(body).jsonObject)

    private fun ok() = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{}""")
        .build()

    private companion object {
        const val SERVER_VALUES =
            """{"pomodoro_enabled":true,"pomodoro_focus_interval_in_minutes":50,"pomodoro_break_interval_in_minutes":10,""" +
                """"pomodoro_long_break_interval_in_minutes":30,"pomodoro_long_break_every":3,"pomodoro_stop_timer_at_end":false}"""
    }
}
