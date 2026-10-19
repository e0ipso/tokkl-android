package com.github.e0ipso.tokkl.catalog

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.BackgroundTrip
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The projects, clients and tags pages load their list again when the app comes back to the
 * foreground, against the real Tokkl container: a change made on the server while the app was in
 * the background shows once it returns. A rotation loads nothing.
 *
 * The app goes to the background with the home key and comes back to the same activity, so the
 * page sees the activity stop and start without being recreated.
 */
class CatalogForegroundTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var scenario: ActivityScenario<MainActivity>? = null
    private var counter: RequestCounter? = null

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = ZONE)
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            scenario?.close()
        } finally {
            try {
                // The counter's relay replaced the stored server and its cleartext confirmation,
                // and the app's client reaches the test server only with both.
                if (::account.isInitialized && counter != null) {
                    ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                    Session(context).save(TokklTestServer.url, account.apiToken)
                }
            } finally {
                try {
                    counter?.close()
                } finally {
                    try {
                        if (::account.isInitialized) TestAccounts.delete(account)
                    } finally {
                        SessionRule.signOut()
                    }
                }
            }
        }
    }

    @Test
    fun aProjectRenamedElsewhereShowsAfterTheForegroundAndARotationLoadsNothing() {
        val id = create("projects", "Website")
        val counter = RequestCounter(TokklTestServer.url, "/workspaces/${account.workspaceId}/projects HTTP/1.1").also { this.counter = it }
        ServerTrustStore(context).confirmCleartext(counter.url)
        Session(context).save(counter.url, account.apiToken)

        launch()
        barItem(R.string.nav_projects).performClick()
        awaitText("Website")
        assertEquals("opening loads once", 1, counter.count)

        val activity = BackgroundTrip.leave()
        rename("projects", id, "Web site", api = TogglApiClient(counter.url, ApiCredentials.ApiToken(account.apiToken)))
        BackgroundTrip.comeBack(activity)

        awaitText("Web site")
        assertFalse("the old name goes", shows("Website"))
        Thread.sleep(SETTLE_MS)
        assertEquals("coming back loads once", 2, counter.count)

        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitCondition("the activity is recreated in landscape") {
            resumedActivity()?.takeIf { it !== activity && it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        }
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        awaitText("Web site")
        assertEquals("the rotation loads nothing", 2, counter.count)
        assertEquals("the relay reached the server each time", emptyList<String>(), counter.failures)
    }

    @Test
    fun aClientAndATagRenamedElsewhereShowAfterTheForeground() {
        val client = create("clients", "Acme")
        val tag = create("tags", "urgent")
        launch()

        openFromMore(R.string.nav_clients)
        awaitText("Acme")
        var activity = BackgroundTrip.leave()
        rename("clients", client, "Acme Inc")
        BackgroundTrip.comeBack(activity)
        awaitText("Acme Inc")

        openFromMore(R.string.nav_tags)
        awaitText("urgent")
        activity = BackgroundTrip.leave()
        rename("tags", tag, "later")
        BackgroundTrip.comeBack(activity)
        awaitText("later")
        assertFalse("the old name goes", shows("urgent"))
    }

    /** Launches the app on the Timer page with the account's settings read. */
    private fun launch() {
        runBlocking { app.accountSettings.refresh().join() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil("the bottom bar shows", TIMEOUT_MS) {
            compose.onAllNodes(barItemMatcher(R.string.nav_projects)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openFromMore(@StringRes page: Int) {
        barItem(R.string.nav_more).performClick()
        compose.waitUntil("the More sheet opens", TIMEOUT_MS) {
            compose.onAllNodes(hasText(string(page)) and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(string(page)) and isSelectable()).performClick()
        compose.waitUntil("the page opens", TIMEOUT_MS) {
            compose.onAllNodes(hasText(string(page)) and isHeading()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun barItem(@StringRes label: Int) = compose.onNode(barItemMatcher(label))

    private fun barItemMatcher(@StringRes label: Int): SemanticsMatcher =
        hasText(string(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun awaitText(text: String) {
        compose.waitUntil("\"$text\" shows", TIMEOUT_MS) { shows(text) }
    }

    private fun shows(text: String): Boolean = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Creates an item named [name] in the workspace's [collection] and returns its id. */
    private fun create(collection: String, name: String): Long =
        account.client.send("POST", url(collection), buildJsonObject { put("name", name) }, JsonObject.serializer())
            .orFail("POST $collection").getValue("id").jsonPrimitive.long

    /**
     * Renames an item through [api]. While the app's stored server is the counter's relay, the
     * shared HTTP client sends only there, so the rename goes through the relay too.
     */
    private fun rename(collection: String, id: Long, name: String, api: TogglApiClient = account.client) {
        api.send("PUT", url(collection, id.toString(), api = api), buildJsonObject { put("name", name) }, JsonObject.serializer())
            .orFail("PUT $collection/$id")
    }

    private fun url(vararg path: String, api: TogglApiClient = account.client) =
        api.server.api("workspaces", account.workspaceId.toString(), *path)

    private fun resumedActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return resumed
    }

    /** Polls [value] until it is not null. Works while the activity is being recreated, unlike the compose rule's waits. */
    private fun <T : Any> awaitCondition(what: String, value: () -> T?): T {
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (true) {
            value()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val ZONE = "Europe/Madrid"
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L

        /** Long enough for a request the app would send at once to reach the relay. */
        const val SETTLE_MS = 2_000L
    }
}
