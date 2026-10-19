package com.github.e0ipso.tokkl.picker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The picker on a host made for the test, since no timer or entry edit screen exists yet, over
 * the cache that one refresh from the real Tokkl container filled. The account has an old entry
 * on an archived project and a done task, and other entries that keep another archived project
 * and done task in the cache. Each test seeds its own account and deletes it after.
 */
class EntityPickerServerTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private lateinit var seed: Seed
    private var picked by mutableStateOf(Picked())

    @Before
    fun seedTheServerAndRefresh() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        seed = Seed(account.client)
        runBlocking { app.accountSettings.refresh().join() }
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
    fun theOldEntrysArchivedItemsStaySelectedWithALabelAndNoOtherArchivedItemIsOffered() {
        show(Picked(seed.oldSite, seed.oldTask, listOf("legacy")))

        option("Old site").assertIsSelected().assert(hasText(string(R.string.picker_archived)))
        option("Old task").assertIsSelected().assert(hasText(string(R.string.picker_archived)))
        option("legacy").assertIsOn()
        option("Website").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        assertEquals("the active projects and tasks are on offer", 1, composeRule.onAllNodesWithText("Design").fetchSemanticsNodes().size)

        assertTrue("the cache holds Retired, archived", runBlocking { app.cache.projects.first() }.any { it.name == "Retired" && !it.active })
        assertTrue("the cache holds Dropped, done", runBlocking { app.cache.tasks.first() }.any { it.name == "Dropped" && !it.active })
        for (unused in listOf("Retired", "Dropped")) {
            search(unused)
            composeRule.onAllNodesWithText(unused).assertCountEquals(1) // The search field.
            scrolledTo(hasText(string(R.string.picker_no_match))).assertExists()
        }
    }

    @Test
    fun searchAndSelectionWorkInAirplaneModeAndThePickerSaysCreatingNeedsAConnection() {
        show(Picked())
        composeRule.onAllNodesWithText(string(R.string.picker_offline)).assertCountEquals(0)

        setAirplaneMode(on = true)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.picker_offline)).fetchSemanticsNodes().isNotEmpty()
        }
        search("des")
        composeRule.onAllNodesWithText("Internal").assertCountEquals(0)
        option("Design").performClick()
        assertEquals(Picked(seed.website, seed.design), picked)
        option("Design").assertIsSelected()
        option("Website").assertIsSelected()

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        option("focus").assertIsOff().performClick()
        assertEquals(listOf("focus"), picked.tags)
        option("focus").assertIsOn()
        assertEveryOptionHasALabelAndAState()
    }

    @Test
    fun aTagTypedInAirplaneModeJoinsTheEntryByNameAndTheServerHasItOnceTheQueueSendsIt() {
        show(Picked())
        setAirplaneMode(on = true)

        search("typed offline")
        scrolledTo(hasText(string(R.string.picker_add_tag, "typed offline"))).performClick()
        assertEquals(listOf("typed offline"), picked.tags)
        option("typed offline").assertIsOn().assert(hasText(string(R.string.picker_new_tag)))

        val start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
        val id = runBlocking {
            app.changes.create(
                seed.workspace,
                EntryFields(description = "Picked offline", projectId = picked.projectId, taskId = picked.taskId, tags = picked.tags),
                start = start,
                stop = start.plus(Duration.ofMinutes(30)),
            )
        }
        val row = runBlocking { app.cache.entries.first() }.single { it.entry.id == id }
        assertEquals("the entry keeps the tag by name", listOf("typed offline"), row.entry.tags)
        assertTrue(row.unsent)

        setAirplaneMode(on = false)
        val deadline = System.nanoTime() + NETWORK_TIMEOUT.toNanos()
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            if (System.nanoTime() > deadline) throw AssertionError("the queue did not empty")
            runBlocking { app.sender.send().join() }
        }

        val api = account.client
        val serverId = runBlocking { app.changes.currentId(id) }
        val entry = api.get(api.server.api("me", "time_entries", serverId.toString()), JsonObject.serializer()).orFail("GET the entry")
        assertEquals(listOf("typed offline"), entry.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        val tags = api.get(api.server.api("me", "tags"), ListSerializer(JsonObject.serializer())).orFail("GET /api/v9/me/tags")
        assertTrue("the server made the tag", tags.any { it.getValue("name").jsonPrimitive.content == "typed offline" })
    }

    private fun show(original: Picked) {
        picked = original
        composeRule.setContent {
            TokklTheme {
                EntityPicker(
                    cache = app.cache,
                    workspaceId = seed.workspace,
                    original = original,
                    picked = picked,
                    onPickedChange = { picked = it },
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText("Website").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun search(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    /** The option row for [name], scrolled into view: one TalkBack item with a radio button or checkbox role. */
    private fun option(name: String): SemanticsNodeInteraction = scrolledTo(hasText(name) and (hasRole(Role.RadioButton) or hasRole(Role.Checkbox)))

    private fun scrolledTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        return composeRule.onNode(matcher)
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    /** Each option reads its name and its selected or checked state, never colour alone. */
    private fun assertEveryOptionHasALabelAndAState() {
        val options = composeRule.onAllNodes(hasRole(Role.RadioButton) or hasRole(Role.Checkbox)).fetchSemanticsNodes()
        assertTrue(options.isNotEmpty())
        for (node in options) {
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
            assertTrue("an option without a label", text.isNotBlank())
            val state = node.config.getOrNull(SemanticsProperties.Selected) ?: node.config.getOrNull(SemanticsProperties.ToggleableState)
            assertTrue("$text has no selected or checked state", state != null)
        }
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** As in AccountCacheServerTest: switches airplane mode and waits until the server answers or not. */
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

    /**
     * One client with projects Website, Old site and Retired, and Internal without a client. Old
     * site and Retired are archived, and the tasks Old task and Dropped are done. The old entry
     * uses Old site, Old task and the tag legacy; two newer ones keep Retired and Dropped cached.
     */
    private class Seed(private val api: TogglApiClient) {
        val workspace = api.get(api.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue("default_workspace_id").jsonPrimitive.long
        private val client = create("clients", buildJsonObject { put("name", "Acme") })
        val website = create("projects", buildJsonObject { put("name", "Website"); put("client_id", client) })
        val oldSite = create("projects", buildJsonObject { put("name", "Old site"); put("client_id", client) })
        private val retired = create("projects", buildJsonObject { put("name", "Retired"); put("client_id", client) })
        private val internal = create("projects", buildJsonObject { put("name", "Internal") })
        val design = create("projects/$website/tasks", buildJsonObject { put("name", "Design") })
        private val dropped = create("projects/$website/tasks", buildJsonObject { put("name", "Dropped") })
        val oldTask = create("projects/$oldSite/tasks", buildJsonObject { put("name", "Old task") })

        init {
            create("tags", buildJsonObject { put("name", "focus") })
            create("tags", buildJsonObject { put("name", "legacy") })
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            entry(now.minus(3, ChronoUnit.DAYS), oldSite, oldTask, "legacy")
            entry(now.minus(2, ChronoUnit.DAYS), website, dropped, null)
            entry(now.minus(1, ChronoUnit.DAYS), retired, null, null)
            entry(now.minus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), internal, null, null)
            update("projects/$oldSite", buildJsonObject { put("active", false) })
            update("projects/$retired", buildJsonObject { put("active", false) })
            update("projects/$oldSite/tasks/$oldTask", buildJsonObject { put("active", false) })
            update("projects/$website/tasks/$dropped", buildJsonObject { put("active", false) })
        }

        private fun entry(start: Instant, project: Long, task: Long?, tag: String?) {
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
                    if (tag != null) put("tags", buildJsonArray { add(JsonPrimitive(tag)) })
                },
            )
        }

        private fun update(path: String, body: JsonObject) {
            val url = api.server.api("workspaces", workspace.toString(), *path.split('/').toTypedArray())
            api.send("PUT", url, body, JsonObject.serializer()).orFail("PUT $path")
        }

        /** POSTs [body] to a workspace collection such as `clients` and returns the new id. */
        private fun create(collection: String, body: JsonObject): Long {
            val url = api.server.api("workspaces", workspace.toString(), *collection.split('/').toTypedArray())
            return api.send("POST", url, body, JsonObject.serializer()).orFail("POST $collection").getValue("id").jsonPrimitive.long
        }
    }

    private companion object {
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
