package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ConfiguredTrust
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import com.github.e0ipso.tokkl.time.DurationFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** What a refresh reads from a MockWebServer that answers like Tokkl. */
class ServerReaderTest {

    private val server = MockWebServer()
    private lateinit var client: TogglApiClient
    private val answers = mutableMapOf<String, MockResponse>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers[request.url.encodedPath] ?: MockResponse.Builder().code(404).build()
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

    /** 10:30 UTC on 18 October is 23:30 in Auckland, so the window starts at its midnight of 5 October. */
    @Test
    fun entriesAreAskedForTheWindowInTheAccountZoneAndParsed() {
        answers["/api/v9/me/time_entries"] = json(
            """[{"id":1,"workspace_id":7,"project_id":3,"task_id":null,"description":"Stopped","start":"2026-10-17T09:00:00+00:00",
               "stop":"2026-10-17T10:00:00+00:00","duration":3600,"billable":true,"tags":["a"],"tag_ids":[5]},
              {"id":2,"workspace_id":7,"description":null,"start":"2026-10-18T10:00:00Z","stop":null,"duration":-1,"tags":null}]""",
        )
        val preferences = preferences("""{"date_format":"YYYY-MM-DD"}""")

        val snapshot = readServerSnapshot(client, me("Pacific/Auckland"), preferences, Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)

        val request = recorded("/api/v9/me/time_entries")
        assertEquals("2026-10-04T11:00:00Z", request.url.queryParameter("start_date"))
        assertEquals("2026-10-18T11:00:00Z", request.url.queryParameter("end_date"))
        assertEquals(
            listOf(
                CachedTimeEntry(
                    1, 7, 3, null, "Stopped", Instant.parse("2026-10-17T09:00:00Z"), Instant.parse("2026-10-17T10:00:00Z"),
                    billable = true, tags = listOf("a"), tagIds = listOf(5L),
                ),
                CachedTimeEntry(
                    2, 7, null, null, "", Instant.parse("2026-10-18T10:00:00Z"), stop = null,
                    billable = false, tags = emptyList(), tagIds = emptyList(),
                ),
            ),
            snapshot.timeEntries,
        )
    }

    /**
     * Tokkl 1.11.0 and later send `all_day` on every entry; Toggl Track and older Tokkl leave it
     * out. The wire model keeps a missing key apart from false, and the cache stores both as timed.
     */
    @Test
    fun noTimeEntriesAreCachedAsNoTimeAndAMissingFlagStaysApartFromFalse() {
        val body = """[{"id":1,"workspace_id":7,"description":"Workshop","start":"2026-10-17T00:00:00+00:00",
                         "stop":"2026-10-17T01:30:00+00:00","duration":5400,"all_day":true,"duronly":true},
                        {"id":2,"workspace_id":7,"description":"Timed","start":"2026-10-17T09:00:00+00:00",
                         "stop":"2026-10-17T10:00:00+00:00","duration":3600,"all_day":false,"duronly":true},
                        {"id":3,"workspace_id":7,"description":"Toggl","start":"2026-10-17T11:00:00Z",
                         "stop":"2026-10-17T12:00:00Z","duration":3600}]"""
        answers["/api/v9/me/time_entries"] = json(body)

        val snapshot = readServerSnapshot(client, me("UTC"), preferences("{}"), Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)

        val wire = ApiJson.decodeFromString(ListSerializer(ServerTimeEntry.serializer()), body)
        assertEquals(listOf(true, false, null), wire.map { it.allDay })
        assertEquals(mapOf(1L to true, 2L to false, 3L to false), snapshot.timeEntries?.associate { it.id to it.allDay })
        assertEquals("a server row leaves the day to its start", listOf(null, null, null), snapshot.timeEntries?.map { it.day })
    }

    /**
     * Tokkl before 1.11.0 lists entries without `all_day`, so no-time entries go off for good.
     * Toggl Track leaves the key out too, but its flag is off already, and a Tokkl that sends the
     * key keeps it on.
     */
    @Test
    fun aTokklEntryListWithoutAllDayTurnsNoTimeEntriesOff() {
        val old = """[{"id":1,"workspace_id":7,"description":"Old","start":"2026-10-17T09:00:00+00:00","stop":"2026-10-17T10:00:00+00:00"}]"""
        val current = """[{"id":1,"workspace_id":7,"description":"New","start":"2026-10-17T09:00:00+00:00","stop":"2026-10-17T10:00:00+00:00","all_day":false}]"""
        fun refresh(body: String, me: Me): Pair<ServerSnapshot, Int> {
            answers["/api/v9/me/time_entries"] = json(body)
            var missing = 0
            val snapshot = readServerSnapshot(
                client, me, preferences("{}"), Clock.fixed(NOW, ZoneOffset.UTC), favorites = true, onAllDayMissing = { missing++ },
            )
            return snapshot to missing
        }

        assertEquals("Toggl Track", 0, refresh(old, me("UTC")).second)
        assertEquals("a Tokkl that sends the key", 0, refresh(current, me("UTC", role = "user")).second)
        val (snapshot, missing) = refresh(old, me("UTC", role = "user"))
        assertEquals("an older Tokkl", 1, missing)
        assertEquals("the entries are cached all the same", listOf("Old"), snapshot.timeEntries?.map { it.description })

        val capabilities = CapabilitiesSource(InMemoryValueStore(Capabilities.NONE.copy(favorites = true, allDay = true)))
        answers["/api/v9/me/time_entries"] = json(old)
        readServerSnapshot(
            client, me("UTC", role = "user"), preferences("{}"), Clock.fixed(NOW, ZoneOffset.UTC), favorites = true,
            onAllDayMissing = capabilities::allDayMissing,
        )
        assertEquals(false, capabilities.state.value.allDay)
    }

    /** Tokkl leaves `favorites` out of /me/preferences until the user saves one. */
    @Test
    fun preferencesWithoutFavoritesMeanNoFavorites() {
        answers["/api/v9/me/time_entries"] = json("[]")
        val preferences = preferences("""{"date_format":"YYYY-MM-DD"}""")

        val snapshot = readServerSnapshot(client, me("UTC"), preferences, Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)

        assertEquals(emptyList<CachedFavorite>(), snapshot.favorites)
    }

    /** The favorites decode on their own, so a broken key leaves the duration format readable. */
    @Test
    fun favoritesThatDoNotDecodeAreUnreadAlone() {
        answers["/api/v9/me/time_entries"] = json("[]")
        val preferences = preferences("""{"duration_format":"classic","favorites":"broken"}""")

        val snapshot = readServerSnapshot(client, me("UTC"), preferences, Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)

        assertNull(snapshot.favorites)
        assertEquals(DurationFormat.CLASSIC, preferences.durationFormat)
    }

    /** Toggl Track has no favorites, so the snapshot keeps none, whatever the preferences hold. */
    @Test
    fun withoutFavoritesNoneAreKept() {
        answers["/api/v9/me/time_entries"] = json("[]")
        val preferences = preferences("""{"favorites":[{"description":"Standup"}]}""")

        val snapshot = readServerSnapshot(client, me("UTC"), preferences, Clock.fixed(NOW, ZoneOffset.UTC), favorites = false)

        assertEquals(emptyList<CachedFavorite>(), snapshot.favorites)
        assertEquals(listOf("/api/v9/me/time_entries"), List(server.requestCount) { server.takeRequest().url.encodedPath })
    }

    @Test
    fun failedRequestsLeaveTheirListsUnread() {
        val snapshot = readServerSnapshot(client, me("UTC"), preferences = null, Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)

        assertNull(snapshot.timeEntries)
        assertNull(snapshot.favorites)
    }

    @Test
    fun anUnknownZoneReadsNoEntries() {
        val preferences = preferences("{}")

        val snapshot = readServerSnapshot(client, me("Mars/Olympus_Mons"), preferences, Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)

        assertNull(snapshot.history)
        assertNull(snapshot.timeEntries)
        assertEquals("nothing was asked for", 0, server.requestCount)
    }

    /**
     * Wired as the app's Koin graph wires a refresh, minus the Room write and the trash probe. The
     * settings take the duration format and the cache the favorites, from one request.
     */
    @Test
    fun aTokklRefreshAsksForThePreferencesOnce() {
        answers["/api/v9/me"] = json("""{"id":1,"timezone":"UTC","beginning_of_week":1,"role":"user"}""")
        answers["/api/v9/me/time_entries"] = json("[]")
        answers["/api/v9/me/preferences"] = json("""{"duration_format":"decimal","favorites":[{"description":"Standup","billable":true}]}""")
        var snapshot: ServerSnapshot? = null
        val settings = AccountSettingsSource(InMemoryValueStore(null))
        val refresh = AccountRefresh(
            client = { client },
            settings = settings,
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            steps = { api, me, preferences ->
                snapshot = readServerSnapshot(api, me, preferences, Clock.fixed(NOW, ZoneOffset.UTC), favorites = true)
            },
        )

        refresh.refresh()

        val paths = List(server.requestCount) { server.takeRequest().url.encodedPath }
        assertEquals(paths.toString(), 1, paths.count { it == "/api/v9/me/preferences" })
        assertEquals(DurationFormat.DECIMAL, settings.state.value?.durationFormat)
        val standup = CachedFavorite(0, "Standup", projectId = null, taskId = null, tags = emptyList(), billable = true)
        assertEquals(listOf(standup), snapshot?.favorites)
    }

    private fun recorded(path: String): RecordedRequest {
        repeat(server.requestCount) {
            val request = server.takeRequest()
            if (request.url.encodedPath == path) return request
        }
        throw AssertionError("no request to $path")
    }

    /** Toggl Track's `/me`, or Tokkl's with a [role]. */
    private fun me(zone: String, role: String? = null): Me {
        val tokkl = if (role == null) "" else ",\"role\":\"$role\""
        return Json { ignoreUnknownKeys = true }.decodeFromString(
            Me.serializer(),
            """{"id":1,"timezone":"$zone","beginning_of_week":1$tokkl}""",
        )
    }

    private fun preferences(body: String) = MePreferences(Json.parseToJsonElement(body).jsonObject)

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-18T10:30:00Z")
    }
}
