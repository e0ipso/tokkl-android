package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Signs in through the screen to the real Tokkl container under the path
 * prefix `/tokkl`, as a reverse proxy serves it beside other sites. The proxy
 * in scripts/instrumented-tests.sh strips the prefix and answers 404 to every
 * path outside it, so sign-in passes only if every request keeps the prefix.
 * The proxy serves plain http there, so each sign-in confirms the unencrypted
 * connection warning. No assertion message carries the token or the password.
 */
@SignedOut
class SignInUnderPathPrefixTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = TokklTestServer.prefixedUrl

    @Before
    fun awaitSignInScreen() {
        composeRule.awaitText(string(R.string.sign_in_server_url))
    }

    @Test
    fun apiTokenSignInReadsMeThroughThePrefix() {
        field(R.string.sign_in_server_url).performTextInput(server.toString())
        field(R.string.sign_in_api_token).performTextInput(TokklTestServer.apiToken)

        submit()
        confirmHttpWarning()

        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        assertSignedInThroughThePrefix()
    }

    @Test
    fun emailAndPasswordSignInReadsMeThroughThePrefix() {
        field(R.string.sign_in_server_url).performTextInput(server.toString())
        composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable()).performClick()
        field(R.string.sign_in_email).performTextInput(TokklTestServer.email)
        field(R.string.sign_in_password).performTextInput(TokklTestServer.password)

        submit()
        confirmHttpWarning()

        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        assertSignedInThroughThePrefix()
    }

    /**
     * Sign-in stores anything only after GET /me answered. The account
     * settings come from that answer, with the test account's timezone, UTC
     * (scripts/instrumented-tests.sh), and so does the token for email and
     * password.
     */
    private fun assertSignedInThroughThePrefix() {
        assertEquals(server, ServerConfigStore(context).load())
        assertTrue("the token store holds the API token from /me", ApiTokenStore(context).load() == TokklTestServer.apiToken)
        assertEquals("UTC", AccountSettingsStore(context).load()?.timeZone)
        assertTrue("the confirmation names the prefixed URL", ServerTrustStore(context).isCleartextConfirmed(server))
    }

    /** The brand above the form can push the button below the screen's or the keyboard's edge. */
    private fun submit() {
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    }

    /**
     * Checks that the warning names the URL with its prefix, then ticks that
     * the user understands and continues: the two steps the warning asks for.
     */
    private fun confirmHttpWarning() {
        composeRule.waitUntil("the http warning shows", NETWORK_TIMEOUT_MS) {
            exists(hasText(string(R.string.sign_in_http_warning_title)))
        }
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_body, server.toString()))).assertExists()
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_understood)) and isToggleable()).performClick()
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_continue)) and hasClickAction()).performClick()
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The text field whose label is [label]. */
    private fun field(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(label)))

    private fun string(@StringRes id: Int): String = context.getString(id)

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
