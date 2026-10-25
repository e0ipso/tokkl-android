package com.github.e0ipso.tokkl.shortcuts

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.widget.Toast
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TapToVisibleDeadline
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.widget.NativeWidget
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The favorites' launcher shortcuts (docs/PRD.md N6) against the real Tokkl container. The test's
 * own requests stand in for the web app: they set the favorites in `/me/preferences`, and a
 * refresh brings them to the phone. Each test signs in to an account of its own and deletes it
 * after.
 */
class FavoriteShortcutsServerTest {

    @get:Rule
    val session = SessionRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val manager: ShortcutManager get() = context.getSystemService(ShortcutManager::class.java)
    private lateinit var account: TestAccounts.Account
    private var project = 0L

    /** The web app: the same account, straight to the server and past the app's guard. */
    private val web by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInToANewAccount() {
        // Android limits how often an app in the background sets its shortcuts, and these tests
        // set them more often than a day of real use does.
        DeviceShell.run("cmd shortcut reset-throttling")
        account = TestAccounts.create(timezone = "Europe/Madrid")
        project = web.send(
            "POST",
            web.server.api("workspaces", account.workspaceId.toString(), "projects"),
            buildJsonObject { put("name", "Website") },
            JsonObject.serializer(),
        ).orFail("POST a project").getValue("id").jsonPrimitive.long
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            if (::account.isInitialized) {
                ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                Session(context).save(TokklTestServer.url, account.apiToken)
                AirplaneMode.set(false, web)
                TestAccounts.delete(account)
            } else {
                AirplaneMode.off()
            }
        } finally {
            SessionRule.signOut()
            awaitShortcuts("cleanup removes the account shortcuts", emptyList())
        }
    }

    @Test
    fun theFavoritesBecomeShortcutsInOrderAndFollowEveryChange() {
        saveFavorites(favorite("Write the report", project), favorite("", project))
        awaitShortcuts("the web app's favorites", listOf("Write the report", "Website"))
        val report = published().first()
        assertEquals("Write the report, Website", report.longLabel.toString())

        saveFavorites(favorite("Write the report", project), favorite("", project), favorite("Call the client", null))
        awaitShortcuts("an added favorite", listOf("Write the report", "Website", "Call the client"))

        saveFavorites(favorite("Call the client", null), favorite("Write the report", project))
        awaitShortcuts("a removed and a moved favorite", listOf("Call the client", "Write the report"))
        assertEquals("a moved favorite keeps its shortcut", report.id, published()[1].id)

        saveFavorites()
        awaitShortcuts("no favorites", emptyList())
    }

    @Test
    fun theLauncherPublishesFavoritesUpToItsLimit() {
        val limit = manager.maxShortcutCountPerActivity
        // Tokkl accepts at most nine favorites. Truncation below that is also covered on the JVM.
        val favorites = (1..9).map { favorite("Favorite $it", null) }
        saveFavorites(*favorites.toTypedArray())

        val shown = minOf(limit, favorites.size)
        awaitShortcuts("the launcher limit", (1..shown).map { "Favorite $it" })
        assertEquals("the cache keeps every server favorite", favorites.size, runBlocking { app.cache.favorites.first() }.size)
        assertEquals((0 until shown).toList(), published().map { it.rank })
    }

    @Test
    fun losingTheFavoritesCapabilityRemovesShortcutsAndRefusesAnOldTap() {
        saveFavorites(favorite("Write the report", project))
        awaitShortcuts("the favorite", listOf("Write the report"))
        val shortcut = published().single()

        app.capabilities.forget()

        awaitShortcuts("favorites unavailable", emptyList())
        assertEquals("only the capability changed", 1, runBlocking { app.cache.favorites.first() }.size)
        awaitToast(context.getString(R.string.shortcut_gone)) {
            context.startActivity(Intent(shortcut.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        assertEquals(null, runBlocking { app.cache.running.first() })
        assertEquals(0, runBlocking { app.changes.unsentCount.first() })
    }

    @Test
    fun theShortcutUpdatesTheNativeWidgetOffline() {
        saveFavorites(favorite("Shortcut to widget", project))
        awaitShortcuts("the favorite", listOf("Shortcut to widget"))
        NativeWidget().use { widget ->
            widget.awaitDescription(context.getString(R.string.widget_start_description))
            AirplaneMode.set(true, web)
            val shortcut = published().single()
            val started = TapToVisibleDeadline("shortcut Start")
            context.startActivity(Intent(shortcut.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            started.await("shortcut start reaches the native widget") {
                widget.hasText("Shortcut to widget") && widget.hasDescription(context.getString(R.string.widget_stop_description))
            }
            assertEquals("Shortcut to widget", runBlocking { app.cache.running.first() }?.entry?.description)
            assertEquals("the offline start is queued", 1, runBlocking { app.changes.unsentCount.first() })
        }
    }

    @Test
    fun signingOutRemovesTheShortcuts() {
        saveFavorites(favorite("Write the report", project))
        awaitShortcuts("the favorite", listOf("Write the report"))

        runBlocking { app.signOut.run() }

        awaitShortcuts("after the sign-out", emptyList())
    }

    @Test
    fun aTapStartsTheFavoriteOfflineAndShowsAToastInsteadOfTheApp() {
        val task = web.send("POST", web.server.api("workspaces", "${account.workspaceId}", "projects", "$project", "tasks"),
            buildJsonObject { put("name", "Shortcut task") }, JsonObject.serializer()).orFail("create shortcut task")["id"]!!.jsonPrimitive.long
        saveFavorites(favorite("Write the report", project, task, listOf("shortcut"), true), favorite("", project))
        awaitShortcuts("the favorites", listOf("Write the report", "Website"))
        val shortcut = published().first()
        AirplaneMode.set(true, web)

        val toast = context.getString(R.string.shortcut_started, "Write the report")
        awaitToast(toast) {
            context.startActivity(Intent(shortcut.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        val running = runBlocking { app.cache.running.first() }
        assertNotNull("a timer runs on the phone", running)
        assertEquals("Write the report", running!!.entry.description)
        assertEquals(account.workspaceId, running.entry.workspaceId)
        assertEquals(project, running.entry.projectId)
        assertEquals(task, running.entry.taskId)
        assertEquals(listOf("shortcut"), running.entry.tags)
        assertTrue(running.entry.billable)
        assertTrue("the queue holds the start", runBlocking { app.changes.unsentCount.first() } > 0)
        assertEquals("no window of the app shows", emptyList<Activity>(), resumedActivities())

        AirplaneMode.set(false, web)
        drainUntilEmpty()
        val entry = serverEntries().single()
        assertEquals("Write the report", entry.getValue("description").jsonPrimitive.content)
        assertEquals(project, entry.getValue("project_id").jsonPrimitive.long)
        assertEquals(task, entry.getValue("task_id").jsonPrimitive.long)
        assertEquals(Json.parseToJsonElement("[\"shortcut\"]"), entry["tags"])
        assertEquals("true", entry.getValue("billable").jsonPrimitive.content)
    }

    @Test
    fun aShortcutWhoseFavoriteIsGoneStartsNothing() {
        saveFavorites(favorite("Write the report", project))
        awaitShortcuts("the favorite", listOf("Write the report"))
        val shortcut = published().single()
        saveFavorites()
        awaitShortcuts("no favorites", emptyList())

        awaitToast(context.getString(R.string.shortcut_gone)) {
            context.startActivity(Intent(shortcut.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        assertEquals(null, runBlocking { app.cache.running.first() })
        assertEquals(0, runBlocking { app.changes.unsentCount.first() })
    }

    @Test
    fun aLongPressOnTheAppIconListsTheFavoritesAndATapStartsOne() {
        saveFavorites(favorite("Write the report", project), favorite("", project))
        awaitShortcuts("the favorites", listOf("Write the report", "Website"))
        val device = UiDevice.getInstance(instrumentation)
        AirplaneMode.set(true, web)

        device.pressHome()
        device.waitForIdle()
        val launcher = device.currentPackageName
        println("Shortcut launcher: resolved=${device.launcherPackageName}; foreground=$launcher")
        assertNotNull("Home exposes the launcher", launcher)
        appIcon(device, launcher).longClick()
        val report = device.wait(Until.findObject(By.pkg(launcher).text(Pattern.compile("Write the report(, Website)?"))), UI_TIMEOUT.toMillis())
        assertNotNull("the long press lists the first favorite", report)
        assertNotNull("and the second", device.findObject(By.pkg(launcher).text("Website")))

        awaitToast(context.getString(R.string.shortcut_started, "Write the report")) { report.click() }

        assertEquals("Write the report", runBlocking { app.cache.running.first() }?.entry?.description)
        assertTrue("the launcher tap queues an offline start", runBlocking { app.changes.unsentCount.first() } > 0)
        assertEquals("the launcher stays in front", launcher, device.currentPackageName)
        assertEquals("no window of the app shows", emptyList<Activity>(), resumedActivities())
    }

    /** The app's icon among the launcher's apps, which open with a swipe up on the home screen. */
    private fun appIcon(device: UiDevice, launcher: String): UiObject2 {
        val name = context.getString(R.string.app_name)
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, SWIPE_STEPS)
        repeat(MAX_SCROLLS) {
            device.wait(Until.findObject(By.pkg(launcher).text(name)), SHORT_WAIT.toMillis())?.let { return it }
            device.findObject(By.pkg(launcher).scrollable(true))?.scroll(Direction.DOWN, 1f)
        }
        dumpWindows(device)
        throw AssertionError("the launcher lists no $name")
    }

    private fun favorite(description: String, project: Long?, task: Long? = null, tags: List<String> = emptyList(), billable: Boolean = false) =
        """{"description": ${Json.encodeToString(description)}, "project_id": ${project ?: "null"}, "task_id": ${task ?: "null"}, "tags": ${Json.encodeToString(tags)}, "billable": $billable}"""

    /** Sets the favorites as the web app does, then refreshes the phone. */
    private fun saveFavorites(vararg favorites: String) {
        val body = Json.parseToJsonElement("""{"favorites": [${favorites.joinToString(",")}]}""")
        web.send("POST", web.server.api("me", "preferences"), body, JsonElement.serializer()).orFail("POST /api/v9/me/preferences")
        runBlocking { app.accountSettings.refresh().join() }
    }

    private fun published(): List<ShortcutInfo> = manager.dynamicShortcuts.sortedBy { it.rank }

    /** Waits until the launcher has shortcuts with these short labels, in this order. */
    private fun awaitShortcuts(what: String, labels: List<String>) {
        val deadline = System.nanoTime() + UI_TIMEOUT.toNanos()
        while (published().map { it.shortLabel.toString() } != labels) {
            if (System.nanoTime() > deadline) {
                throw AssertionError("$what: expected $labels, the launcher has ${published().map { it.shortLabel }}")
            }
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * Checks the platform's currently displayed toast, not an accessibility window: system text
     * toasts are not exposed as UiAutomator nodes on these AOSP images. The first record is the
     * one Android displays; later records are waiting. It must belong to this app and be short.
     */
    private fun awaitToast(text: String, action: () -> Unit) {
        val finished = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val lifecycle = ActivityLifecycleCallback { activity, stage ->
            if (activity is StartFavoriteActivity && stage == Stage.DESTROYED) finished.countDown()
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(lifecycle) }
        try {
            action()
            val deadline = System.nanoTime() + UI_TIMEOUT.toNanos()
            var shown: String? = null
            while (System.nanoTime() < deadline) {
                shown = DeviceShell.run("dumpsys notification").lineSequence()
                    .firstOrNull { it.contains("TextToastRecord{") }
                if (shown?.contains(":${context.packageName}/") == true && shown.contains(" text=$text duration=${Toast.LENGTH_SHORT}}")) {
                    println("Shortcut system confirmation: $shown")
                    assertTrue("the shortcut action closes after its confirmation", finished.await(UI_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                    return
                }
                Thread.sleep(20)
            }
            dumpWindows(UiDevice.getInstance(instrumentation))
            throw AssertionError("Expected short system toast '$text'; platform toast=$shown; running=${runBlocking { app.cache.running.first() }?.entry?.description}; resumed=${resumedActivities()}")
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(lifecycle) }
        }
    }

    private fun dumpWindows(device: UiDevice) {
        val output = ByteArrayOutputStream()
        device.dumpWindowHierarchy(output)
        println("Shortcut diagnostic windows: ${output.toString(Charsets.UTF_8.name())}")
    }

    private fun resumedActivities(): List<Activity> {
        var resumed = emptyList<Activity>()
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList()
        }
        return resumed
    }

    private fun drainUntilEmpty() {
        val deadline = System.nanoTime() + NETWORK_TIMEOUT.toNanos()
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            if (System.nanoTime() > deadline) throw AssertionError("the queue is not empty")
            runBlocking { app.sender.send().join() }
        }
    }

    private fun serverEntries(): List<JsonObject> =
        web.get(web.server.api("me", "time_entries"), JsonArray.serializer()).orFail("GET /api/v9/me/time_entries")
            .map { it.jsonObject }

    private companion object {
        val UI_TIMEOUT: Duration = Duration.ofSeconds(10)
        val SHORT_WAIT: Duration = Duration.ofSeconds(2)
        val NETWORK_TIMEOUT: Duration = Duration.ofSeconds(60)
        const val POLL_MS = 100L
        const val SWIPE_STEPS = 20
        const val MAX_SCROLLS = 5
    }
}
