package com.github.e0ipso.tokkl.clients

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
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
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
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
 * The clients page against the real Tokkl container, wired as ClientsViewModel wires it, on an
 * account of its own.
 */
class ClientsServerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val scope = MainScope()
    private val refreshes = CopyOnWriteArrayList<Job>()
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private lateinit var model: ClientsModel

    @Before
    fun seedTheServerAndRefresh() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        workspace = account.workspaceId
        runBlocking { app.accountSettings.refresh().join() }
        model = ClientsModel(
            client = Session(context)::client,
            workspaceId = { app.accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { refreshes += app.accountSettings.refreshAfterChange() },
            scope = scope,
            io = Dispatchers.IO,
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
    fun createEditArchiveAndRestoreAClientAndTheCacheFollowsEachChange() {
        show()
        button(R.string.clients_new).tap()
        awaitText(string(R.string.client_form_new))
        field(R.string.client_name).type("Acme")
        field(R.string.client_notes).type("Pays late")
        button(R.string.client_create).tap()

        awaitText(string(R.string.catalog_created, "Acme"))
        awaitMessageGone(string(R.string.catalog_created, "Acme"))
        var server = client("Acme")
        assertEquals("Pays late", server.string("notes"))
        val id = server.id()
        awaitCache("the cache holds the new client") { clients, _ -> clients.any { it.id == id && it.name == "Acme" && !it.archived } }

        val project = create("projects", buildJsonObject { put("name", "Website"); put("client_id", id) })
        runBlocking { app.accountSettings.refresh().join() }
        // The page counts projects when it loads, as opening it again would.
        composeRule.runOnUiThread { model.retry() }
        awaitText(context.resources.getQuantityString(R.plurals.clients_projects, 1, 1))

        row("Acme").tap(scroll = false)
        awaitText(string(R.string.client_form_edit))
        field(R.string.client_name).clear()
        field(R.string.client_name).type("Acme Inc")
        field(R.string.client_notes).clear()
        button(R.string.client_save).tap()

        awaitText(string(R.string.catalog_saved, "Acme Inc"))
        awaitMessageGone(string(R.string.catalog_saved, "Acme Inc"))
        server = client("Acme Inc")
        assertEquals(id, server.id())
        assertEquals(null, server.string("notes"))
        awaitCache("the cache holds the new name") { clients, _ -> clients.any { it.id == id && it.name == "Acme Inc" } }

        composeRule.onNode(hasContentDescription(string(R.string.clients_archive_named, "Acme Inc"))).tap()
        awaitText(string(R.string.clients_archive_title, "Acme Inc"))
        awaitText(context.resources.getQuantityString(R.plurals.clients_archive_projects, 1, 1))
        composeRule.onNode(hasText(string(R.string.clients_archive)) and hasClickAction() and hasAnyAncestor(isDialog())).tap(scroll = false)

        awaitText(string(R.string.clients_archived_done, "Acme Inc"))
        awaitMessageGone(string(R.string.clients_archived_done, "Acme Inc"))
        assertEquals("true", client("Acme Inc").getValue("archived").jsonPrimitive.content)
        composeRule.onNode(hasText(string(R.string.clients_archived)) and isHeading()).assertExists()
        composeRule.onNode(hasText("Acme Inc") and hasClickAction()).assert(hasText(string(R.string.clients_archived)))
        awaitCache("the cache holds the archived client, and its project is archived") { clients, projects ->
            clients.any { it.id == id && it.archived } && projects.none { it.id == project && it.active }
        }

        composeRule.onNode(hasContentDescription(string(R.string.clients_restore_named, "Acme Inc"))).tap()
        awaitText(string(R.string.clients_restored_done, "Acme Inc"))
        assertEquals("false", client("Acme Inc").getValue("archived").jsonPrimitive.content)
        awaitCache("the cache holds the restored client and project") { clients, projects ->
            clients.any { it.id == id && !it.archived } && projects.any { it.id == project && it.active }
        }
    }

    /**
     * The server stores a new client and the connection drops before its answer reaches the
     * phone. The page takes the client it then finds under that name for the one it made and says
     * so. It sends no second copy for the server to refuse as a duplicate name (TKA-0197).
     */
    @Test
    fun aClientWhoseAnswerWasLostShowsAsCreatedAndTheServerHoldsOneCopy() {
        throughARelay { relay ->
            show()
            awaitEnabled(button(R.string.clients_new))
            relay.dropAnswerTo("POST /api/v9/workspaces/$workspace/clients HTTP/1.1")
            button(R.string.clients_new).tap()
            awaitText(string(R.string.client_form_new))
            field(R.string.client_name).type("Acme")
            button(R.string.client_create).tap()

            awaitText(string(R.string.catalog_created, "Acme"))
            assertEquals("the relay dropped the create's answer", 1, relay.droppedAnswers)
            row("Acme").assertExists()
            assertNoText("Name has already been taken")
        }
        assertEquals("one copy on the server", 1, clients().count { it.getValue("name").jsonPrimitive.content == "Acme" })
    }

    @Test
    fun aRefusedCreateShowsTheServersReasonAndKeepsTheForm() {
        create("clients", buildJsonObject { put("name", "Acme") })
        show()
        awaitText("Acme")
        button(R.string.clients_new).tap()
        awaitText(string(R.string.client_form_new))
        field(R.string.client_name).type("Acme")
        button(R.string.client_create).tap()

        awaitText("Name has already been taken")
        composeRule.onNode(hasText(string(R.string.client_form_new))).assertExists()
        assertEquals("one copy on the server", 1, clients().count { it.getValue("name").jsonPrimitive.content == "Acme" })
    }

    @Test
    fun cancellingTheArchiveQuestionKeepsTheClient() {
        create("clients", buildJsonObject { put("name", "Keep") })
        show()
        awaitText("Keep")
        composeRule.onNode(hasContentDescription(string(R.string.clients_archive_named, "Keep"))).tap()
        awaitText(string(R.string.clients_archive_no_projects))
        composeRule.onNode(hasText(string(R.string.catalog_cancel)) and hasClickAction()).tap(scroll = false)
        composeRule.waitForIdle()
        assertEquals("false", client("Keep").getValue("archived").jsonPrimitive.content)
    }

    @Test
    fun offlineEveryActionIsOffAndThePageSaysTheyNeedTheServer() {
        create("clients", buildJsonObject { put("name", "Acme") })
        show()
        awaitText("Acme")
        button(R.string.clients_new).assertIsEnabled()

        AirplaneMode.set(true, account.client)
        awaitText(string(R.string.clients_server_needed_message))
        button(R.string.clients_new).assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.clients_archive_named, "Acme"))).assertIsNotEnabled()
        row("Acme").assertIsNotEnabled()

        AirplaneMode.set(false, account.client)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.clients_server_needed_message)).fetchSemanticsNodes().isEmpty()
        }
        button(R.string.clients_new).assertIsEnabled()
    }

    @Test
    fun theServerOutOfReachListsThePhonesClientsWithTheArchivedOnesMarked() {
        val gone = create("clients", buildJsonObject { put("name", "Gone") })
        account.client.sendNoContent("POST", url("clients", gone.toString(), "archive"), buildJsonObject {}).orFail("archive")
        runBlocking { app.accountSettings.refresh().join() }
        AirplaneMode.set(true, account.client)
        show()

        awaitText(string(R.string.clients_server_needed_title))
        awaitText("Gone")
        row("Gone").assert(hasText(string(R.string.clients_archived))).assertIsNotEnabled()
        button(R.string.clients_new).assertIsNotEnabled()
    }

    @Test
    fun everyControlHasALabel() {
        create("clients", buildJsonObject { put("name", "Acme") })
        show()
        awaitText("Acme")
        assertEveryControlHasALabel()
        button(R.string.clients_new).tap()
        awaitText(string(R.string.client_form_new))
        assertEveryControlHasALabel()
    }

    private fun assertEveryControlHasALabel() {
        val controls = composeRule.onAllNodes(hasClickAction() or hasSetTextAction()).fetchSemanticsNodes()
        assertTrue(controls.isNotEmpty())
        for (node in controls) {
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
            assertFalse("a control without a label: ${node.config}", text.isBlank())
        }
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

    /**
     * Shows the page in a window laid out as MainActivity's: edge to edge, and resized for the
     * keyboard, never panned, as ProjectsServerTest does. Below Android 15 a plain
     * ComponentActivity keeps the keyboard's insets from the page, which then keeps its full
     * height, so a control scrolled into view sits at the bottom of the window, under the
     * snackbar of the change before, and a touch there lands on the snackbar.
     */
    private fun show() {
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.enableEdgeToEdge(window)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent { TokklTheme { ClientsScreen(model) } }
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
     * If the keyboard covers the control after the scroll, scroll again: a clipped control can
     * keep still too, but a tap on it reaches the keyboard.
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
     * Waits until the snackbar that says [text] has gone, as it does after four seconds, so that it
     * covers no control a later tap aims at.
     */
    private fun awaitMessageGone(text: String) {
        composeRule.mainClock.advanceTimeBy(SNACKBAR_MS)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    private fun button(id: Int) = composeRule.onNode(hasText(string(id)) and hasClickAction())

    private fun field(id: Int) = composeRule.onNode(hasText(string(id)) and hasSetTextAction())

    private fun row(name: String) =
        composeRule.onNode(hasText(name) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    private fun hasAnyAncestor(matcher: SemanticsMatcher) = androidx.compose.ui.test.hasAnyAncestor(matcher)

    private fun awaitCache(message: String, check: (List<CachedClient>, List<CachedProject>) -> Boolean) {
        runBlocking { refreshes.forEach { it.join() } }
        assertTrue(message, check(runBlocking { app.cache.clients.first() }, runBlocking { app.cache.projects.first() }))
    }

    private fun client(name: String): JsonObject = clients().single { it.getValue("name").jsonPrimitive.content == name }

    private fun clients(): List<JsonObject> {
        val url = url("clients").newBuilder().addQueryParameter("status", "both").build()
        return account.client.get(url, ListSerializer(JsonObject.serializer())).orFail("GET clients")
    }

    private fun JsonObject.id() = getValue("id").jsonPrimitive.long

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun url(vararg path: String) = account.client.server.api("workspaces", workspace.toString(), *path)

    private fun create(collection: String, body: JsonObject): Long =
        account.client.send("POST", url(collection), body, JsonObject.serializer()).orFail("POST $collection").getValue("id").jsonPrimitive.long

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun awaitText(text: String) {
        try {
            composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            val shown = composeRule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            throw AssertionError("\"$text\" never showed. Shown: $shown", e)
        }
    }

    private companion object {
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(30)

        /** Longer than a snackbar stays, SnackbarDuration.Short, with its exit. */
        const val SNACKBAR_MS = 5_000L

        /** How long a control keeps still before a tap, longer than the keyboard takes to slide. */
        const val STILL_MS = 500L
    }
}
