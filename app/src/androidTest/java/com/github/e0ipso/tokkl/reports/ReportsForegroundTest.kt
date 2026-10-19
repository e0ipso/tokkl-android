package com.github.e0ipso.tokkl.reports

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A report loaded before the app went to the background is asked for again when the app comes
 * back (docs/PRD.md R5), against the real Tokkl container. Offline, the screen then says that
 * reports need the server and shows none of the old numbers. A rotation asks nothing.
 *
 * The app goes to the background with the home key, as a user leaves it, and comes back to the
 * same activity, so the screen sees the activity stop and start without being recreated.
 */
class ReportsForegroundTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var scenario: ActivityScenario<MainActivity>? = null
    private var relay: ReportSearchCounter? = null

    @Before
    fun signInToANewAccountWithTimeThisWeek() {
        account = TestAccounts.create(timezone = ZONE)
        seedTimeToday(account.client)
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            scenario?.close()
        } finally {
            try {
                // The relay replaced the stored server and its cleartext confirmation, and the
                // app's client reaches the test server, where the account is deleted, only with both.
                if (::account.isInitialized) {
                    ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                    Session(context).save(TokklTestServer.url, account.apiToken)
                    DeviceShell.run("cmd connectivity airplane-mode disable")
                    awaitServer(answers = true)
                }
            } finally {
                try {
                    relay?.close()
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
    fun offlineAfterTheBackgroundShowsTheMessageAndNoNumbers() {
        val model = openReports()
        val loaded = awaitLoaded(model, after = null)
        val report = loaded.report
        val total = settings().formatDuration(report.totals.seconds)
        assertTrue("the seeded time shows: $report", report.totals.seconds > 0)
        assertTrue("the total $total shows", hasNode(total))

        val activity = sendToBackground()
        DeviceShell.run("cmd connectivity airplane-mode enable")
        awaitServer(answers = false)
        bringBack(activity)

        val offline = runCatching {
            compose.waitUntil("the offline message shows", LOAD_TIMEOUT_MS) { hasNode(string(R.string.reports_offline_title)) }
        }
        assertTrue("the offline message shows; state ${model.state.value}, old total shows: ${hasNode(total)}", offline.isSuccess)
        assertTrue("the offline explanation shows", hasNode(string(R.string.reports_offline_message)))
        val state = model.state.value
        assertEquals("failed for want of the server", ReportsState.Failed(loaded.selection, ReportFailure.Unreachable), state)
        assertFalse("no total $total offline", hasNode(total))
        assertFalse("no totals offline", hasNode(string(R.string.reports_total_time).uppercase()))
    }

    @Test
    fun theForegroundAsksOnceAndARotationAsksNothing() {
        val relay = ReportSearchCounter(TokklTestServer.url).also { this.relay = it }
        ServerTrustStore(context).confirmCleartext(relay.url)
        Session(context).save(relay.url, account.apiToken)

        val model = openReports()
        val first = awaitLoaded(model, after = null)
        assertEquals("opening asks once", 1, relay.searches)

        bringBack(sendToBackground())
        val second = awaitLoaded(model, after = first)
        assertEquals("the same range again", first.selection, second.selection)
        Thread.sleep(SETTLE_MS)
        assertEquals("coming back asks once", 2, relay.searches)

        val portrait = resumedActivity()!!
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        val landscape = awaitCondition("the activity is recreated in landscape") {
            resumedActivity()?.takeIf { it !== portrait && it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        }
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        assertSame("the rotation keeps the model", model, modelOf(landscape))
        assertSame("the rotation keeps the report", second, model.state.value)
        assertEquals("the rotation asks nothing", 2, relay.searches)
        assertEquals("the relay reached the server each time", emptyList<String>(), relay.failures)
    }

    /** Launches the app on the Timer page, opens the reports and returns the screen's model. */
    private fun openReports(): ReportsModel {
        runBlocking { app.accountSettings.refresh().join() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil("the Timer page shows", LOAD_TIMEOUT_MS) { hasNode(string(R.string.reports)) }
        compose.onNode(hasText(string(R.string.reports)) and hasClickAction()).performClick()
        return modelOf(resumedActivity()!!)
    }

    /**
     * Waits for a report other than [after] and returns it. The compose rule's wait lets the
     * screen recompose, which a plain sleep does not.
     */
    private fun awaitLoaded(model: ReportsModel, after: ReportsState.Loaded?): ReportsState.Loaded {
        compose.waitUntil("a report loads", LOAD_TIMEOUT_MS) {
            when (val state = model.state.value) {
                is ReportsState.Loaded -> state !== after
                is ReportsState.Failed, is ReportsState.NoAccountTime -> true
                ReportsState.Closed, is ReportsState.Loading -> false
            }
        }
        compose.waitForIdle()
        val state = model.state.value
        assertTrue("a report loads: $state", state is ReportsState.Loaded && state !== after)
        return state as ReportsState.Loaded
    }

    /** Presses the home key and waits until the activity has stopped. Returns that activity. */
    private fun sendToBackground(): MainActivity {
        val activity = resumedActivity()!!
        DeviceShell.run("input keyevent KEYCODE_HOME")
        awaitCondition("the app is in the background") { activity.takeIf { inStage(it, Stage.STOPPED) } }
        return activity
    }

    /** Brings [activity]'s task to the front as it was, and checks that the same activity came back. */
    private fun bringBack(activity: MainActivity) {
        val component = "${context.packageName}/${MainActivity::class.java.name}"
        DeviceShell.run("am start -W -n $component -f $NEW_TASK_REORDER_TO_FRONT")
        val back = awaitCondition("the app is in the foreground") { resumedActivity() }
        assertSame("the same activity came back, not a new one", activity, back)
    }

    private fun modelOf(activity: MainActivity): ReportsModel {
        var model: ReportsModel? = null
        instrumentation.runOnMainSync { model = ViewModelProvider(activity)[ReportsViewModel::class.java].model }
        return model!!
    }

    private fun resumedActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return resumed
    }

    private fun inStage(activity: MainActivity, stage: Stage): Boolean {
        var found = false
        instrumentation.runOnMainSync { found = activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage) }
        return found
    }

    private fun hasNode(text: String): Boolean = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** Polls [value] until it is not null. Works while the app is in the background, unlike the compose rule's waits. */
    private fun <T : Any> awaitCondition(what: String, value: () -> T?): T {
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (true) {
            value()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    /** Waits until requests to the test server get through, or fail for want of a network. */
    private fun awaitServer(answers: Boolean) {
        awaitCondition("the server ${if (answers) "answers" else "is out of reach"}") {
            val result = account.client.get(account.client.server.api("me"), JsonObject.serializer())
            ((result as? ApiResult.Failure)?.error !is ApiError.Network).takeIf { it == answers }
        }
    }

    private fun settings() = app.accountSettings.state.value ?: throw AssertionError("no account settings")

    private fun string(id: Int): String = context.getString(id)

    /** Puts 25 minutes at the start of today in the account's timezone, which is always in this week. */
    private fun seedTimeToday(api: TogglApiClient) {
        val workspace = api.get(api.server.api("me"), Me.serializer()).orFail("GET /api/v9/me").defaultWorkspaceId
        val body = buildJsonObject {
            put("workspace_id", workspace)
            put("created_with", "tokkl-android tests")
            put("description", "Reports")
            put("start", LocalDate.now(ZoneId.of(ZONE)).atStartOfDay(ZoneId.of(ZONE)).toInstant().toString())
            put("duration", SEEDED_SECONDS)
        }
        api.send("POST", api.server.api("workspaces", workspace.toString(), "time_entries"), body, JsonObject.serializer())
            .orFail("POST a time entry")
    }

    @Serializable
    private class Me(@SerialName("default_workspace_id") val defaultWorkspaceId: Long)

    private companion object {
        const val ZONE = "Europe/Madrid"
        const val SEEDED_SECONDS = 1500
        const val LOAD_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L

        /** Long enough for a request the app would send at once to reach the relay. */
        const val SETTLE_MS = 2_000L

        /** FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_REORDER_TO_FRONT: the existing task and activity, as they were. */
        const val NEW_TASK_REORDER_TO_FRONT = "0x10020000"
    }
}

/**
 * A plain TCP relay on the phone's loopback to [server] that counts the report searches the app
 * sends through it. Each search is one `POST .../search/time_entries`, and no other request or
 * body holds [SEARCH], so counting it in the stream needs no HTTP parsing.
 */
private class ReportSearchCounter(server: ServerUrl) : Closeable {

    private val target = server.toString().toHttpUrl()
    // The address the URL names. getLoopbackAddress() may be ::1 instead.
    private val listener = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val count = AtomicInteger()

    /** Connections the relay could not pass on, which would make [searches] wrong. */
    val failures = CopyOnWriteArrayList<String>()

    val url: ServerUrl = ServerUrl.parse("http://127.0.0.1:${listener.localPort}") ?: error("not a URL")

    val searches: Int get() = count.get()

    init {
        thread(isDaemon = true, name = "report-search-relay") {
            while (true) {
                val app = try {
                    listener.accept()
                } catch (e: IOException) {
                    return@thread
                }
                val tokkl = try {
                    Socket(target.host, target.port)
                } catch (e: IOException) {
                    failures += "connect: $e"
                    app.close()
                    continue
                }
                sockets += app
                sockets += tokkl
                thread(isDaemon = true) { pipe(app.getInputStream(), tokkl.getOutputStream(), counting = true, app, tokkl) }
                thread(isDaemon = true) { pipe(tokkl.getInputStream(), app.getOutputStream(), counting = false, app, tokkl) }
            }
        }
    }

    /** Copies [input] to [output] until either side closes, counting [SEARCH] in requests. */
    private fun pipe(input: InputStream, output: OutputStream, counting: Boolean, vararg ends: Socket) {
        val buffer = ByteArray(8192)
        // The end of the previous read, so a search split across two reads is counted once.
        var tail = ByteArray(0)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                output.flush()
                if (counting) {
                    val seen = tail + buffer.copyOf(read)
                    count.addAndGet(occurrences(seen))
                    tail = seen.copyOfRange(maxOf(0, seen.size - SEARCH.size + 1), seen.size)
                }
            }
        } catch (e: IOException) {
            // A side closed its connection.
        } finally {
            ends.forEach { it.close() }
        }
    }

    private fun occurrences(bytes: ByteArray): Int =
        (0..bytes.size - SEARCH.size).count { start -> SEARCH.indices.all { bytes[start + it] == SEARCH[it] } }

    override fun close() {
        listener.close()
        sockets.forEach { it.close() }
    }

    private companion object {
        val SEARCH = "/search/time_entries HTTP/1.1".toByteArray(Charsets.US_ASCII)
    }
}
