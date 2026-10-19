package com.github.e0ipso.tokkl.projects

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The projects page against the real Tokkl container, wired as ProjectsViewModel wires it, on an
 * account of its own with the client Acme and the archived client Gone.
 */
class ProjectsServerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val scope = MainScope()
    private val refreshes = CopyOnWriteArrayList<Job>()
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private var acme = 0L
    private lateinit var model: ProjectsModel

    @Before
    fun seedTheServerAndRefresh() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        workspace = account.workspaceId
        acme = create("clients", buildJsonObject { put("name", "Acme") })
        val gone = create("clients", buildJsonObject { put("name", "Gone") })
        account.client.sendNoContent("POST", url("clients", gone.toString(), "archive"), buildJsonObject {}).orFail("archive Gone")
        runBlocking { app.accountSettings.refresh().join() }
        model = ProjectsModel(
            client = Session(context)::client,
            workspaceId = { app.accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { refreshes += app.accountSettings.refreshAfterChange() },
            scope = scope,
            io = Dispatchers.IO,
            cachedProjects = app.cache.projects,
            cachedClients = app.cache.clients,
        )
    }

    @After
    fun cleanUp() {
        scope.cancel()
        try {
            if (::account.isInitialized) AirplaneMode.set(false, account.client)
        } finally {
            try {
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun createEditArchiveAndRestoreAProjectAndTheCacheFollowsEachChange() {
        show()

        button(R.string.projects_new).performClick()
        composeRule.awaitText(string(R.string.project_form_new))
        field(R.string.project_name).type("Website")
        colour(R.string.project_colour_green).tap()
        colour(R.string.project_colour_green).assertIsSelected()
        colour(R.string.project_colour_blue).assertIsNotSelected()
        composeRule.onNode(hasText(string(R.string.project_client))).tap()
        composeRule.onNode(hasText("Acme") and hasClickAction()).tap(scroll = false)
        assertEquals("an archived client is not offered", 0, composeRule.onAllNodesWithText("Gone", substring = true).fetchSemanticsNodes().size)
        field(R.string.project_rate).type("50")
        field(R.string.project_currency).type("usd")
        field(R.string.project_estimate).type("12,5")
        composeRule.onNode(hasText(string(R.string.project_billable_default)) and hasClickAction()).tap()
        button(R.string.project_create).tap()

        composeRule.awaitText(string(R.string.catalog_created, "Website"))
        var server = project("Website")
        assertEquals("#2da608", server.string("color"))
        assertEquals(acme, server.getValue("client_id").jsonPrimitive.long)
        assertEquals(50.0, server.number("rate")!!, 0.0)
        assertEquals("USD", server.string("currency"))
        assertEquals(12.5, server.number("estimated_hours")!!, 0.0)
        assertEquals("true", server.getValue("billable").jsonPrimitive.content)
        awaitCache("the cache holds the new project") { cached ->
            cached.any { it.id == server.id() && it.color == "#2da608" && it.clientId == acme && it.billable && it.active }
        }
        val id = server.id()

        row("Website").tap(scroll = false)
        composeRule.awaitText(string(R.string.project_form_edit))
        field(R.string.project_rate).assert(hasText("50"))
        field(R.string.project_estimate).assert(hasText("12.5"))
        field(R.string.project_name).clear()
        field(R.string.project_name).type("Web site")
        colour(R.string.project_colour_custom).tap()
        field(R.string.project_colour_custom_field).clear()
        field(R.string.project_colour_custom_field).type("#123456")
        composeRule.onNode(hasText(string(R.string.project_client))).tap()
        composeRule.onNode(hasText(string(R.string.projects_no_client)) and hasClickAction()).tap(scroll = false)
        field(R.string.project_rate).clear()
        field(R.string.project_currency).clear()
        field(R.string.project_estimate).clear()
        composeRule.onNode(hasText(string(R.string.project_billable_default)) and hasClickAction()).tap()
        button(R.string.project_save).tap()

        composeRule.awaitText(string(R.string.catalog_saved, "Web site"))
        server = project("Web site")
        assertEquals(id, server.id())
        assertEquals("#123456", server.string("color"))
        assertEquals(null, server.string("client_id"))
        assertEquals(null, server.number("rate"))
        assertEquals(null, server.string("currency"))
        assertEquals(null, server.number("estimated_hours"))
        assertEquals("false", server.getValue("billable").jsonPrimitive.content)
        awaitCache("the cache holds the edited project") { cached ->
            cached.any { it.id == id && it.name == "Web site" && it.color == "#123456" && it.clientId == null && !it.billable }
        }

        composeRule.onNode(hasContentDescription(string(R.string.projects_archive_named, "Web site"))).tap()
        composeRule.awaitText(string(R.string.catalog_archived, "Web site"))
        assertEquals("false", project("Web site").getValue("active").jsonPrimitive.content)
        composeRule.onNode(hasText(string(R.string.projects_archived)) and isHeading()).assertExists()
        awaitCache("the cache no longer offers the archived project") { cached -> cached.none { it.id == id && it.active } }

        composeRule.onNode(hasContentDescription(string(R.string.projects_restore_named, "Web site"))).tap()
        composeRule.awaitText(string(R.string.catalog_restored, "Web site"))
        assertEquals("true", project("Web site").getValue("active").jsonPrimitive.content)
        awaitCache("the cache offers the restored project") { cached -> cached.any { it.id == id && it.active } }
    }

    @Test
    fun aRefusedChangeShowsTheServersReasonAndKeepsTheForm() {
        create("projects", buildJsonObject { put("name", "Taken") })
        show()
        composeRule.awaitText("Taken")

        button(R.string.projects_new).performClick()
        field(R.string.project_name).type("Taken")
        button(R.string.project_create).tap()

        composeRule.awaitText("Name has already been taken")
        composeRule.onNode(hasText(string(R.string.project_form_new))).assertExists()
    }

    @Test
    fun anEmptyNameIsAMistakeUnderTheField() {
        show()
        button(R.string.projects_new).performClick()
        field(R.string.project_rate).type("abc")
        button(R.string.project_create).tap()

        composeRule.awaitText(string(R.string.project_name_missing))
        composeRule.awaitText(string(R.string.project_rate_invalid))
    }

    @Test
    fun offlineEveryActionIsOffAndThePageSaysTheyNeedTheServer() {
        create("projects", buildJsonObject { put("name", "Internal") })
        show()
        composeRule.awaitText("Internal")
        button(R.string.projects_new).assertIsEnabled()

        AirplaneMode.set(true, account.client)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.projects_server_needed_message)).fetchSemanticsNodes().isNotEmpty()
        }
        button(R.string.projects_new).assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.projects_archive_named, "Internal"))).assertIsNotEnabled()
        row("Internal").assertIsNotEnabled()

        AirplaneMode.set(false, account.client)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.projects_server_needed_message)).fetchSemanticsNodes().isEmpty()
        }
        button(R.string.projects_new).assertIsEnabled()
    }

    @Test
    fun theServerOutOfReachListsThePhonesProjectsWithEveryActionOff() {
        create("projects", buildJsonObject { put("name", "Cached") })
        runBlocking { app.accountSettings.refresh().join() }
        AirplaneMode.set(true, account.client)
        show()

        composeRule.awaitText(string(R.string.projects_server_needed_title))
        composeRule.awaitText("Cached")
        button(R.string.projects_new).assertIsNotEnabled()
        row("Cached").assertIsNotEnabled()
    }

    @Test
    fun everyControlHasALabel() {
        create("projects", buildJsonObject { put("name", "Internal") })
        show()
        composeRule.awaitText("Internal")
        assertEveryControlHasALabel()
        button(R.string.projects_new).performClick()
        composeRule.awaitText(string(R.string.project_form_new))
        assertEveryControlHasALabel()
    }

    private fun assertEveryControlHasALabel() {
        val controls = composeRule.onAllNodes(hasClickAction() or hasSetTextAction()).fetchSemanticsNodes()
        assertTrue(controls.isNotEmpty())
        for (node in controls) {
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
                node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            assertFalse("a control without a label: ${node.config}", text.isBlank())
        }
    }

    /**
     * Shows the page in a window laid out as MainActivity's: edge to edge, and resized for the
     * keyboard, never panned. The target SDK makes every window edge to edge from Android 15 on.
     * Below that a plain ComponentActivity keeps the keyboard's insets from the page, which then
     * keeps its full height, so a control scrolled into view sits at the bottom of the window,
     * under the snackbar of the change before, and a touch there lands on the snackbar.
     */
    private fun show() {
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.enableEdgeToEdge(window)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent { TokklTheme { ProjectsScreen(model) } }
    }

    /** Types [text] into the field, as a user does, and waits until the keyboard it opens takes up room. */
    private fun SemanticsNodeInteraction.type(text: String) {
        performScrollTo().performTextInput(text)
        awaitKeyboard()
    }

    /** Clears the field, as a user does, and waits until the keyboard it opens takes up room. */
    private fun SemanticsNodeInteraction.clear() {
        performScrollTo().performTextClearance()
        awaitKeyboard()
    }

    /**
     * Taps the control, scrolled into view unless [scroll] is false, once it has kept still for
     * [STILL_MS]. While the keyboard slides in or out, the page shrinks or grows above it frame
     * by frame, and a tap aimed at where a control near the bottom was lands beside it.
     */
    private fun SemanticsNodeInteraction.tap(scroll: Boolean = true) {
        if (scroll) performScrollTo()
        var bounds: Rect? = null
        var since = SystemClock.uptimeMillis()
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            val now = fetchSemanticsNode().boundsInRoot
            if (now != bounds) {
                bounds = now
                since = SystemClock.uptimeMillis()
            }
            SystemClock.uptimeMillis() - since >= STILL_MS
        }
        performClick()
    }

    /** Waits until the keyboard takes up room at the bottom of the window. */
    private fun awaitKeyboard() {
        val decor = composeRule.activity.window.decorView
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            var height = 0
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                height = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            }
            height > 0
        }
    }

    private fun button(id: Int) = composeRule.onNode(hasText(string(id)) and hasClickAction())

    private fun field(id: Int) = composeRule.onNode(hasText(string(id)) and hasSetTextAction())

    private fun colour(id: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(id)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    private fun row(name: String) = composeRule.onNode(hasText(name) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    private fun awaitCache(message: String, check: (List<CachedProject>) -> Boolean) {
        runBlocking { refreshes.forEach { it.join() } }
        assertTrue(message, check(runBlocking { app.cache.projects.first() }))
    }

    private fun project(name: String): JsonObject {
        val list = account.client.get(url("projects"), ListSerializer(JsonObject.serializer())).orFail("GET projects")
        return list.single { it.getValue("name").jsonPrimitive.content == name }
    }

    private fun JsonObject.id() = getValue("id").jsonPrimitive.long

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = (get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun url(vararg path: String) = account.client.server.api("workspaces", workspace.toString(), *path)

    private fun create(collection: String, body: JsonObject): Long =
        account.client.send("POST", url(collection), body, JsonObject.serializer()).orFail("POST $collection").getValue("id").jsonPrimitive.long

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun ComposeTestRule.awaitText(text: String) {
        waitUntil(NETWORK_TIMEOUT.toMillis()) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }



    private companion object {
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(30)

        /** How long a control keeps still before a tap, longer than the keyboard takes to slide. */
        const val STILL_MS = 500L
    }
}
