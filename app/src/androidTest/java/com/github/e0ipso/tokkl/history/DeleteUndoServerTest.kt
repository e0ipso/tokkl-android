package com.github.e0ipso.tokkl.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Delete with Undo against the real Tokkl container (docs/PRD.md H3). A delete the server has gets
 * the entry back through Tokkl's `POST /api/app/time_entries/{id}/restore`, out of the trash, also
 * when the delete's answer was lost. A delete still in the queue leaves it with no request. Each
 * test signs in to an account of its own.
 */
class DeleteUndoServerTest {
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
            signIn(TokklTestServer.url)
            TestAccounts.delete(account)
        } finally {
            SessionRule.signOut()
        }
    }

    @Test
    fun undoOfASentDeleteRestoresTheEntryThroughTheRestoreEndpoint() {
        val id = recentEntry("Deleted by mistake")
        refresh()
        assertTrue("the server offers restore", app.capabilities.state.value.restore)

        assertTrue(runBlocking { app.changes.delete(id) })
        sendTheQueue()
        assertEquals("the server deleted it", 404, (serverEntry(id) as? ApiResult.Failure)?.error?.status())
        assertEquals("into the trash", listOf(id), trash())

        val outcome = RequestCounter(TokklTestServer.url, "/api/app/time_entries/$id/restore HTTP/1.1").use { counter ->
            signIn(counter.url)
            runBlocking { app.sender.undoDelete(id) }.also {
                assertEquals("one restore request", 1, counter.count)
                assertEquals(emptyList<String>(), counter.failures)
            }
        }

        assertEquals(ChangeSender.UndoOutcome.Restored, outcome)
        val restored = (serverEntry(id) as ApiResult.Success).value
        assertEquals("Deleted by mistake", restored.getValue("description").jsonPrimitive.content)
        assertEquals("out of the trash", emptyList<Long>(), trash())
        val cached = runBlocking { app.cache.entries.first() }.single { it.entry.id == id }
        assertEquals("the phone shows it again, sent", "Deleted by mistake" to false, cached.entry.description to cached.unsent)
    }

    @Test
    fun undoOfAnUnsentDeleteTakesItOutOfTheQueueWithNoRequest() {
        val id = recentEntry("Kept after all")
        refresh()
        // Nothing listens there, so the sender's try fails before any request reaches a server.
        signIn(NO_ROUTE)
        assertTrue(runBlocking { app.changes.delete(id) })
        runBlocking { app.sender.send().join() }
        assertEquals("the delete waits in the queue", 1, unsentCount())
        assertTrue("gone from the phone", runBlocking { app.cache.entries.first() }.none { it.entry.id == id })

        val outcome = RequestCounter(TokklTestServer.url, " HTTP/1.1").use { counter ->
            signIn(counter.url)
            runBlocking { app.sender.undoDelete(id) }.also {
                assertEquals("no request", 0, counter.count)
                assertEquals(emptyList<String>(), counter.failures)
            }
        }

        assertEquals(ChangeSender.UndoOutcome.Cancelled, outcome)
        assertEquals("the queue is empty", 0, unsentCount())
        val cached = runBlocking { app.cache.entries.first() }.single { it.entry.id == id }
        assertEquals("the phone shows it again, as sent", "Kept after all" to false, cached.entry.description to cached.unsent)
        signIn(TokklTestServer.url)
        assertTrue("the server never deleted it", serverEntry(id) is ApiResult.Success)
        assertEquals(emptyList<Long>(), trash())
    }

    /**
     * A delete whose answer the network lost after the server had put the entry in the trash
     * (TKA-0205). The retry gets 404, which Tokkl answers for an entry in its trash, and that
     * counts as sent, so the queue moves on. Undo then goes through the server and restores the
     * entry, which the phone shows again.
     */
    @Test
    fun undoOfADeleteWhoseAnswerWasLostRestoresTheEntryThroughTheRestoreEndpoint() {
        val id = recentEntry("Answer lost")
        refresh()
        assertTrue("the server offers restore", app.capabilities.state.value.restore)

        HangingRelay(TokklTestServer.url).use { relay ->
            signIn(relay.url)
            relay.holdAnswerTo(DELETE)
            assertTrue(runBlocking { app.changes.delete(id) })
            waitUntil("the server deleted the entry and its answer is held") { relay.holdsAnswer }
            assertEquals("the server put it in the trash", listOf(id), trash())
            // The connection drops before the answer reaches the phone, and the network stays quiet.
            relay.hang()
            runBlocking { app.sender.sendAndWait() }
            assertEquals("the delete waits in the queue", 1, unsentCount())
            relay.releaseAnswer()
            relay.release()

            val retry = runBlocking { app.sender.sendAndWait() }
            assertTrue("the retry's 404 counts as sent: $retry", retry is ChangeSender.Outcome.Empty)
            assertEquals("the queue moved on", 0, unsentCount())
        }

        val outcome = RequestCounter(TokklTestServer.url, "/api/app/time_entries/$id/restore HTTP/1.1").use { counter ->
            signIn(counter.url)
            runBlocking { app.sender.undoDelete(id) }.also {
                assertEquals("one restore request", 1, counter.count)
                assertEquals(emptyList<String>(), counter.failures)
            }
        }

        assertEquals(ChangeSender.UndoOutcome.Restored, outcome)
        signIn(TokklTestServer.url)
        val restored = serverEntry(id)
        assertTrue("the server has it again: $restored", restored is ApiResult.Success)
        assertEquals("Answer lost", (restored as ApiResult.Success).value.getValue("description").jsonPrimitive.content)
        assertEquals("out of the trash", emptyList<Long>(), trash())
        val cached = runBlocking { app.cache.entries.first() }.single { it.entry.id == id }
        assertEquals("the phone shows it again, sent", "Answer lost" to false, cached.entry.description to cached.unsent)
    }

    /**
     * In the app, the entry's menu deletes it and the message's Undo brings it back, on the phone
     * and on the server, whether the delete had gone out by then or not.
     */
    @Test
    fun theEntrysMenuDeletesItAndUndoBringsItBack() {
        val id = recentEntry("Menu entry")
        refresh()
        val more = context.getString(R.string.history_more_actions, "Menu entry")

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            composeRule.waitUntil("the entry shows", LOAD_TIMEOUT_MS) { inList("Menu entry") }
            composeRule.onNode(hasContentDescription(more) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.onNode(hasText(context.getString(R.string.history_delete)) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitUntil("the entry goes", LOAD_TIMEOUT_MS) { !inList("Menu entry") }
            val undo = hasText(context.getString(R.string.history_undo)) and hasClickAction()
            val message = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and
                hasAnyDescendant(hasText(context.getString(R.string.history_deleted))) and hasAnyDescendant(undo)
            composeRule.waitUntil("the message and its Undo show in a live region", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(message).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(undo).performSemanticsAction(SemanticsActions.OnClick)

            composeRule.waitUntil("the entry is back", LOAD_TIMEOUT_MS) { inList("Menu entry") }
        }
        sendTheQueue()
        assertTrue("the server has it", serverEntry(id) is ApiResult.Success)
        assertEquals(emptyList<Long>(), trash())
    }

    private fun recentEntry(description: String): Long {
        val stop = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.HOURS)
        return account.createEntry(description, stop.minus(30, ChronoUnit.MINUTES), stop)
    }

    private fun serverEntry(id: Long): ApiResult<JsonObject> =
        web.get(web.server.api("me", "time_entries", id.toString()), JsonObject.serializer())

    /** The ids in the account's trash, Tokkl's `GET /api/app/trash`. */
    private fun trash(): List<Long> =
        web.get(web.server.app("trash"), ListSerializer(JsonObject.serializer())).orFail("GET /api/app/trash")
            .map { it.getValue("id").jsonPrimitive.long }

    private fun ApiError.status(): Int? = (this as? ApiError.Http)?.status

    private fun refresh() = runBlocking { app.accountSettings.refresh().join() }

    private fun unsentCount(): Int = runBlocking { app.changes.unsentCount.first() }

    /** Sends the queue until it is empty, as the next refresh would. */
    private fun sendTheQueue() {
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (unsentCount() > 0) {
            check(System.nanoTime() < deadline) { "the queue did not empty" }
            runBlocking { app.sender.send().join() }
        }
    }

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    private fun inList(text: String) =
        composeRule.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollToKeyAction()), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        val NO_ROUTE: ServerUrl = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
        const val POLL_MS = 50L

        /** The start of a delete's request. */
        const val DELETE = "DELETE /api/v9/workspaces/"
    }
}
