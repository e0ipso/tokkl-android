package com.github.e0ipso.tokkl.history

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.HangingRelay
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
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The history on the Timer page against the real Tokkl container, in the real activity (docs/PRD.md
 * H1). Each test signs in to an account of its own, in Madrid. The phone keeps the last two
 * weeks, and older days load from the server, 14 at a time, once the end of the list shows.
 */
class HistoryServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private val zone = ZoneId.of(ZONE)
    private lateinit var account: TestAccounts.Account
    private var relay: HangingRelay? = null

    /**
     * The account's client with an OkHttp client of its own, which reaches the server while the
     * app's configured server is a relay, as the app's shared client would not.
     */
    private val direct by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInToANewAccount() {
        account = TestAccounts.create(timezone = ZONE)
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            relay?.close()
            AirplaneMode.set(false, direct)
        } finally {
            try {
                // The admin's client sends only to the configured server, and plain http only once confirmed.
                signIn(TokklTestServer.url)
                TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    /**
     * Stopped entries show the times the server stored, snapped to its resolution, under the day
     * of their start with the day's total, and the running entry stays out. Older days load only
     * once the end of the list shows, a page at a time, until the account's first day.
     */
    @Test
    fun serverTimesShowAsStoredAndOlderDaysLoadOnScroll() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        // A start the server snaps.
        val sentStart = now.minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).plusSeconds(451)
        val snapped = account.createEntry("Snapped by the server", sentStart, sentStart.plusSeconds(3125))
        account.createEntry("Running now", now.minus(10, ChronoUnit.MINUTES))
        // An entry on each of 11 earlier days, so the end of the list starts off screen. None is
        // on the day before, where the snapped entry falls when the test runs soon after midnight.
        for (days in 2L..12L) {
            val start = now.minus(days, ChronoUnit.DAYS)
            account.createEntry("Filler $days", start, start.plusSeconds(1800))
        }
        val twenty = now.minus(20, ChronoUnit.DAYS).let { account.createEntry("Twenty days ago", it, it.plusSeconds(3600)) }
        val forty = now.minus(40, ChronoUnit.DAYS).let { account.createEntry("Forty days ago", it, it.plusSeconds(5400)) }
        refresh()
        val stored = account.entry(snapped)
        val start = stored.instant("start")
        val stop = stored.instant("stop")
        assertNotEquals("the server snapped the start", sentStart, start)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            val settings = app.accountSettings.state.value!!
            awaitText(context.getString(R.string.history_time_range, settings.formatTime(start, zone), settings.formatTime(stop, zone)))
            awaitText(settings.formatDuration(stop.epochSecond - start.epochSecond))
            // The timer above the list shows the running entry, and the list leaves it out.
            composeRule.waitUntil("the timer shows the running entry", LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText("Running now") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("the running entry stays out of the list", !inList("Running now"))
            assertTrue("no older day before the end shows", !exists("Twenty days ago"))

            list().performScrollToKey(OLDER_KEY)
            awaitKey("entry-$twenty")
            awaitText("Twenty days ago")

            list().performScrollToKey(OLDER_KEY)
            awaitKey("entry-$forty")
            awaitText("Forty days ago")

            list().performScrollToKey(OLDER_KEY)
            awaitText(context.getString(R.string.history_no_older))
        }
    }

    /**
     * When the server cannot be reached while the phone keeps its network, the older days say
     * that they need the server, and Try again loads them once it can be reached. The network
     * never changes, so only the tap loads them.
     */
    @Test
    fun unreachableOlderDaysNeedTheServerAndTryAgainLoadsThem() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        account.createEntry("Recent", now.minus(1, ChronoUnit.HOURS), now.minus(50, ChronoUnit.MINUTES))
        val older = now.minus(20, ChronoUnit.DAYS).let { account.createEntry("Older offline", it, it.plusSeconds(600)) }
        val relay = HangingRelay(TokklTestServer.url).also { relay = it }
        signIn(relay.url)
        refresh()
        // Before the launch, since the list's end shows at once and starts loading older days.
        relay.hang()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            awaitText("Recent")
            list().performScrollToKey(OLDER_KEY)
            awaitText(context.getString(R.string.history_loading_older))
            // The hung request fails as a network failure would, and the relay reaches the server again.
            relay.release()
            awaitText(context.getString(R.string.history_offline_title))

            composeRule.onNode(hasText(string(R.string.history_retry)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

            awaitKey("entry-$older")
            awaitText("Older offline")
        }
    }

    /**
     * Offline, the older days say that they need the server, and they load on their own once the
     * network comes back with the app on screen, with no tap on Try again.
     */
    @Test
    fun offlineOlderDaysLoadOnceTheNetworkComesBack() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        account.createEntry("Recent", now.minus(1, ChronoUnit.HOURS), now.minus(50, ChronoUnit.MINUTES))
        val older = now.minus(20, ChronoUnit.DAYS).let { account.createEntry("Older on reconnect", it, it.plusSeconds(600)) }
        refresh()
        AirplaneMode.set(true, direct)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            awaitText("Recent")
            list().performScrollToKey(OLDER_KEY)
            awaitText(context.getString(R.string.history_offline_title))

            AirplaneMode.set(false, direct)

            awaitKey("entry-$older")
            awaitText("Older on reconnect")
        }
    }

    /**
     * An entry the server has not received shows the times the server will store, snapped on the
     * phone, marked as not sent. Once sent, the server keeps those times and the mark goes.
     */
    @Test
    fun anUnsentEntryShowsTheTimesTheServerWillStore() {
        val workspace = account.workspaceId
        val relay = HangingRelay(TokklTestServer.url).also { relay = it }
        signIn(relay.url)
        refresh()
        relay.hang()
        val start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).plusMillis(451_500)
        val stop = start.plusMillis(3_125_250)
        val id = runBlocking { app.changes.create(workspace, EntryFields(description = "Logged offline"), start, stop) }
        val settings = app.accountSettings.state.value!!
        val snapped = settings.time()!!.snapUnsent(start, stop)
        val range = context.getString(R.string.history_time_range, settings.formatTime(snapped.start, zone), settings.formatTime(snapped.stop, zone))
        val unsent = string(R.string.history_unsent).uppercase(Locale.ROOT)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(LOAD_TIMEOUT_MS)
            awaitText(range)
            awaitText(unsent)

            relay.release()
            sendTheQueue()

            val stored = direct.get(direct.server.api("me", "time_entries", runBlocking { app.changes.currentId(id) }.toString()), JsonObject.serializer())
                .orFail("GET /api/v9/me/time_entries/<id>")
            assertEquals(snapped.start, stored.instant("start"))
            assertEquals(snapped.stop, stored.instant("stop"))
            composeRule.waitUntil("the mark goes", LOAD_TIMEOUT_MS) { !exists(unsent) }
            awaitText(range)
        }
    }

    /**
     * Tokkl writes times with an offset, such as +00:00. Instant.parse refused that on API 31 and
     * took only a Z, so the offset goes through OffsetDateTime, as in the app's ServerReader.
     */
    private fun JsonObject.instant(key: String): Instant = OffsetDateTime.parse(getValue(key).jsonPrimitive.content).toInstant()

    private fun refresh() = runBlocking { app.accountSettings.refresh().join() }

    /** Sends the queue until it is empty, as the next refresh would. */
    private fun sendTheQueue() {
        val deadline = System.nanoTime() + LOAD_TIMEOUT_MS * 1_000_000
        while (runBlocking { app.changes.unsentCount.first() } > 0) {
            check(System.nanoTime() < deadline) { "the queue did not empty" }
            runBlocking { app.sender.send().join() }
        }
    }

    private fun signIn(server: ServerUrl) {
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, account.apiToken)
    }

    private fun list() = composeRule.onNode(hasScrollToKeyAction())

    private fun exists(text: String) = composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun inList(text: String) =
        composeRule.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollToKeyAction()), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitText(text: String) = composeRule.waitUntil("\"$text\" shows", LOAD_TIMEOUT_MS) { exists(text) }

    /** Waits until the list has an item with [key], and scrolls to it. */
    private fun awaitKey(key: String) = composeRule.waitUntil("the list has $key", LOAD_TIMEOUT_MS) {
        try {
            list().performScrollToKey(key)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val ZONE = "Europe/Madrid"
        const val LOAD_TIMEOUT_MS = 30_000L

        /** The key of the list's row after the days, HistoryList's OLDER_KEY. */
        const val OLDER_KEY = "older"
    }
}
