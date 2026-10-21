package com.github.e0ipso.tokkl.widget

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.e0ipso.tokkl.HangingRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.RequestCounter
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.notification.RunningNotifications
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.tile.QuickSettingsTile
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Real-server fixtures and native widget controls on API 31 and 36. No fake server or periodic widget work. */
class WidgetServerTest {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var workspace = 0L
    private var widget: NativeWidget? = null
    private val native get() = checkNotNull(widget)
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun prepareAccountAndWidget() {
        account = TestAccounts.create(timezone = "Europe/Madrid")
        workspace = account.workspaceId
        signIn(TokklTestServer.url, account.apiToken)
        runBlocking { app.accountSettings.refresh().join() }
        RunningNotifications.allow()
        widget = NativeWidget()
        native.awaitText(context.getString(R.string.widget_idle))
        native.awaitDescription(context.getString(R.string.widget_start_description))
    }

    @After
    fun cleanUp() {
        try {
            QuickSettingsTile.collapse()
            QuickSettingsTile.remove()
            widget?.close()
        } finally {
            try {
                assertTrue("sign-out clears the account and queue", runBlocking { app.signOut.run() })
                signIn(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun startAndStopWorkOfflineThroughTheNativeControls() {
        signIn(NO_ROUTE, account.apiToken)
        val queued = unsentCount()
        native.click(context.getString(R.string.widget_start_description))
        val entry = runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first().entry } }
        native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
        assertEquals("the app's default workspace", workspace, entry.workspaceId)
        assertEquals("a blank Start has the app's empty fields", "", entry.description)
        assertEquals(queued + 1, unsentCount())

        native.click(context.getString(R.string.widget_stop_description))
        runBlocking { withTimeout(1_000) { app.cache.running.first { it == null } } }
        native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        assertEquals(queued + 2, unsentCount())
        assertNotNull("the local entry was stopped", runBlocking { app.cache.timeEntries.first() }.single { it.id == entry.id }.stop)
    }

    @Test
    fun favoritesResizeAndEachNativeTapQueuesAllItsFieldsOffline() {
        val project = web.send("POST", web.server.api("workspaces", "$workspace", "projects"),
            buildJsonObject { put("name", "Widget project"); put("billable", true) }, JsonObject.serializer()).orFail("create project")["id"]!!.jsonPrimitive.long
        val task = web.send("POST", web.server.api("workspaces", "$workspace", "projects", "$project", "tasks"),
            buildJsonObject { put("name", "Widget task") }, JsonObject.serializer()).orFail("create task")["id"]!!.jsonPrimitive.long
        val favorites = (1..3).map { index ->
            Json.parseToJsonElement("""{"description":"Favorite $index","project_id":$project,"task_id":$task,"tags":["widget"],"billable":true}""")
        }
        web.sendNoContent("POST", web.server.api("me", "preferences"), buildJsonObject {
            put("favorites", kotlinx.serialization.json.JsonArray(favorites))
        }).orFail("save favorites")
        runBlocking { app.accountSettings.refresh().join() }
        native.awaitText("Favorite 3")
        native.awaitText("Widget project · Widget task")

        native.resize(360, 152)
        native.await("the compact widget fits only its first favorite") { native.hasText("Favorite 1") && !native.hasText("Favorite 2") }
        native.resize(360, 320)
        native.awaitText("Favorite 3")
        signIn(NO_ROUTE, account.apiToken)
        for (index in 1..3) {
            val queued = unsentCount()
            native.click("Start Favorite $index, Widget project · Widget task")
            val running = runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first { it.entry.description == "Favorite $index" }.entry } }
            native.awaitText("Favorite $index", 1_000)
            assertEquals(project, running.projectId)
            assertEquals(task, running.taskId)
            assertEquals(listOf("widget"), running.tags)
            assertTrue(running.billable)
            assertTrue("the favorite uses the offline queue", unsentCount() > queued)
        }
        app.capabilities.forget()
        native.await("a server without favorites hides every favorite") { (1..3).none { native.hasDescription("Start Favorite $it, Widget project · Widget task") } }
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        assertTrue("the cache still holds favorites, only the capability hid them", runBlocking { app.cache.favorites.first() }.isNotEmpty())
        assertTrue(runBlocking { app.signOut.run() })
        native.awaitDescription(context.getString(R.string.widget_sign_in))
        assertFalse(native.hasText("Favorite 3"))
    }

    @Test
    fun nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod() {
        assertEquals(0, native.provider.updatePeriodMillis)
        assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL, native.provider.resizeMode)
        assertTrue(native.provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0)
        signIn(NO_ROUTE, account.apiToken)
        runBlocking { app.changes.start(workspace, EntryFields("A ticking widget")) }
        native.awaitText("A ticking widget")
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        native.assertClockTicksWithoutUpdates()
    }

    @Test
    fun theWidgetFollowsTheAppNotificationAndTileWithinASecond() {
        signIn(NO_ROUTE, account.apiToken)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTimerPage()
            compose.onNodeWithContentDescription(context.getString(R.string.timer_start_spoken)).performClick()
            native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
            RunningNotifications.awaitShown("the app start")
            RunningNotifications.shown()!!.notification.actions.single().actionIntent.send()
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)

            compose.onNodeWithContentDescription(context.getString(R.string.timer_start_spoken)).performClick()
            native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
            compose.onNodeWithContentDescription(context.getString(R.string.timer_stop_spoken)).performClick()
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        }
        runBlocking { app.changes.start(workspace, EntryFields("Tile to widget")) }
        native.awaitText("Tile to widget")
        QuickSettingsTile.add()
        QuickSettingsTile.expand()
        QuickSettingsTile.awaitText("tile ready", "Tile to widget")
        QuickSettingsTile.click()
        native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        QuickSettingsTile.awaitText("stopped", context.getString(R.string.tile_continue))
        QuickSettingsTile.click()
        native.awaitText("Tile to widget", 1_000)
        native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
    }

    @Test
    fun widgetStartReachesTheRealServerAndStopSendsAnExistingLongEntry() {
        native.click(context.getString(R.string.widget_start_description))
        // The broadcast runs asynchronously. Wait for its commit before asking the sender to drain.
        runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first() } }
        drain()
        val current = web.get(web.server.api("me", "time_entries", "current"), JsonObject.serializer()).orFail("read widget start")
        assertEquals("", current["description"]!!.jsonPrimitive.content)
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        // Use an entry old enough for the existing Stop API. Exact captured short Stop is TKA-0259.
        web.send("POST", web.server.api("workspaces", "$workspace", "time_entries"), buildJsonObject {
            put("workspace_id", workspace); put("created_with", "tokkl-android tests")
            put("description", "Long entry stopped by widget")
            put("start", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS).toString()); put("duration", -1)
        }, JsonObject.serializer()).orFail("seed long entry")
        runBlocking { app.accountSettings.refresh().join() }
        val id = runBlocking { app.cache.running.filterNotNull().first().entry.id }
        native.awaitText("Long entry stopped by widget")
        native.click(context.getString(R.string.widget_stop_description))
        native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
        drain()
        val entries = web.get(web.server.api("me", "time_entries"), ListSerializer(JsonObject.serializer())).orFail("read stopped entry")
        val stopped = entries.single { it["id"]!!.jsonPrimitive.long == id }
        assertTrue("the real server stored Stop", stopped["stop"] != null && stopped["stop"] != JsonNull)
    }

    @Test
    fun remoteStopClearsTheNativeWidgetAndNotificationAtThePeriodicCheck() {
        val id = account.createEntry("Stopped remotely", Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.SECONDS))
        runBlocking { app.accountSettings.refresh().join() }
        native.awaitText("Stopped remotely")
        native.awaitDescription(context.getString(R.string.widget_stop_description))
        RunningNotifications.awaitShown("the seeded running entry")
        val workManager = WorkManager.getInstance(context)
        val check = runBlocking {
            withTimeout(60_000) {
                workManager.getWorkInfosForUniqueWorkFlow(RunningCheck.WORK_NAME).filterNotNull()
                    .first { it.singleOrNull()?.state == WorkInfo.State.ENQUEUED }.single()
            }
        }
        web.sendNoContent("PATCH", web.server.api("workspaces", "$workspace", "time_entries", "$id", "stop"), buildJsonObject {}).orFail("remote stop")
        assertNotNull("the phone has not refreshed", runBlocking { app.cache.running.first() })
        RequestCounter(TokklTestServer.url, "/api/v9/me/time_entries/current HTTP/1.1").use { requests ->
            signIn(requests.url, account.apiToken)
            val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
            driver.setInitialDelayMet(check.id)
            driver.setAllConstraintsMet(check.id)
            runBlocking { withTimeout(60_000) { app.cache.running.first { it == null } } }
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
            assertFalse(native.hasText("Stopped remotely"))
            RunningNotifications.awaitGone("the remotely stopped timer")
            assertEquals(1, requests.count)
            assertTrue(requests.failures.isEmpty())
            assertEquals(0, unsentCount())
        }
    }

    @Test
    fun widgetActionsDoNotWaitForANetworkThatNeverAnswers() {
        HangingRelay(TokklTestServer.url).use { relay ->
            signIn(relay.url, account.apiToken)
            relay.hang()
            native.click(context.getString(R.string.widget_start_description))
            runBlocking { withTimeout(1_000) { app.cache.running.filterNotNull().first() } }
            native.awaitDescription(context.getString(R.string.widget_stop_description), 1_000)
            native.click(context.getString(R.string.widget_stop_description))
            runBlocking { withTimeout(1_000) { app.cache.running.first { it == null } } }
            native.awaitDescription(context.getString(R.string.widget_start_description), 1_000)
            assertEquals(2, unsentCount())
        }
    }

    private fun drain() {
        runBlocking { app.sender.sendAndWait(); app.accountSettings.refresh().join() }
        assertEquals("every queued change reached the server", 0, unsentCount())
    }

    private fun unsentCount() = runBlocking { app.changes.unsentCount.first() }

    private fun signIn(server: ServerUrl, token: String) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, token)
    }

    private companion object {
        val NO_ROUTE = checkNotNull(ServerUrl.parse("http://127.0.0.1:9"))
    }
}
