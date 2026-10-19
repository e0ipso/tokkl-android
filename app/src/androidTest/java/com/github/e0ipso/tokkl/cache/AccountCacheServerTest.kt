package com.github.e0ipso.tokkl.cache

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * One refresh from the real Tokkl container fills the cache, and with the phone in airplane mode
 * the app reads every cached kind and the account settings. The test seeds its own account, so the
 * account scripts/instrumented-tests.sh made stays empty for the other tests, and deletes it after.
 */
class AccountCacheServerTest {

    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = ZONE)
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            if (::account.isInitialized) setAirplaneMode(on = false)
        } finally {
            try {
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun afterOneRefreshEveryCachedKindReadsInAirplaneMode() {
        val seed = Seed(account.client)
        val stale = CacheContents(
            timeEntries = listOf(
                cachedEntry(STALE_OLD_ENTRY, seed.now.minus(30, ChronoUnit.DAYS)),
                cachedEntry(STALE_DELETED_ENTRY, seed.now.minus(1, ChronoUnit.DAYS)),
            ),
            projects = listOf(CachedProject(seed.project, seed.workspace, null, "Stale name", null, active = true, billable = false)),
            tags = listOf(CachedTag(STALE_TAG, seed.workspace, "stale")),
        )
        // Another instance on the same file, as the app's own is not open to writes from here.
        CacheDatabase.open(context).run {
            try {
                cache().refresh { _, _ -> stale }
            } finally {
                close()
            }
        }

        runBlocking { app.accountSettings.refresh().join() }
        setAirplaneMode(on = true)
        val offline = account.client.get(account.client.server.api("me"), JsonObject.serializer())
        assertTrue("requests fail in airplane mode: $offline", (offline as? ApiResult.Failure)?.error is ApiError.Network)

        val cache = app.cache
        assertEquals(
            "today's and the archived project's entries, newest first; the 20-day-old one is outside the window",
            listOf(seed.todayEntry, seed.archivedProjectEntry),
            runBlocking { cache.timeEntries.first() }.map { it.id },
        )
        val projects = runBlocking { cache.projects.first() }
        assertEquals(
            "the refreshed project and the archived one an entry uses, not the unused archived one",
            listOf(Triple(seed.archivedProject, "Archived, on an entry", false), Triple(seed.project, "Website", true)),
            projects.map { Triple(it.id, it.name, it.active) },
        )
        assertEquals(listOf(seed.client to "Acme"), runBlocking { cache.clients.first() }.map { it.id to it.name })
        assertEquals(
            listOf(seed.tag to "focus", seed.oldTag to "legacy"),
            runBlocking { cache.tags.first() }.map { it.id to it.name },
        )
        assertEquals(listOf(seed.task to "Design"), runBlocking { cache.tasks.first() }.map { it.id to it.name })
        assertEquals(
            listOf(CachedFavorite(0, "Write report", seed.project, seed.task, listOf("focus"), billable = false)),
            runBlocking { cache.favorites.first() },
        )

        val settings = AccountSettingsStore(context).load()
        assertNotNull("the account settings are stored", settings)
        assertEquals(ZONE, settings!!.timeZone)
        assertEquals(settings, app.accountSettings.state.value)
    }

    /**
     * Puts the phone in airplane mode, or takes it out, and waits until requests to the server
     * fail for want of a network, or get through again. Only the shell user may switch it.
     */
    private fun setAirplaneMode(on: Boolean) {
        DeviceShell.run("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
        val deadline = System.nanoTime() + NETWORK_TIMEOUT.toNanos()
        while (serverAnswers() == on) {
            if (System.nanoTime() > deadline) throw AssertionError("the network did not ${if (on) "go" else "come back"}")
            Thread.sleep(200)
        }
    }

    private fun serverAnswers(): Boolean {
        val result = account.client.get(account.client.server.api("me"), JsonObject.serializer())
        return (result as? ApiResult.Failure)?.error !is ApiError.Network
    }

    private fun cachedEntry(id: Long, start: Instant) =
        CachedTimeEntry(id, 0, null, null, "stale", start, start.plusSeconds(60), false, emptyList(), emptyList())

    /** Data on the server: one client, three projects, two tags, a task, three entries and a favorite. */
    private class Seed(private val api: TogglApiClient) {
        val now: Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val workspace = api.get(api.server.api("me"), Me.serializer()).orFail("GET /api/v9/me").defaultWorkspaceId
        val client = create("clients", buildJsonObject { put("name", "Acme") })
        val project = create("projects", buildJsonObject { put("name", "Website"); put("client_id", client) })
        val archivedProject = create("projects", buildJsonObject { put("name", "Archived, on an entry") })
        val unusedProject = create("projects", buildJsonObject { put("name", "Archived, unused") })
        val tag = create("tags", buildJsonObject { put("name", "focus") })
        val oldTag = create("tags", buildJsonObject { put("name", "legacy") })
        val task = create("projects/$project/tasks", buildJsonObject { put("name", "Design") })
        val todayEntry = entry(now.minus(2, ChronoUnit.HOURS), project, task, tag)
        val archivedProjectEntry = entry(now.minus(3, ChronoUnit.DAYS), archivedProject, null, oldTag)
        val oldEntry = entry(now.minus(20, ChronoUnit.DAYS), project, null, null)

        init {
            archive(archivedProject)
            archive(unusedProject)
            val favorite = buildJsonObject {
                put("description", "Write report")
                put("project_id", project)
                put("task_id", task)
                put("tags", buildJsonArray { add(JsonPrimitive("focus")) })
                put("billable", false)
            }
            api.send(
                "POST",
                api.server.api("me", "preferences"),
                buildJsonObject { put("favorites", JsonArray(listOf(favorite))) },
                JsonObject.serializer(),
            ).orFail("POST /api/v9/me/preferences")
        }

        private fun entry(start: Instant, project: Long, task: Long?, tag: Long?): Long =
            create(
                "time_entries",
                buildJsonObject {
                    put("workspace_id", workspace)
                    put("created_with", "tokkl-android tests")
                    put("description", "Entry")
                    put("start", start.toString())
                    put("duration", 1800)
                    put("project_id", project)
                    if (task != null) put("task_id", task)
                    if (tag != null) put("tag_ids", buildJsonArray { add(JsonPrimitive(tag)) })
                },
            )

        private fun archive(project: Long) {
            api.send(
                "PUT",
                api.server.api("workspaces", workspace.toString(), "projects", project.toString()),
                buildJsonObject { put("active", false) },
                JsonObject.serializer(),
            ).orFail("PUT a project")
        }

        /** POSTs [body] to a workspace collection such as `clients` and returns the new id. */
        private fun create(collection: String, body: JsonObject): Long {
            val url = api.server.api("workspaces", workspace.toString(), *collection.split('/').toTypedArray())
            return api.send("POST", url, body, Created.serializer()).orFail("POST $collection").id
        }
    }

    @Serializable
    private class Me(@SerialName("default_workspace_id") val defaultWorkspaceId: Long)

    @Serializable
    private class Created(val id: Long)

    private companion object {
        /** UTC+13 in October, so the history window starts at another instant than in UTC. */
        const val ZONE = "Pacific/Auckland"
        const val STALE_OLD_ENTRY = 9_000_000L
        const val STALE_DELETED_ENTRY = 9_000_001L
        const val STALE_TAG = 9_000_002L
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
