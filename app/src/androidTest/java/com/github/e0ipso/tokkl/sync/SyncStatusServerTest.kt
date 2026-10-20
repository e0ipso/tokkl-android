package com.github.e0ipso.tokkl.sync

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.AirplaneMode
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.formatTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The status element above the bottom bar against the real Tokkl container (docs/PRD.md O5).
 * Offline, each change the timer queues counts in it, beside when the server last answered in the
 * account's timezone, Tokyo, which TalkBack reads as one. When the network comes back the app
 * sends the queue on its own, the server's answers move the time on, and the element goes.
 */
class SyncStatusServerTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private lateinit var account: TestAccounts.Account

    /** The same account straight on the server, past the app's guard, to tell when the network is gone or back. */
    private val direct by lazy {
        TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken), httpClient = OkHttpClient())
    }

    @Before
    fun signIn() {
        account = TestAccounts.create(timezone = ZONE.id)
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        Session(context).save(TokklTestServer.url, account.apiToken)
    }

    @After
    fun cleanUp() {
        try {
            AirplaneMode.off()
        } finally {
            try {
                // TestAccounts talks to the server through the app's guard, which follows the session.
                Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
                if (::account.isInitialized) TestAccounts.delete(account)
            } finally {
                SessionRule.signOut()
            }
        }
    }

    @Test
    fun theStatusCountsOfflineChangesAndGoesOnceTheAppHasSentThem() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
            runBlocking { app.accountSettings.refresh().join() }
            composeRule.waitUntil("Start can start", NETWORK_TIMEOUT_MS) { shows(startButton()) }
            val answered = checkNotNull(app.lastAnswer.state.value) { "the refresh's answers set no time" }
            assertTrue("the status shows with nothing queued", !shows(hasText(unsent(1))))

            AirplaneMode.set(true, direct)
            composeRule.onNode(startButton()).performClick()
            awaitStatus(unsent(1), answeredLine(answered))
            composeRule.waitUntil("Stop can stop", NETWORK_TIMEOUT_MS) { shows(stopButton()) }
            composeRule.onNode(stopButton()).performClick()
            awaitStatus(unsent(2), answeredLine(answered))
            assertEquals("the offline taps answered nothing", answered, app.lastAnswer.state.value)

            AirplaneMode.set(false, direct)
            composeRule.waitUntil("the app sends the queue and the status goes", NETWORK_TIMEOUT_MS) {
                runBlocking { app.changes.unsentCount.first() } == 0 && !shows(hasText(unsent(2))) && !shows(hasText(unsent(1)))
            }
            val later = checkNotNull(app.lastAnswer.state.value)
            assertTrue("the send's answers moved the time on from $answered to $later", later.isAfter(answered))
        }
    }

    /** One node, as TalkBack reads it, that holds [count] and [answered], and the bar's unsent icon with them. */
    private fun awaitStatus(count: String, answered: String) {
        composeRule.waitUntil("the status says \"$count\" and \"$answered\"", NETWORK_TIMEOUT_MS) {
            shows(hasText(count) and hasText(answered))
        }
    }

    /** The line for [answered] in the account's zone and 24-hour clock, today or yesterday there. */
    private fun answeredLine(answered: Instant): String {
        val time = formatTime(answered, ZONE, twelveHour = false)
        val today = LocalDate.now(ZONE)
        return if (answered.atZone(ZONE).toLocalDate() == today) {
            context.getString(R.string.sync_last_answered_today, time)
        } else {
            context.getString(R.string.sync_last_answered_yesterday, time)
        }
    }

    private fun unsent(count: Int): String = context.resources.getQuantityString(R.plurals.sync_unsent, count, count)

    private fun startButton(): SemanticsMatcher = hasContentDescription(context.getString(R.string.timer_start_spoken)) and hasClickAction()

    private fun stopButton(): SemanticsMatcher = hasContentDescription(context.getString(R.string.timer_stop_spoken)) and hasClickAction()

    private fun shows(matcher: SemanticsMatcher): Boolean = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val NETWORK_TIMEOUT_MS = 30_000L
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}
