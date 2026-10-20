package com.github.e0ipso.tokkl.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import com.github.e0ipso.tokkl.timer.continueLast
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Continue against the real Tokkl container, in the real activity (docs/PRD.md T2). The history's
 * Continue button, found by the label TalkBack reads, and continue-last, which the Quick Settings
 * tile will use, start a new timer with the past entry's description, project, task, tags and
 * billable through the change queue. So both work in airplane mode, stop the running entry, and
 * reach the server once the network returns.
 */
class ContinueServerTest {
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
    fun continueFromTheHistoryInAirplaneModeStartsTheEntryAgainAndReachesTheServer() {
        val (project, task) = projectWithTask()
        val stop = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(2, ChronoUnit.HOURS)
        postEntry("Paint the wall", project, task, listOf("outdoor", "spray"), billable = true, stop.minus(45, ChronoUnit.MINUTES), stop)
        val running = account.createEntry("Answer email", Instant.now().minus(10, ChronoUnit.MINUTES))
        runBlocking { app.accountSettings.refresh().join() }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            val continueButton = hasContentDescription(context.getString(R.string.history_continue, "Paint the wall")) and hasClickAction()
            composeRule.waitUntil("the entry shows with its Continue button", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(continueButton and hasAnyAncestor(hasScrollToKeyAction())).fetchSemanticsNodes().size == 1
            }
            AirplaneMode.set(true, web)

            composeRule.onNode(continueButton).performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitUntil("the timer runs the continued entry at once, offline", LOAD_TIMEOUT_MS) {
                runBlocking { app.cache.running.first() }?.entry?.description == "Paint the wall" &&
                    composeRule.onAllNodes(hasContentDescription(context.getString(R.string.timer_stop_spoken)) and hasClickAction())
                        .fetchSemanticsNodes().size == 1
            }
            val continued = runBlocking { app.cache.running.first() }!!.entry
            assertEquals(project, continued.projectId)
            assertEquals(task, continued.taskId)
            assertEquals(setOf("outdoor", "spray"), continued.tags.toSet())
            assertTrue(continued.billable)

            AirplaneMode.set(false, web)
            sendTheQueue()
        }

        val current = currentEntry()
        assertNotEquals("a new entry, not the one that ran", running, current.getValue("id").jsonPrimitive.long)
        assertEquals("Paint the wall", current.string("description"))
        assertEquals(project, current.getValue("project_id").jsonPrimitive.long)
        assertEquals(task, current.getValue("task_id").jsonPrimitive.long)
        assertEquals(setOf("outdoor", "spray"), current.getValue("tags").jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals("true", current.string("billable"))
        assertTrue("the entry that ran has stopped", account.entry(running)["stop"] !is JsonNull)
    }

    /**
     * Continue tapped while a description is still being typed for the running entry gives that
     * entry the typed text as it stops, and the continued entry keeps its own description, also
     * when the keyboard's Done is pressed after the start (TKA-0237).
     */
    @Test
    fun continueWhileADescriptionIsTypedKeepsTheContinuedEntrysDescription() {
        val stop = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(2, ChronoUnit.HOURS)
        postEntry("Paint the wall", project = null, task = null, tags = emptyList(), billable = false, stop.minus(45, ChronoUnit.MINUTES), stop)
        val running = account.createEntry("Answer email", Instant.now().minus(10, ChronoUnit.MINUTES))
        runBlocking { app.accountSettings.refresh().join() }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            val continueButton = hasContentDescription(context.getString(R.string.history_continue, "Paint the wall")) and hasClickAction()
            composeRule.waitUntil("the entry shows with its Continue button", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(continueButton and hasAnyAncestor(hasScrollToKeyAction())).fetchSemanticsNodes().size == 1
            }
            val description = hasSetTextAction() and hasText(context.getString(R.string.entry_description))
            composeRule.waitUntil("the running entry's description shows", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(description and hasText("Answer email")).fetchSemanticsNodes().size == 1
            }
            composeRule.onNode(description).performTextClearance()
            composeRule.onNode(description).performTextInput("Draft")

            // A touch, as a finger taps, which leaves focus in the description field.
            composeRule.onNode(continueButton).performScrollTo().performClick()
            composeRule.waitUntil("the phone runs the continued entry", LOAD_TIMEOUT_MS) {
                runBlocking { app.cache.running.first() }?.entry?.description == "Paint the wall"
            }
            // Once the server has the start, the timer shows the cache's entry, not the tap's.
            sendTheQueue()
            composeRule.waitUntil("the timer shows every change sent", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(context.getString(R.string.history_unsent), ignoreCase = true)).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNode(description).performImeAction()
            sendTheQueue()

            assertEquals("Paint the wall", runBlocking { app.cache.running.first() }?.entry?.description)
            val shown = composeRule.onNode(description).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
            assertEquals("the description field shows the continued entry's", "Paint the wall", shown)
        }

        assertEquals("Paint the wall", currentEntry().string("description"))
        val stopped = account.entry(running)
        assertEquals("the entry it was typed for has it", "Draft", stopped.string("description"))
        assertTrue("the entry that ran has stopped", stopped["stop"] !is JsonNull)
    }

    @Test
    fun continueLastInAirplaneModeStartsTheLatestStoppedEntryAndReachesTheServer() {
        val (project, task) = projectWithTask()
        val stop = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.HOURS)
        postEntry("Earlier work", project = null, task = null, tags = emptyList(), billable = false, stop.minus(3, ChronoUnit.HOURS), stop.minus(2, ChronoUnit.HOURS))
        postEntry("Latest work", project, task, listOf("deep"), billable = true, stop.minus(30, ChronoUnit.MINUTES), stop)
        runBlocking { app.accountSettings.refresh().join() }
        AirplaneMode.set(true, web)

        val id = runBlocking { app.changes.continueLast(app.cache.entries.first().map { it.entry }) }

        val continued = runBlocking { app.cache.running.first() }!!.entry
        assertEquals("the new entry runs on the phone at once", id, continued.id)
        assertEquals("Latest work", continued.description)
        AirplaneMode.set(false, web)
        sendTheQueue()

        val current = currentEntry()
        assertEquals("Latest work", current.string("description"))
        assertEquals(project, current.getValue("project_id").jsonPrimitive.long)
        assertEquals(task, current.getValue("task_id").jsonPrimitive.long)
        assertEquals(listOf("deep"), current.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("true", current.string("billable"))
    }

    /** A project with a task, as the web app would make them. */
    private fun projectWithTask(): Pair<Long, Long> {
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
        return project to task
    }

    private fun postEntry(
        description: String,
        project: Long?,
        task: Long?,
        tags: List<String>,
        billable: Boolean,
        start: Instant,
        stop: Instant,
    ): Long =
        web.send(
            "POST",
            web.server.api("workspaces", account.workspaceId.toString(), "time_entries"),
            buildJsonObject {
                put("workspace_id", account.workspaceId)
                put("created_with", "tokkl-android tests")
                put("description", description)
                if (project != null) put("project_id", project)
                if (task != null) put("task_id", task)
                put("tags", JsonArray(tags.map(::JsonPrimitive)))
                put("billable", billable)
                put("start", start.toString())
                put("stop", stop.toString())
            },
            JsonObject.serializer(),
        ).orFail("POST an entry").getValue("id").jsonPrimitive.long

    /** The server's running entry. */
    private fun currentEntry(): JsonObject =
        web.get(web.server.api("me", "time_entries", "current"), JsonObject.serializer()).orFail("GET /api/v9/me/time_entries/current")

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

    private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.content.orEmpty()

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
    }
}
