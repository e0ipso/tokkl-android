package com.github.e0ipso.tokkl.session

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.AirplaneMode
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
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.followNoTimerEvents
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The app reads the server on events only (docs/PRD.md O2): once when it opens, once each time it
 * comes back to the foreground and once each time the network comes back while it is on screen,
 * and nothing while it sits idle, against the real Tokkl container. A rotation reads nothing.
 *
 * Two [RequestCounter]s sit in front of the server, one inside the other: the outer one counts
 * every request the app sends, the inner one the refresh's `GET /api/v9/me`.
 */
class ForegroundRefreshServerTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account
    private var scenario: ActivityScenario<MainActivity>? = null
    private var all: RequestCounter? = null
    private var me: RequestCounter? = null

    /** The account straight on the server, past the app's guard and the counters. */
    private val direct by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signInThroughTheCounters() {
        // A prior airplane-mode test can return on cellular before Wi-Fi takes over.
        // That handoff is a refresh/reconnect event, so it must precede the idle measurement.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        awaitCondition("the emulator has validated Wi-Fi as its default route") {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.takeIf {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        account = TestAccounts.create(timezone = "Europe/Madrid")
        val all = RequestCounter(TokklTestServer.url, " HTTP/1.1\r\n").also { this.all = it }
        val me = RequestCounter(all.url, "GET /api/v9/me?with_related_data=true HTTP/1.1").also { this.me = it }
        ServerTrustStore(context).confirmCleartext(me.url)
        Session(context).save(me.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
        } finally {
            try {
                scenario?.close()
            } finally {
                try {
                    // TestAccounts talks to the server through the app's guard, which follows the session.
                    if (::account.isInitialized) {
                        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                        Session(context).save(TokklTestServer.url, account.apiToken)
                    }
                } finally {
                    try {
                        me?.close()
                        all?.close()
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
    }

    @Test
    fun theAppReadsTheServerOnOpenForegroundAndReconnectOnly() {
        val all = checkNotNull(all)
        val me = checkNotNull(me)
        // The settings the Timer page needs, read before the app opens, as after a sign-in.
        runBlocking { app.accountSettings.refresh().join() }
        val beforeOpen = me.count

        launch()
        awaitCount(me, beforeOpen + 1, "opening refreshes")
        settle()
        assertEquals("opening refreshes once", beforeOpen + 1, me.count)

        val idleFrom = all.count
        Thread.sleep(IDLE_MS)
        assertEquals("an idle Timer page with no timer sends nothing; requests: ${all.lines}", idleFrom, all.count)

        val activity = BackgroundTrip.leave()
        settle()
        assertEquals("the background sends nothing", idleFrom, all.count)
        BackgroundTrip.comeBack(activity)
        awaitCount(me, beforeOpen + 2, "coming back refreshes")
        settle()
        assertEquals("coming back refreshes once", beforeOpen + 2, me.count)

        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitCondition("the activity is recreated in landscape") {
            resumedActivity()?.takeIf { it !== activity && it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        }
        settle()
        assertEquals("a rotation refreshes nothing", beforeOpen + 2, me.count)

        // Offline, the timer event stream tries GET /api/app/timer/events again after 1 s, 2 s and
        // so on (docs/PRD.md section 6.3), and the outer relay cannot pass those on. Those are the
        // stream's requests, which TimerSyncLifecycleTest covers. This test counts the refreshes,
        // so it goes offline without the stream. The network's return refreshes and starts it again.
        app.followNoTimerEvents()
        val offlineFrom = all.count
        AirplaneMode.set(true, direct)
        settle()
        assertEquals("going offline sends nothing", offlineFrom, all.count)
        AirplaneMode.set(false, direct)
        awaitCount(me, beforeOpen + 3, "the network's return refreshes")
        settle()
        assertEquals("the network's return refreshes once", beforeOpen + 3, me.count)
        assertEquals("the relays reached the server each time", emptyList<String>(), all.failures + me.failures)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil("the bottom bar shows", TIMEOUT_MS) {
            compose.onAllNodes(bottomBarItem(R.string.nav_timer)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitCount(counter: RequestCounter, count: Int, what: String) {
        awaitCondition("$what: ${counter.count} of $count") { Unit.takeIf { counter.count >= count } }
    }

    private fun settle() = Thread.sleep(SETTLE_MS)

    private fun resumedActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return resumed
    }

    /** Polls [value] until it is not null. Works while the app is in the background, unlike the compose rule's waits. */
    private fun <T : Any> awaitCondition(what: String, value: () -> T?): T {
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (true) {
            value()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("timed out: $what")
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L

        /** Long enough for a request the app would send at once to reach the relay. */
        const val SETTLE_MS = 2_000L

        /**
         * Longer than every timer in the app's foreground: the running clock's second, the
         * calendar's minute and the web app's 30-second poll that the app must not copy. A
         * repeating request on any of them would show at least once in this window.
         */
        const val IDLE_MS = 65_000L
    }
}
