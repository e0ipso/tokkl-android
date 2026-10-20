package com.github.e0ipso.tokkl.timer

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The timer's description suggestions against the real Tokkl container (docs/PRD.md T4), at
 * Android's largest font scale, so the list has to stay usable there. They come from the cached
 * entries, so they show in airplane mode, and a pick fills every field as the web app's does.
 * Each test signs in to an account of its own and deletes it after.
 */
class DescriptionSuggestionsServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST))
        .around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = account.workspaceId
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
    fun suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField() {
        val client = create("clients", buildJsonObject { put("name", "Acme") })
        val project = create("projects", buildJsonObject { put("name", "Website"); put("client_id", client) })
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        entry("Write the report", project, listOf("call"), billable = true, start = now.minus(3, ChronoUnit.HOURS))
        entry("Write the report", project, listOf("call"), billable = true, start = now.minus(5, ChronoUnit.HOURS))
        entry("Review the report", projectId = null, tags = emptyList(), billable = false, start = now.minus(7, ChronoUnit.HOURS))
        entry("Lunch", projectId = null, tags = emptyList(), billable = false, start = now.minus(9, ChronoUnit.HOURS))
        launch {
            AirplaneMode.set(true, web)
            description().performTextInput("REPORT")
            awaitShown("the count of suggestions") { composeRule.onNode(hasText(plural(R.plurals.timer_suggestions, 2))) }
            val count = composeRule.onNode(hasText(plural(R.plurals.timer_suggestions, 2))).fetchSemanticsNode()
            assertEquals("TalkBack announces the count", LiveRegionMode.Polite, count.config.getOrNull(SemanticsProperties.LiveRegion))
            assertTrue(
                "the newest entry's suggestion comes first",
                suggestion("Write the report").getBoundsInRoot().top < suggestion("Review the report").getBoundsInRoot().top,
            )
            assertEquals("an entry that does not match is left out", 0, count(hasText("Lunch") and inTheList()))
            assertUsable("Write the report")
            assertUsable("Review the report")

            description().performTextClearance()
            description().performTextInput("acme")
            awaitShown("the suggestion that matches the client") { composeRule.onNode(hasText(plural(R.plurals.timer_suggestions, 1))) }

            suggestion("Write the report").performClick()
            try {
                awaitShown("the picked description") { composeRule.onNode(hasSetTextAction() and hasText("Write the report")) }
            } catch (e: Throwable) {
                throw AssertionError("the pick did not fill the description: ${fieldsAndPopups()}", e)
            }
            composeRule.onNode(hasSetTextAction() and hasText("Write the report")).assertIsFocused()
            awaitShown("the picked project") { composeRule.onNode(hasContentDescription(string(R.string.entry_project_spoken, "Website"))) }
            awaitShown("the picked tags") { composeRule.onNode(hasContentDescription(string(R.string.entry_tags_spoken, "call"))) }
            assertEquals(
                ToggleableState.On,
                composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable()).fetchSemanticsNode()
                    .config.getOrNull(SemanticsProperties.ToggleableState),
            )

            composeRule.onNode(hasContentDescription(string(R.string.timer_start_spoken)) and hasClickAction()).performClick()
            awaitShown("Stop") { composeRule.onNode(hasContentDescription(string(R.string.timer_stop_spoken)) and hasClickAction()) }
            AirplaneMode.set(false, web)
            drainUntilEmpty()
        }

        val started = serverEntries().single { it["stop"] == null || it["stop"] is JsonNull }
        assertEquals("Write the report", started.getValue("description").jsonPrimitive.content)
        assertEquals(project, started.getValue("project_id").jsonPrimitive.long)
        assertEquals(listOf("call"), started.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue("billable", started.getValue("billable").jsonPrimitive.boolean)
    }

    private fun launch(steps: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(NETWORK_TIMEOUT.toMillis())
            // The refresh the launch started, so the cache holds what the server has.
            runBlocking { app.accountSettings.refresh().join() }
            composeRule.waitUntil("the cache holds the entries", NETWORK_TIMEOUT.toMillis()) {
                runBlocking { app.cache.timeEntries.first() }.size == ENTRIES
            }
            composeRule.waitUntil("Start can start", NETWORK_TIMEOUT.toMillis()) {
                shows(composeRule.onNode(hasContentDescription(string(R.string.timer_start_spoken)) and hasClickAction() and enabled()))
            }
            steps()
        }
    }

    /** A suggestion in the list, by its description. */
    private fun suggestion(description: String): SemanticsNodeInteraction =
        composeRule.onNode(hasClickAction() and hasText(description) and inTheList())

    /** The text fields' texts and the menu's items, for a failure message. */
    private fun fieldsAndPopups(): String {
        val fields = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.EditableText)?.text to it.boundsInRoot }
        val items = composeRule.onAllNodes(hasClickAction() and inTheList()).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { text -> text.text } to it.positionOnScreen }
        return "fields $fields, suggestions $items, screen ${context.resources.displayMetrics.heightPixels}px"
    }

    /** Inside the suggestions, whose list starts with how many there are. */
    private fun inTheList(): SemanticsMatcher =
        hasAnyAncestor(hasAnyChild(hasText(plural(R.plurals.timer_suggestions, 1)) or hasText(plural(R.plurals.timer_suggestions, 2))))

    private fun count(matcher: SemanticsMatcher): Int = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    /** The suggestion for [description] is whole on the screen, its text not cut off, and tall enough to tap. */
    private fun assertUsable(description: String) {
        val node = suggestion(description)
        val box = node.getBoundsInRoot()
        assertTrue("the suggestion for $description is at least 48dp tall: $box", box.bottom - box.top >= 48.dp)
        val semantics = node.fetchSemanticsNode()
        val top = semantics.positionOnScreen.y
        val screenHeight = context.resources.displayMetrics.heightPixels
        assertTrue("the suggestion for $description is on the screen", top >= 0f && top + semantics.size.height <= screenHeight)
        val text = composeRule.onNode(hasText(description) and inTheList(), useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        text.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertTrue("the suggestion for $description lays out its text", results.isNotEmpty())
        for (result in results) assertFalse("the suggestion for $description is cut off", result.didOverflowHeight)
    }

    private fun entry(description: String, projectId: Long?, tags: List<String>, billable: Boolean, start: Instant) {
        create(
            "time_entries",
            buildJsonObject {
                put("workspace_id", workspace)
                put("created_with", "tokkl-android tests")
                put("description", description)
                if (projectId != null) put("project_id", projectId)
                put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
                put("billable", billable)
                put("start", start.toString())
                put("stop", start.plus(30, ChronoUnit.MINUTES).toString())
            },
        )
    }

    private fun create(kind: String, body: JsonObject): Long =
        web.send("POST", web.server.api("workspaces", workspace.toString(), kind), body, JsonObject.serializer())
            .orFail("POST $kind").getValue("id").jsonPrimitive.long

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    private fun description(): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.entry_description)))

    private fun enabled(): SemanticsMatcher = SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled)

    private fun awaitShown(what: String, node: () -> SemanticsNodeInteraction) {
        composeRule.waitUntil("$what shows", NETWORK_TIMEOUT.toMillis()) { shows(node()) }
    }

    /** Whether exactly one node matches. */
    private fun shows(node: SemanticsNodeInteraction): Boolean =
        try {
            node.fetchSemanticsNode()
            true
        } catch (e: AssertionError) {
            false
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

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun plural(id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    private companion object {
        const val ENTRIES = 4
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
