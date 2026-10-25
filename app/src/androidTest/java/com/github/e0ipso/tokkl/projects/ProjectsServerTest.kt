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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
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
 * The projects page and the task list in a project's form against the real Tokkl container, wired
 * as ProjectsViewModel wires them, on an account of its own with the client Acme and the archived
 * client Gone.
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

    /**
     * The server stores a new project and the connection drops before its answer reaches the
     * phone. The page takes the project it then finds under that name for the one it made and
     * says so. It sends no second copy for the server to refuse as a duplicate name (TKA-0197).
     */
    @Test
    fun aProjectWhoseAnswerWasLostShowsAsCreatedAndTheServerHoldsOneCopy() {
        throughARelay { relay ->
            show()
            awaitEnabled(button(R.string.projects_new))
            relay.dropAnswerTo("POST /api/v9/workspaces/$workspace/projects HTTP/1.1")
            button(R.string.projects_new).performClick()
            composeRule.awaitText(string(R.string.project_form_new))
            field(R.string.project_name).type("Website")
            button(R.string.project_create).tap()

            composeRule.awaitText(string(R.string.catalog_created, "Website"))
            assertEquals("the relay dropped the create's answer", 1, relay.droppedAnswers)
            row("Website").assertExists()
            assertNoText("Name has already been taken")
        }
        assertEquals("one copy on the server", 1, projects().count { it.string("name") == "Website" })
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
    fun createEditAndDeleteATaskAndTheCacheFollowsEachChange() {
        val project = create("projects", buildJsonObject { put("name", "Website") })
        show()
        composeRule.awaitText("Website")
        row("Website").tap(scroll = false)
        composeRule.awaitText(string(R.string.project_tasks_empty))

        field(R.string.project_tasks_new_name).type("Design")
        button(R.string.project_tasks_add).tap()

        composeRule.awaitText(string(R.string.catalog_created, "Design"))
        awaitMessageGone(string(R.string.catalog_created, "Design"))
        val id = tasks(project).single { it.string("name") == "Design" }.id()
        assertEquals("true", tasks(project).single().getValue("active").jsonPrimitive.content)
        awaitTasksCache("the cache holds the new task") { cached -> cached.any { it.id == id && it.projectId == project && it.name == "Design" && it.active } }
        assertEveryControlHasALabel()

        row("Design").tap()
        composeRule.awaitText(string(R.string.project_task_edit_title, "Design"))
        assertEveryControlHasALabel()
        field(R.string.project_task_name).clear()
        field(R.string.project_task_name).type("Wireframes")
        dialogButton(R.string.project_task_save).tap(scroll = false)

        composeRule.awaitText(string(R.string.catalog_saved, "Wireframes"))
        awaitMessageGone(string(R.string.catalog_saved, "Wireframes"))
        var saved = tasks(project).single()
        assertEquals(id, saved.id())
        assertEquals("Wireframes", saved.string("name"))
        assertEquals("true", saved.getValue("active").jsonPrimitive.content)
        awaitTasksCache("the cache holds the renamed task") { cached -> cached.any { it.id == id && it.name == "Wireframes" && it.active } }

        row("Wireframes").tap()
        composeRule.awaitText(string(R.string.project_task_edit_title, "Wireframes"))
        composeRule.onNode(hasText(string(R.string.project_task_done)) and hasClickAction() and inDialog()).tap()
        dialogButton(R.string.project_task_save).tap(scroll = false)

        composeRule.awaitText(string(R.string.catalog_saved, "Wireframes"))
        awaitMessageGone(string(R.string.catalog_saved, "Wireframes"))
        saved = tasks(project).single()
        assertEquals("false", saved.getValue("active").jsonPrimitive.content)
        // The cache keeps a done task only while an entry uses it, so the picker stops offering it.
        awaitTasksCache("the cache drops the done task") { cached -> cached.none { it.id == id } }
        composeRule.onNode(hasText(string(R.string.project_task_done))).assertExists()

        val delete = composeRule.onNode(hasContentDescription(string(R.string.project_task_delete_named, "Wireframes")))
        delete.tap()
        composeRule.awaitText(string(R.string.project_task_delete_title, "Wireframes"))
        dialogButton(R.string.catalog_cancel).tap(scroll = false)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.project_task_delete_title, "Wireframes")).fetchSemanticsNodes().isEmpty()
        }
        assertEquals("cancelling keeps the task", listOf(id), tasks(project).map { it.id() })

        delete.tap()
        composeRule.awaitText(string(R.string.project_task_delete_title, "Wireframes"))
        dialogButton(R.string.project_task_delete_confirm).tap(scroll = false)

        composeRule.awaitText(string(R.string.catalog_deleted, "Wireframes"))
        assertEquals(emptyList<JsonObject>(), tasks(project))
        awaitTasksCache("the cache no longer has the deleted task") { cached -> cached.none { it.id == id } }
        composeRule.awaitText(string(R.string.project_tasks_empty))
    }

    /**
     * The server stores a new task and the connection drops before its answer reaches the phone.
     * The list takes the task it then finds under that name for the one it made and says so, and
     * the server holds one copy (TKA-0197).
     */
    @Test
    fun aTaskWhoseAnswerWasLostShowsAsCreatedAndTheServerHoldsOneCopy() {
        val project = create("projects", buildJsonObject { put("name", "Website") })
        throughARelay { relay ->
            show()
            composeRule.awaitText("Website")
            row("Website").tap(scroll = false)
            composeRule.awaitText(string(R.string.project_tasks_empty))
            relay.dropAnswerTo("POST /api/v9/workspaces/$workspace/projects/$project/tasks HTTP/1.1")
            field(R.string.project_tasks_new_name).type("Design")
            button(R.string.project_tasks_add).tap()

            composeRule.awaitText(string(R.string.catalog_created, "Design"))
            assertEquals("the relay dropped the create's answer", 1, relay.droppedAnswers)
            row("Design").assertExists()
            assertNoText("Name has already been taken")
        }
        assertEquals("one copy on the server", listOf("Design"), tasks(project).map { it.string("name") })
    }

    @Test
    fun offlineEveryTaskActionIsOffAndTheListSaysTheyNeedTheServer() {
        val project = create("projects", buildJsonObject { put("name", "Website") })
        account.client.send("POST", url("projects", project.toString(), "tasks"), buildJsonObject { put("name", "Design") }, JsonObject.serializer())
            .orFail("POST tasks")
        show()
        composeRule.awaitText("Website")
        row("Website").tap(scroll = false)
        composeRule.awaitText("Design")
        button(R.string.project_tasks_add).assertIsEnabled()

        AirplaneMode.set(true, account.client)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.project_tasks_server_needed_message)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(string(R.string.project_tasks_new_name))).assertIsNotEnabled()
        button(R.string.project_tasks_add).assertIsNotEnabled()
        row("Design").assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.project_task_delete_named, "Design"))).assertIsNotEnabled()

        AirplaneMode.set(false, account.client)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.project_tasks_server_needed_message)).fetchSemanticsNodes().isEmpty()
        }
        button(R.string.project_tasks_add).assertIsEnabled()
        row("Design").assertIsEnabled()
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

    /** Types [text] into the field, as a user does. */
    private fun SemanticsNodeInteraction.type(text: String) {
        performScrollTo().performTextInput(text)
    }

    /** Clears the field, as a user does. */
    private fun SemanticsNodeInteraction.clear() {
        performScrollTo().performTextClearance()
    }

    /**
     * Taps the control, scrolled into view unless [scroll] is false, once it has kept still for
     * [STILL_MS]. While the keyboard slides in or out, the page shrinks or grows above it frame
     * by frame, and a tap aimed at where a control near the bottom was lands beside it.
     * If the keyboard returns after the scroll, scroll again: an empty clipped rectangle can
     * keep still too, but cannot receive a touch.
     */
    private fun SemanticsNodeInteraction.tap(scroll: Boolean = true) {
        if (scroll) performScrollTo()
        var bounds: Rect? = null
        var since = SystemClock.uptimeMillis()
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            val node = fetchSemanticsNode()
            val now = node.boundsInRoot
            val fullyVisible = now.width > 0 && now.height > 0 &&
                now.width >= node.size.width && now.height >= node.size.height
            if (!fullyVisible) {
                // A dialog can return focus to the form's field after the first scroll.
                // Its keyboard then clips the control, sometimes to an empty rectangle.
                if (scroll) performScrollTo()
                bounds = null
                since = SystemClock.uptimeMillis()
            } else if (now != bounds) {
                bounds = now
                since = SystemClock.uptimeMillis()
            }
            fullyVisible && SystemClock.uptimeMillis() - since >= STILL_MS
        }
        performClick()
    }

    /**
     * Runs [block] with the session on a [HangingRelay] to the test server, then puts the session
     * back on the server. The account's client goes through the app's guard, which follows the
     * session, so it reaches the server only outside [block].
     */
    private fun throughARelay(block: (HangingRelay) -> Unit) {
        val relay = HangingRelay(TokklTestServer.url)
        try {
            ServerTrustStore(context).confirmCleartext(relay.url)
            Session(context).save(relay.url, account.apiToken)
            block(relay)
        } finally {
            relay.close()
            ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
            Session(context).save(TokklTestServer.url, account.apiToken)
        }
    }

    /** Waits until the page has loaded what it needs to enable [node]. */
    private fun awaitEnabled(node: SemanticsNodeInteraction) {
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null
        }
    }

    private fun assertNoText(text: String) {
        assertEquals("\"$text\" shows", 0, composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size)
    }

    private fun button(id: Int) = composeRule.onNode(hasText(string(id)) and hasClickAction())

    private fun field(id: Int) = composeRule.onNode(hasText(string(id)) and hasSetTextAction())

    private fun colour(id: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(id)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    private fun row(name: String) = composeRule.onNode(hasText(name) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    /**
     * Waits until the snackbar that says [text] has gone, as it does after four seconds, so that it
     * covers no control a later tap aims at.
     */
    private fun awaitMessageGone(text: String) {
        composeRule.mainClock.advanceTimeBy(SNACKBAR_MS)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    /** A button in a dialog, which the form under it may share a label with. */
    private fun dialogButton(id: Int) = composeRule.onNode(hasText(string(id)) and hasClickAction() and inDialog())

    private fun inDialog(): SemanticsMatcher = hasAnyAncestor(isDialog())

    /** The project's tasks on the server. */
    private fun tasks(project: Long): List<JsonObject> =
        account.client.get(url("projects", project.toString(), "tasks"), ListSerializer(JsonObject.serializer())).orFail("GET tasks")

    private fun awaitTasksCache(message: String, check: (List<CachedTask>) -> Boolean) {
        runBlocking { refreshes.forEach { it.join() } }
        assertTrue(message, check(runBlocking { app.cache.tasks.first() }))
    }

    private fun awaitCache(message: String, check: (List<CachedProject>) -> Boolean) {
        runBlocking { refreshes.forEach { it.join() } }
        assertTrue(message, check(runBlocking { app.cache.projects.first() }))
    }

    private fun project(name: String): JsonObject = projects().single { it.getValue("name").jsonPrimitive.content == name }

    private fun projects(): List<JsonObject> =
        account.client.get(url("projects"), ListSerializer(JsonObject.serializer())).orFail("GET projects")

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

        /** Longer than a snackbar stays, SnackbarDuration.Short, with its exit. */
        const val SNACKBAR_MS = 5_000L

        /** How long a control keeps still before a tap, longer than the keyboard takes to slide. */
        const val STILL_MS = 500L
    }
}
