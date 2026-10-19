package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlin.math.abs
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tokkl's own 429 through the sign-in screen. Tokkl counts failed passwords
 * per account and address, per account and per address, within 15 minutes.
 * The test invites a throwaway account through [TestAccounts] and sends it
 * wrong passwords until Tokkl refuses them, so the shared test account is
 * never charged. The account and address counter stops at 10 failures. Every
 * test in the run reaches Tokkl from one address, whose counter stops at 100,
 * so the test sends at most [MAX_ATTEMPTS] and the rest of the run keeps 90.
 */
class SignInRateLimitTest {

    /** Signed in, as [TestAccounts] needs. The test signs out before the activity starts. */
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = TokklTestServer.url
    private lateinit var account: TestAccounts.Account

    @Before
    fun inviteAccountAndSignOut() {
        account = TestAccounts.create("UTC")
        SessionRule.signOut()
        // Spares every attempt the http warning, which SignInScreenTest covers.
        ServerTrustStore(context).confirmCleartext(server)
    }

    /**
     * Every other test signs in to the same origin, so its cooldown goes.
     * [TestAccounts] reaches only the stored server, so the test server is
     * stored again before the account is deleted.
     */
    @After
    fun forgetCooldownAndDeleteAccount() {
        SignInCooldownStore(context).clear(server.origin)
        if (!::account.isInitialized) return
        ServerTrustStore(context).confirmCleartext(server)
        Session(context).save(server, TokklTestServer.apiToken)
        TestAccounts.delete(account)
    }

    /**
     * The countdown starts from the Retry-After Tokkl sent, about 15 minutes,
     * not from the app's 60-second fallback. A new activity has a new model,
     * which reads the deadline from [SignInCooldownStore] (TKA-0103).
     */
    @Test
    fun tokklsRetryAfterDrivesACountdownThatOutlivesTheActivity() {
        val shown: Long
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitText(string(R.string.sign_in_server_url))
            field(R.string.sign_in_server_url).performTextInput(server.toString())
            composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable()).performClick()
            field(R.string.sign_in_email).performTextInput(account.email)
            field(R.string.sign_in_password).performTextInput(WRONG_PASSWORD)

            var attempts = 0
            while (!exists(countdown)) {
                assertTrue("Tokkl answered 429 within $MAX_ATTEMPTS wrong passwords", attempts < MAX_ATTEMPTS)
                submitButton().performScrollTo().performClick()
                attempts++
                // The click cleared the previous message.
                composeRule.waitForIdle()
                composeRule.waitUntil("attempt $attempts ends", NETWORK_TIMEOUT_MS) {
                    exists(hasText(string(R.string.sign_in_error_wrong_password))) || exists(countdown)
                }
            }

            composeRule.onNode(hasText(string(R.string.sign_in_error_rate_limited))).assertIsDisplayed()
            shown = secondsShown()
            assertFollowsTokkl(shown)
            submitButton().assertIsNotEnabled()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitText(string(R.string.sign_in_server_url))
            field(R.string.sign_in_server_url).performTextInput(server.toString())

            composeRule.waitUntil("the countdown shows after the restart") { exists(countdown) }
            val restarted = secondsShown()
            assertTrue("the countdown went on from $shown s, not back up: $restarted s", restarted <= shown)
            assertFollowsTokkl(restarted)
            submitButton().assertIsNotEnabled()
        }
    }

    /**
     * Asks Tokkl itself how long it still refuses the account, with one more
     * wrong password. Tokkl refuses it unchecked, so it counts as no failure.
     */
    private fun assertFollowsTokkl(seconds: Long) {
        val request = Request.Builder()
            .url(server.api("me"))
            .header("Authorization", Credentials.basic(account.email, WRONG_PASSWORD))
            .build()
        val retryAfter = OkHttpClient().newCall(request).execute().use { response ->
            assertEquals("Tokkl still refuses the account", 429, response.code)
            response.header("Retry-After")?.toLongOrNull() ?: throw AssertionError("Tokkl's 429 has no Retry-After in seconds")
        }
        assertTrue(
            "the countdown shows $seconds s and Tokkl's Retry-After is $retryAfter s",
            abs(seconds - retryAfter) <= SLACK_SECONDS,
        )
    }

    /** The countdown's text in its plural for several seconds, with the seconds as a group. */
    private val countdownText: Regex by lazy {
        val format = context.resources.getQuantityString(R.plurals.sign_in_retry_countdown, SEVERAL)
        val (before, after) = format.split("%d", limit = 2)
        Regex(Regex.escape(before) + "(\\d+)" + Regex.escape(after))
    }

    private val countdown: SemanticsMatcher by lazy {
        SemanticsMatcher("shows the sign-in countdown") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { countdownText.matches(it.text) }
        }
    }

    private fun secondsShown(): Long {
        val node = composeRule.onNode(countdown).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].first { countdownText.matches(it.text) }.text
        return countdownText.matchEntire(text)!!.groupValues[1].toLong()
    }

    private fun submitButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction())

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The text field whose label is [label]. */
    private fun field(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(label)))

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        /** Tokkl's 10 failures per account and address, and the attempt it refuses. */
        const val MAX_ATTEMPTS = 11
        const val WRONG_PASSWORD = "not-the-password"
        const val NETWORK_TIMEOUT_MS = 15_000L
        const val SEVERAL = 2
        /** The two clocks round up a second each, and the requests take some time. */
        const val SLACK_SECONDS = 3L
    }
}
