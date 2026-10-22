package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The timer on the Timer page against the real Tokkl container (docs/PRD.md T1). The app talks
 * to the server through a [HangingRelay], so a test can make the network hang, and airplane mode
 * cuts the relay off from the server too. The test's own requests, which stand in for the web
 * app, go straight to the server. Each test signs in to an account of its own and deletes it after.
 *
 * Each tap has to change the screen within [AT_ONCE], far below OkHttp's 10-second timeouts,
 * which a tap that waited for the network would run into.
 */
class TimerServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private lateinit var relay: HangingRelay
    private var workspace = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInThroughTheRelay() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = web.get(web.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        relay = HangingRelay(TokklTestServer.url)
        signIn(relay.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
            if (::relay.isInitialized) relay.close()
        } finally {
            try {
                // TestAccounts talks to the server through the app's guard, which follows the session.
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun startEditsAndStopShowAtOnceWhileTheNetworkHangsAndReachTheServer() {
        val project = createProject("Timer project")
        var shortStop = false
        launch {
            relay.hang()
            description().performTextInput("Write the timer")
            start().performClick()
            awaitAtOnce("Stop shows") { shows(stopButton()) }
            awaitAtOnce("the running state shows in words") { shows(composeRule.onNode(hasText(string(R.string.timer_running)))) }
            assertTrue("the clock starts at zero: ${clockText()}", clockText().startsWith("0:00:0"))

            billable().performClick()
            awaitAtOnce("billable is on") { billableState() == ToggleableState.On }

            projectButton(null).performClick()
            option("Timer project").performClick()
            composeRule.onNode(hasSetTextAction() and hasText(string(R.string.picker_search))).performTextInput("call")
            option(string(R.string.picker_add_tag, "call")).performClick()
            composeRule.onNode(hasText(string(R.string.entry_picker_done)) and hasClickAction()).performClick()
            awaitAtOnce("the project shows") { shows(projectButton("Timer project")) }
            awaitAtOnce("the tag shows") { shows(composeRule.onNode(hasContentDescription(string(R.string.entry_tags_spoken, "call")))) }

            description().performTextClearance()
            description().performTextInput("Write the timer screen")
            description().performImeAction()
            stop().performClick()
            awaitAtOnce("Start shows") { shows(startButton()) }
            awaitAtOnce("Stop reaches the cache") { runBlocking { app.cache.running.first() } == null }
            shortStop = localShortStop()

            assertTrue("the app tried to send while the network hung", relay.hungConnections > 0)
            relay.release()
            drainUntilEmpty()
        }

        val entry = stoppedEntry(shortStop)
        assertEquals("Write the timer screen", entry.string("description"))
        assertEquals(project, entry.getValue("project_id").jsonPrimitive.long)
        assertEquals(listOf("call"), entry.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue("billable", entry.getValue("billable").jsonPrimitive.boolean)
        assertTrue("stopped", entry["stop"] !is JsonNull)
    }

    @Test
    fun startEditsAndStopWorkInAirplaneModeAndReachTheServerAfterwards() {
        var shortStop = false
        launch {
            AirplaneMode.set(true, web)
            description().performTextInput("Offline timer")
            start().performClick()
            awaitAtOnce("Stop shows") { shows(stopButton()) }
            awaitAtOnce("the timer says the server lacks it") {
                shows(composeRule.onNode(hasText(string(R.string.history_unsent), ignoreCase = true)))
            }
            billable().performClick()
            awaitAtOnce("billable is on") { billableState() == ToggleableState.On }
            // Stop while the description is still being typed: the stop takes it along.
            description().performTextClearance()
            description().performTextInput("Offline timer, edited")
            stop().performClick()
            awaitAtOnce("Start shows") { shows(startButton()) }
            awaitAtOnce("Stop reaches the cache") { runBlocking { app.cache.running.first() } == null }
            shortStop = localShortStop()
            assertEquals("the idle timer starts empty", "", descriptionText())

            AirplaneMode.set(false, web)
            drainUntilEmpty()
        }

        val entry = stoppedEntry(shortStop)
        assertEquals("Offline timer, edited", entry.string("description"))
        assertTrue("billable", entry.getValue("billable").jsonPrimitive.boolean)
        assertTrue("stopped", entry["stop"] !is JsonNull)
    }

    /**
     * While the network hangs, every request the app makes stays in the relay, which counts it.
     * Once Start's own request is there, the clock ticks on and no other request comes.
     */
    @Test
    fun theClockTicksWithoutAnyRequest() {
        launch {
            relay.hang()
            start().performClick()
            awaitAtOnce("Stop shows") { shows(stopButton()) }
            composeRule.waitUntil("Start's request reaches the network", NETWORK_TIMEOUT.toMillis()) { relay.hungConnections > 0 }
            // A pooled connection that the hang cut makes OkHttp retry on a new one at once.
            Thread.sleep(SETTLE.toMillis())
            val requests = relay.hungConnections

            val first = clockText()
            composeRule.waitUntil("the clock moves on from $first", TICK_TIMEOUT.toMillis()) { clockText() != first }
            val second = clockText()
            composeRule.waitUntil("the clock moves on from $second", TICK_TIMEOUT.toMillis()) { clockText() != second }

            assertEquals("requests while the clock ticked from $first past $second", requests, relay.hungConnections)
        }
    }

    /**
     * The web app starts a timer that the phone, offline, has not seen. The phone's own Start
     * shows alone, and once the queue sends it the server stops the web's timer, so one runs.
     */
    @Test
    fun startingWhileAnotherEntryRunsLeavesOneRunning() {
        launch {
            val fromTheWeb = web.send(
                "POST",
                web.server.api("workspaces", workspace.toString(), "time_entries"),
                buildJsonObject {
                    put("workspace_id", workspace)
                    put("created_with", "tokkl-android tests")
                    put("description", "Started on the web")
                    put("start", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                    put("duration", -1)
                },
                JsonObject.serializer(),
            ).orFail("POST a running entry").getValue("id").jsonPrimitive.long
            AirplaneMode.set(true, web)
            description().performTextInput("Started on the phone")
            start().performClick()
            awaitAtOnce("Stop shows") { shows(stopButton()) }

            AirplaneMode.set(false, web)
            drainUntilEmpty()

            val running = runBlocking { app.cache.timeEntries.first() }.filter { it.stop == null }
            assertEquals("one running entry on the phone", listOf("Started on the phone"), running.map { it.description })
            awaitAtOnce("the phone's entry shows running") { shows(composeRule.onNode(hasText("Started on the phone") and hasSetTextAction())) }
            assertTrue("the web's timer stopped", serverEntry(fromTheWeb)["stop"] !is JsonNull)
            val current = web.get(web.server.api("me", "time_entries", "current"), JsonElement.serializer()).orFail("GET the running entry")
            assertEquals("Started on the phone", current.jsonObject.string("description"))
        }
    }

    private fun launch(steps: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(NETWORK_TIMEOUT.toMillis())
            // The refresh the launch started, so the cache holds what the server has.
            runBlocking { app.accountSettings.refresh().join() }
            composeRule.waitUntil("Start can start", NETWORK_TIMEOUT.toMillis()) { shows(startButton()) }
            steps()
        }
    }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    private fun createProject(name: String): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", name) },
            JsonObject.serializer(),
        ).orFail("POST a project").getValue("id").jsonPrimitive.long

    private fun description(): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.entry_description)))

    private fun descriptionText(): String =
        description().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun startButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.timer_start_spoken)) and hasClickAction())

    private fun stopButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.timer_stop_spoken)) and hasClickAction())

    private fun start(): SemanticsNodeInteraction = startButton()

    private fun stop(): SemanticsNodeInteraction = stopButton()

    private fun billable(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable())

    private fun billableState(): ToggleableState? =
        billable().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    /** The project button, as TalkBack reads it for [name], or for no project. */
    private fun projectButton(name: String?): SemanticsNodeInteraction {
        val spoken = name?.let { string(R.string.entry_project_spoken, it) } ?: string(R.string.entry_no_project_spoken)
        return composeRule.onNode(hasContentDescription(spoken) and hasClickAction())
    }

    /** A row of the picker, scrolled into view. */
    private fun option(text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and hasClickAction()
        val list = hasScrollToNodeAction() and hasAnyDescendant(hasText(string(R.string.picker_project)))
        composeRule.onNode(list).performScrollToNode(matcher)
        return composeRule.onNode(matcher)
    }

    private fun clockText(): String =
        composeRule.onNode(hasContentDescription(" elapsed", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    /** Whether exactly one node matches. */
    private fun shows(node: SemanticsNodeInteraction): Boolean =
        try {
            node.fetchSemanticsNode()
            true
        } catch (e: AssertionError) {
            false
        }

    private fun awaitAtOnce(what: String, condition: () -> Boolean) {
        composeRule.waitUntil("$what within $AT_ONCE", AT_ONCE.toMillis(), condition)
    }

    /** Sends the queue until it is empty, as each change and refresh would, then refreshes. */
    private fun drainUntilEmpty() {
        val deadline = System.nanoTime() + NETWORK_TIMEOUT.toNanos()
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            if (System.nanoTime() > deadline) throw AssertionError("the queue is not empty")
            runBlocking { app.sender.send().join() }
        }
        runBlocking { app.accountSettings.refresh().join() }
    }

    private fun serverEntries(): List<JsonObject> =
        web.get(web.server.api("me", "time_entries"), JsonArray.serializer()).orFail("GET /api/v9/me/time_entries")
            .map { it.jsonObject }

    private fun localShortStop(): Boolean {
        val undo = runBlocking { app.changes.shortStopUndo.first() }
        val entries = runBlocking { app.cache.timeEntries.first() }
        if (undo == null) {
            assertTrue("a kept Stop remains in history", entries.any { it.stop != null })
        } else {
            assertEquals("the short Stop holds a zero span for Undo", undo.entry.start, undo.entry.stop)
            assertTrue("the short Stop leaves history", entries.none { it.id == undo.entryId })
        }
        return undo != null
    }

    private fun stoppedEntry(shortStop: Boolean): JsonObject {
        val kept = serverEntries()
        val trashed = web.get(web.server.app("trash"), JsonArray.serializer()).orFail("GET /api/app/trash")
            .map { it.jsonObject }
        assertEquals("the server has exactly one stopped entry", 1, kept.size + trashed.size)
        assertEquals("the server follows the offline short Stop prediction", shortStop, trashed.isNotEmpty())
        val entry = (kept + trashed).single()
        assertTrue("the Stop reached the server", entry.getValue("stop") !is JsonNull)
        if (shortStop) {
            assertEquals("Trash holds the zero span", entry.string("start"), entry.string("stop"))
            assertEquals(0L, entry.getValue("duration").jsonPrimitive.long)
            assertTrue("Trash records deletion", entry.getValue("server_deleted_at") !is JsonNull)
        } else {
            assertTrue("a kept entry is outside Trash", entry.getValue("server_deleted_at") is JsonNull)
        }
        return entry
    }

    private fun serverEntry(id: Long): JsonObject =
        web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer()).orFail("GET a time entry")

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        val AT_ONCE: Duration = Duration.ofSeconds(2)
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(60)
        val SETTLE: Duration = Duration.ofMillis(500)
        val TICK_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
