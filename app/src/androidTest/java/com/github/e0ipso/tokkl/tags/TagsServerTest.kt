package com.github.e0ipso.tokkl.tags

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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
import kotlinx.serialization.json.buildJsonArray
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
 * The tags page against the real Tokkl container, wired as TagsViewModel wires it, on an account
 * of its own.
 */
class TagsServerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val scope = MainScope()
    private val refreshes = CopyOnWriteArrayList<Job>()
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private lateinit var model: TagsModel

    @Before
    fun seedTheServerAndRefresh() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        Session(context).save(TokklTestServer.url, account.apiToken)
        workspace = account.workspaceId
        runBlocking { app.accountSettings.refresh().join() }
        model = TagsModel(
            client = Session(context)::client,
            workspaceId = { app.accountSettings.state.value?.defaultWorkspaceId },
            refreshCache = { refreshes += app.accountSettings.refreshAfterChange() },
            scope = scope,
            io = Dispatchers.IO,
            cachedTags = app.cache.tags,
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
    fun createRenameAndDeleteATagAndTheCacheAndHistoryFollow() {
        show()
        // The field and Add tag stay off until the page has loaded the workspace's tags.
        awaitEnabled(button(R.string.tags_add))
        field(R.string.tags_new_name).type("focus")
        button(R.string.tags_add).tap()

        awaitText(string(R.string.catalog_created, "focus"))
        awaitMessageGone(string(R.string.catalog_created, "focus"))
        val id = tag("focus")!!.getValue("id").jsonPrimitive.long
        awaitCache("the cache holds the new tag") { tags, _ -> tags.any { it.id == id && it.name == "focus" } }
        field(R.string.tags_new_name).assert(SemanticsMatcher("an empty field") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "" })

        entry("focus")
        runBlocking { app.accountSettings.refresh().join() }

        row("focus").tap(scroll = false)
        awaitText(string(R.string.tags_rename_title, "focus"))
        field(R.string.tags_rename_field).clear()
        field(R.string.tags_rename_field).type("deep work")
        composeRule.onNode(hasText(string(R.string.tags_rename_save)) and hasClickAction() and hasAnyAncestor(isDialog())).tap(scroll = false)

        awaitText(string(R.string.catalog_renamed, "deep work"))
        awaitMessageGone(string(R.string.catalog_renamed, "deep work"))
        assertEquals(null, tag("focus"))
        assertEquals(id, tag("deep work")!!.getValue("id").jsonPrimitive.long)
        awaitCache("the cache and the history hold the new name") { tags, entries ->
            tags.any { it.id == id && it.name == "deep work" } && entries.single().tags == listOf("deep work")
        }

        composeRule.onNode(hasContentDescription(string(R.string.tags_delete_named, "deep work"))).tap()
        awaitText(string(R.string.tags_delete_title, "deep work"))
        composeRule.onNode(hasText(string(R.string.tags_delete_confirm)) and hasClickAction()).tap(scroll = false)

        awaitText(string(R.string.catalog_deleted, "deep work"))
        assertEquals(null, tag("deep work"))
        awaitCache("the tag left the cache and the entry") { tags, entries ->
            tags.none { it.id == id } && entries.single().tags.isEmpty()
        }
    }

    /**
     * The server stores a new tag and the connection drops before its answer reaches the phone.
     * The page takes the tag it then finds under that name for the one it made and says so. It
     * sends no second copy for the server to refuse as a duplicate name (TKA-0197).
     */
    @Test
    fun aTagWhoseAnswerWasLostShowsAsCreatedAndTheServerHoldsOneCopy() {
        throughARelay { relay ->
            show()
            awaitEnabled(button(R.string.tags_add))
            relay.dropAnswerTo("POST /api/v9/workspaces/$workspace/tags HTTP/1.1")
            field(R.string.tags_new_name).type("focus")
            button(R.string.tags_add).tap()

            awaitText(string(R.string.catalog_created, "focus"))
            assertEquals("the relay dropped the create's answer", 1, relay.droppedAnswers)
            row("focus").assertExists()
            assertNoText("A tag with the same name already exists")
        }
        assertEquals("one copy on the server", 1, tags().count { it.getValue("name").jsonPrimitive.content == "focus" })
    }

    @Test
    fun aRefusedCreateShowsTheServersReason() {
        create("focus")
        show()
        awaitText("focus")
        field(R.string.tags_new_name).type("focus")
        button(R.string.tags_add).tap()
        awaitText("A tag with the same name already exists")
        assertEquals("one copy on the server", 1, tags().count { it.getValue("name").jsonPrimitive.content == "focus" })
    }

    @Test
    fun cancellingTheDeleteQuestionKeepsTheTag() {
        create("keep")
        show()
        awaitText("keep")
        composeRule.onNode(hasContentDescription(string(R.string.tags_delete_named, "keep"))).tap()
        awaitText(string(R.string.tags_delete_title, "keep"))
        composeRule.onNode(hasText(string(R.string.catalog_cancel)) and hasClickAction()).tap(scroll = false)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.tags_delete_title, "keep")).fetchSemanticsNodes().isEmpty()
        }
        assertTrue("the server keeps the tag", tag("keep") != null)
        row("keep").assertExists()
    }

    @Test
    fun aRefusedRenameShowsTheServersReasonInTheForm() {
        create("one")
        create("two")
        show()
        awaitText("two")
        row("two").tap(scroll = false)
        field(R.string.tags_rename_field).clear()
        field(R.string.tags_rename_field).type("one")
        composeRule.onNode(hasText(string(R.string.tags_rename_save)) and hasClickAction() and hasAnyAncestor(isDialog())).tap(scroll = false)
        awaitText("A tag with the same name already exists")
        assertTrue(tag("two") != null)
    }

    @Test
    fun offlineEveryActionIsOffAndThePageSaysTheyNeedTheServer() {
        create("focus")
        show()
        awaitText("focus")
        button(R.string.tags_add).assertIsEnabled()

        AirplaneMode.set(true, account.client)
        awaitText(string(R.string.tags_server_needed_message))
        button(R.string.tags_add).assertIsNotEnabled()
        // A disabled field takes no text, so it has no SetText action.
        composeRule.onNode(hasText(string(R.string.tags_new_name)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
            .assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.tags_delete_named, "focus"))).assertIsNotEnabled()
        row("focus").assertIsNotEnabled()

        AirplaneMode.set(false, account.client)
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) {
            composeRule.onAllNodesWithText(string(R.string.tags_server_needed_message)).fetchSemanticsNodes().isEmpty()
        }
        button(R.string.tags_add).assertIsEnabled()
    }

    @Test
    fun theServerOutOfReachListsThePhonesTags() {
        create("cached")
        runBlocking { app.accountSettings.refresh().join() }
        AirplaneMode.set(true, account.client)
        show()
        awaitText(string(R.string.tags_server_needed_title))
        awaitText("cached")
        row("cached").assertIsNotEnabled()
    }

    @Test
    fun everyControlHasALabel() {
        create("focus")
        show()
        awaitText("focus")
        assertEveryControlHasALabel()
        row("focus").tap(scroll = false)
        awaitText(string(R.string.tags_rename_title, "focus"))
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
        composeRule.setContent { TokklTheme { TagsScreen(model) } }
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

    private fun awaitCache(message: String, check: (List<CachedTag>, List<CachedTimeEntry>) -> Boolean) {
        runBlocking { refreshes.forEach { it.join() } }
        assertTrue(message, check(runBlocking { app.cache.tags.first() }, runBlocking { app.cache.timeEntries.first() }))
    }

    private fun tag(name: String): JsonObject? = tags().singleOrNull { it.getValue("name").jsonPrimitive.content == name }

    private fun tags(): List<JsonObject> = account.client.get(url("tags"), ListSerializer(JsonObject.serializer())).orFail("GET tags")

    private fun entry(tag: String) {
        val start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
        val body = buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "tokkl-android tests")
            put("description", "Tagged")
            put("start", start.toString())
            put("duration", 1800)
            put("tags", buildJsonArray { add(JsonPrimitive(tag)) })
        }
        account.client.send("POST", url("time_entries"), body, JsonObject.serializer()).orFail("POST time_entries")
    }

    private fun url(vararg path: String) = account.client.server.api("workspaces", workspace.toString(), *path)

    private fun create(name: String) {
        account.client.send("POST", url("tags"), buildJsonObject { put("name", name) }, JsonObject.serializer()).orFail("POST tags")
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun awaitText(text: String) {
        composeRule.waitUntil(NETWORK_TIMEOUT.toMillis()) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(30)

        /** Longer than a snackbar stays, SnackbarDuration.Short, with its exit. */
        const val SNACKBAR_MS = 5_000L

        /** How long a control keeps still before a tap, longer than the keyboard takes to slide. */
        const val STILL_MS = 500L
    }
}
