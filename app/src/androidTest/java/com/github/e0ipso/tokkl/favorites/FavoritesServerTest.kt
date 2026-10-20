package com.github.e0ipso.tokkl.favorites

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
 * The favorites on the Timer page against the real Tokkl container (docs/PRD.md T5). The test's
 * own requests stand in for the web app: they set favorites in `/me/preferences` as TimerPage.tsx
 * does, and read back what the phone stored. Each test signs in to an account of its own and
 * deletes it after.
 */
class FavoritesServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

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
    fun theWebAppsFavoritesShowAndOneTapStartsOneOffline() {
        val project = createProject("Favorites project")
        saveOnTheWeb(
            """
            {"favorites": [
              {"description": "Write the report", "project_id": $project, "task_id": null, "tags": ["call"], "billable": true},
              {"description": "", "project_id": $project, "task_id": null, "tags": [], "billable": false}
            ]}
            """,
        )
        launch {
            awaitShown("the first favorite with its project") { favorite("Write the report, Favorites project") }
            awaitShown("the second favorite by its project's name") { favorite("Favorites project") }

            AirplaneMode.set(true, web)
            favorite("Write the report, Favorites project").performClick()
            awaitAtOnce("Stop shows") { shows(stopButton()) }
            awaitAtOnce("the favorite's description runs") {
                shows(composeRule.onNode(hasSetTextAction() and hasText("Write the report")))
            }

            AirplaneMode.set(false, web)
            drainUntilEmpty()
        }

        val entry = serverEntries().single()
        assertEquals("Write the report", entry.getValue("description").jsonPrimitive.content)
        assertEquals(project, entry.getValue("project_id").jsonPrimitive.long)
        assertEquals(listOf("call"), entry.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue("billable", entry.getValue("billable").jsonPrimitive.boolean)
        assertTrue("running", entry["stop"] == null || entry["stop"] is JsonNull)
    }

    /**
     * Pinning, moving and removing on the phone store the list in the web app's format, and leave
     * every other preference key as it was.
     */
    @Test
    fun favoritesEditedOnThePhoneReachTheWebInItsFormat() {
        saveOnTheWeb(
            """
            {"favorites": [{"description": "Write the report", "project_id": null, "task_id": null, "tags": [], "billable": false}],
             "pomodoro_enabled": true,
             "anotherClientsKey": {"kept": [1, "two"]}}
            """,
        )
        launch {
            description().performTextInput("Pinned on the phone")
            description().performImeAction()
            editButton().performClick()
            pinButton().performClick()
            awaitShown("the pinned favorite can move up") { control(R.string.favorites_move_up, "Pinned on the phone") }
            awaitEnabled(control(R.string.favorites_move_up, "Pinned on the phone")).performClick()
            awaitShown("the old favorite can move down") { control(R.string.favorites_move_down, "Write the report") }
            awaitEnabled(control(R.string.favorites_remove, "Write the report")).performClick()
            composeRule.waitUntil("the old favorite is gone", NETWORK_TIMEOUT.toMillis()) {
                !shows(control(R.string.favorites_remove, "Write the report"))
            }
            composeRule.waitUntil("the phone caches what it stored", NETWORK_TIMEOUT.toMillis()) {
                runBlocking { app.cache.favorites.first() }.map { it.description } == listOf("Pinned on the phone")
            }
        }

        val preferences = web.get(web.server.api("me", "preferences"), JsonObject.serializer()).orFail("GET /api/v9/me/preferences")
        assertEquals(
            Json.parseToJsonElement("""[{"description": "Pinned on the phone", "project_id": null, "task_id": null, "tags": [], "billable": false}]"""),
            preferences.getValue("favorites"),
        )
        assertTrue("pomodoro_enabled stays", preferences.getValue("pomodoro_enabled").jsonPrimitive.boolean)
        assertEquals(Json.parseToJsonElement("""{"kept": [1, "two"]}"""), preferences.getValue("anotherClientsKey"))
    }

    @Test
    fun nineFavoritesAreTheMost() {
        val nine = (1..MAX_FAVORITES).joinToString(",") {
            """{"description": "Favorite $it", "project_id": null, "task_id": null, "tags": [], "billable": false}"""
        }
        saveOnTheWeb("""{"favorites": [$nine]}""")
        launch {
            awaitShown("the ninth favorite") { favorite("Favorite 9") }
            description().performTextInput("A tenth")
            description().performImeAction()
            editButton().performClick()
            val full = context.resources.getQuantityString(R.plurals.favorites_full, MAX_FAVORITES, MAX_FAVORITES)
            awaitShown("the list says it is full") { composeRule.onNode(hasText(full)) }
            pinButton().assertIsNotEnabled()
        }
        val stored = web.get(web.server.api("me", "preferences"), JsonObject.serializer()).orFail("GET /api/v9/me/preferences")
        assertEquals(MAX_FAVORITES, stored.getValue("favorites").jsonArray.size)
    }

    @Test
    fun editingOfflineSaysItNeedsTheServer() {
        saveOnTheWeb("""{"favorites": [{"description": "Write the report", "project_id": null, "task_id": null, "tags": [], "billable": false}]}""")
        launch {
            awaitShown("the favorite") { favorite("Write the report") }
            AirplaneMode.set(true, web)
            editButton().performClick()
            awaitShown("the offline notice") { composeRule.onNode(hasText(string(R.string.favorites_offline))) }
            control(R.string.favorites_remove, "Write the report").assertIsNotEnabled()
            pinButton().assertIsNotEnabled()
            AirplaneMode.set(false, web)
        }
    }

    /**
     * A server without Tokkl's favorites, such as Toggl Track, shows no favorites control. The
     * same favorites show once the capability is there, so the screen did draw.
     */
    @Test
    fun withoutTheCapabilityNoFavoritesControlShows() {
        val favorites = listOf(FavoriteItem(EntryFields(description = "Write the report"), EntryLabels()))
        val available = mutableStateOf(false)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TokklTheme {
                        FavoritesSection(
                            state = FavoritesState(available = available.value, favorites = favorites),
                            timerFields = EntryFields(description = "Typed"),
                            onStart = {},
                            onAdd = {},
                            onRemove = {},
                            onMove = { _, _ -> },
                        )
                    }
                }
            }
            composeRule.waitForIdle()
            assertEquals(0, count(hasText(string(R.string.favorites_title))))
            assertEquals(0, count(hasContentDescription(string(R.string.favorites_edit_spoken))))
            assertEquals(0, count(hasContentDescription(string(R.string.favorites_start_spoken, "Write the report"))))

            scenario.onActivity { available.value = true }
            composeRule.waitForIdle()
            assertEquals(1, count(hasContentDescription(string(R.string.favorites_start_spoken, "Write the report")) and hasClickAction()))
        }
    }

    private fun count(matcher: SemanticsMatcher): Int = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    private fun launch(steps: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(NETWORK_TIMEOUT.toMillis())
            // The refresh the launch started, so the cache holds what the server has.
            runBlocking { app.accountSettings.refresh().join() }
            composeRule.waitUntil("Start can start", NETWORK_TIMEOUT.toMillis()) {
                shows(composeRule.onNode(hasContentDescription(string(R.string.timer_start_spoken)) and hasClickAction() and isEnabled()))
            }
            steps()
        }
    }

    private fun saveOnTheWeb(json: String) {
        web.send("POST", web.server.api("me", "preferences"), Json.parseToJsonElement(json), JsonElement.serializer())
            .orFail("POST /api/v9/me/preferences")
    }

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    private fun createProject(name: String): Long =
        web.send(
            "POST",
            web.server.api("workspaces", workspace.toString(), "projects"),
            buildJsonObject { put("name", name) },
            JsonObject.serializer(),
        ).orFail("POST a project").getValue("id").jsonPrimitive.long

    /** A favorite's start button, as TalkBack reads it for [spokenName]. */
    private fun favorite(spokenName: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.favorites_start_spoken, spokenName)) and hasClickAction())

    /** A button beside the favorite [name] while editing. */
    private fun control(@StringRes label: Int, name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(label, name)) and hasClickAction())

    private fun editButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.favorites_edit_spoken)) and hasClickAction())

    private fun pinButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.favorites_pin)) and hasClickAction())

    private fun description(): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.entry_description)))

    private fun stopButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.timer_stop_spoken)) and hasClickAction())

    private fun isEnabled(): SemanticsMatcher = SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled)

    private fun awaitShown(what: String, node: () -> SemanticsNodeInteraction) {
        composeRule.waitUntil("$what shows", NETWORK_TIMEOUT.toMillis()) { shows(node()) }
    }

    /** Waits until [node] is enabled, as it is again once the change before it is stored. */
    private fun awaitEnabled(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        composeRule.waitUntil("the button is enabled", NETWORK_TIMEOUT.toMillis()) {
            shows(node) && node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null
        }
        return node.also { it.assertIsEnabled() }
    }

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

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        val AT_ONCE: Duration = Duration.ofSeconds(2)
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
