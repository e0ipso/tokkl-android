package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.readServerSnapshot
import com.github.e0ipso.tokkl.session.Me
import com.github.e0ipso.tokkl.session.MePreferences
import java.io.IOException
import java.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The capability flags from `/me` and the trash probe, against a MockWebServer. */
class CapabilitiesTest {

    private val server = MockWebServer()
    private lateinit var client: TogglApiClient
    private val answers = mutableMapOf<String, MockResponse>()
    private val saved = mutableListOf<Capabilities>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers[request.url.encodedPath] ?: MockResponse.Builder().code(404).body("\"Not found\"").build()
        }
        server.start()
        val configured = ServerUrl.parse(server.url("/").toString())!!
        ConfiguredServer.readStoredFrom { configured }
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
    fun timeResolutionIsFalseWhenMeLacksTimeResolutionMinutes() {
        answers[TRASH] = json("[]")

        val capabilities = source().refresh(client, me(TOGGL_ME))

        assertFalse(capabilities.timeResolution)
    }

    @Test
    fun favoritesAreFalseWhenMeLacksRole() {
        answers[TRASH] = json("[]")

        val capabilities = source().refresh(client, me("""{"id":1,"timezone":"UTC","beginning_of_week":1,"time_resolution_minutes":5}"""))

        assertFalse(capabilities.favorites)
        assertTrue(capabilities.timeResolution)
    }

    /** Tokkl leaves `favorites` out of /me/preferences until the user saves one. */
    @Test
    fun favoritesAreTrueOnAFreshTokklAccountWithNoSavedFavorites() {
        answers[TRASH] = json("[]")
        answers["/api/v9/me/time_entries"] = json("[]")
        val preferences = MePreferences(Json.parseToJsonElement("""{"date_format":"YYYY-MM-DD","timeofday_format":"H:mm"}""").jsonObject)
        val me = me(TOKKL_ME)

        val capabilities = source().refresh(client, me)
        val snapshot = readServerSnapshot(client, me, preferences, Clock.systemUTC(), favorites = capabilities.favorites)

        assertTrue(capabilities.favorites)
        assertEquals("no favorites yet", emptyList<CachedFavorite>(), snapshot.favorites)
    }

    @Test
    fun allFlagsAreTrueOnTokkl() {
        answers[TRASH] = json("[]")

        val capabilities = source().refresh(client, me(TOKKL_ME))

        assertEquals(ALL, capabilities)
        assertEquals("the flags are stored", listOf(ALL), saved)
    }

    @Test
    fun trashAndRestoreAreFalseWhenTheTrashAnswers404() {
        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertFalse(capabilities.trash)
        assertFalse(capabilities.restore)
        assertEquals(listOf(ALL.copy(trash = false, restore = false)), saved)
    }

    /** Such as a proxy's HTML page in front of a server without the trash. */
    @Test
    fun aTrashAnswerThatIsNotAListHidesTrashAndRestore() {
        answers[TRASH] = MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html></html>").build()

        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertFalse(capabilities.trash)
        assertFalse(capabilities.restore)
    }

    @Test
    fun aProbeWithoutAClearAnswerKeepsTheTrashFlags() {
        answers[TRASH] = MockResponse.Builder().code(503).build()

        val capabilities = source(initial = ALL).refresh(client, me(TOKKL_ME))

        assertEquals(ALL, capabilities)
        assertEquals("nothing changed, so nothing was written", emptyList<Capabilities>(), saved)
    }

    /** The probe never makes up an entry id to try the restore endpoint. */
    @Test
    fun theOnlyProbeIsOneGetOfTheTrash() {
        answers[TRASH] = json("[]")

        source().refresh(client, me(TOKKL_ME))

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(TRASH, request.url.encodedPath)
    }

    @Test
    fun aFailedWriteKeepsTheNewFlagsAndTheNextRefreshWritesAgain() {
        answers[TRASH] = json("[]")
        var failWrites = true
        val source = CapabilitiesSource(initial = null) {
            if (failWrites) throw IOException("disk full")
            saved += it
        }

        assertEquals(ALL, source.refresh(client, me(TOKKL_ME)))
        assertEquals(ALL, source.state.value)
        failWrites = false
        source.refresh(client, me(TOKKL_ME))

        assertEquals(listOf(ALL), saved)
    }

    private fun source(initial: Capabilities? = null) = CapabilitiesSource(initial) { saved += it }

    private fun me(body: String): Me = Json { ignoreUnknownKeys = true }.decodeFromString(Me.serializer(), body)

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val TRASH = "/api/app/trash"
        const val TOKKL_ME = """{"id":1,"timezone":"UTC","beginning_of_week":1,"role":"user","time_resolution_minutes":5}"""
        const val TOGGL_ME = """{"id":1,"timezone":"UTC","beginning_of_week":1}"""
        val ALL = Capabilities(trash = true, restore = true, favorites = true, timeResolution = true)
    }
}
