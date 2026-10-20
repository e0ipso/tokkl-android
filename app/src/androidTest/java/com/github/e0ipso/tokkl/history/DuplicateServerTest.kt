package com.github.e0ipso.tokkl.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Duplicate against the real Tokkl container, in the real activity (docs/PRD.md H5). The copy
 * goes through the change queue, so it works in airplane mode and reaches the server once the
 * network returns, with the source's fields and, by the web app's rule, its start and stop.
 */
class DuplicateServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        signIn(TokklTestServer.url)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
        } finally {
            try {
                signIn(TokklTestServer.url)
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun aDuplicateMadeInAirplaneModeReachesTheServerWhenTheNetworkReturns() {
        val workspace = account.workspaceId
        val project = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", "Mural") },
            JsonObject.serializer(),
        ).orFail("POST a project").getValue("id").jsonPrimitive.long
        val task = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects", project.toString(), "tasks"),
            buildJsonObject { put("name", "Outline") },
            JsonObject.serializer(),
        ).orFail("POST a task").getValue("id").jsonPrimitive.long
        val stop = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.HOURS)
        val start = stop.minus(45, ChronoUnit.MINUTES)
        val source = web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", "Paint the wall")
                put("project_id", project)
                put("task_id", task)
                put("tags", JsonArray(listOf(JsonPrimitive("outdoor"), JsonPrimitive("spray"))))
                put("billable", true)
                put("start", start.toString())
                put("stop", stop.toString())
            },
            JsonObject.serializer(),
        ).orFail("POST an entry").getValue("id").jsonPrimitive.long
        // The server snaps the times to the account's resolution, and the copy takes them as stored.
        val stored = web.get(web.server.api("me", "time_entries", source.toString()), JsonObject.serializer()).orFail("GET the source")
        runBlocking { app.accountSettings.refresh().join() }
        val unsent = context.getString(R.string.history_unsent)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            composeRule.waitUntil("the entry shows", LOAD_TIMEOUT_MS) { rowsIn("Paint the wall") == 1 }
            AirplaneMode.set(true, web)

            composeRule.onNode(hasContentDescription(context.getString(R.string.history_more_actions, "Paint the wall")) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.onNode(hasText(context.getString(R.string.history_duplicate)) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitUntil("the copy shows at once, marked unsent", LOAD_TIMEOUT_MS) {
                rowsIn("Paint the wall") == 2 &&
                    composeRule.onAllNodes(hasText("Paint the wall") and hasText(unsent, ignoreCase = true)).fetchSemanticsNodes().size == 1
            }
            composeRule.waitUntil("the message says so", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(context.getString(R.string.history_duplicated))).fetchSemanticsNodes().isNotEmpty()
            }

            AirplaneMode.set(false, web)
            sendTheQueue()
        }

        val entries = serverEntries(start).filter { it.string("description") == "Paint the wall" }
        assertEquals("the source and its copy", 2, entries.size)
        val copy = entries.single { it.getValue("id").jsonPrimitive.long != source }
        assertEquals(project, copy.getValue("project_id").jsonPrimitive.long)
        assertEquals(task, copy.getValue("task_id").jsonPrimitive.long)
        assertEquals(setOf("outdoor", "spray"), copy.getValue("tags").jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals("true", copy.getValue("billable").jsonPrimitive.content)
        assertEquals("the source's start", stored.instant("start"), copy.instant("start"))
        assertEquals("the source's stop", stored.instant("stop"), copy.instant("stop"))
    }

    /** The server's entries that start within a day of [start]. */
    private fun serverEntries(start: Instant): List<JsonObject> {
        val url = web.server.api("me", "time_entries").newBuilder()
            .addQueryParameter("start_date", start.minus(1, ChronoUnit.DAYS).toString())
            .addQueryParameter("end_date", start.plus(1, ChronoUnit.DAYS).toString())
            .build()
        return web.get(url, ListSerializer(JsonObject.serializer())).orFail("GET /api/v9/me/time_entries")
    }

    /** Sends the queue until it is empty, as the next refresh would. */
    private fun sendTheQueue() {
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            check(System.nanoTime() < deadline) { "the queue did not empty" }
            runBlocking { app.sender.send().join() }
        }
    }

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    /** How many rows of the list show [description]. */
    private fun rowsIn(description: String): Int =
        composeRule.onAllNodes(hasText(description) and hasClickAction() and hasAnyAncestor(hasScrollToKeyAction()))
            .fetchSemanticsNodes().size

    private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.content.orEmpty()

    private fun JsonObject.instant(key: String): Instant = OffsetDateTime.parse(string(key)).toInstant()

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
    }
}
