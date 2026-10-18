package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Signs in through the screen to the real Tokkl container behind the TLS
 * proxy of scripts/instrumented-tests.sh, whose self-signed certificate no CA
 * on the phone trusts. https never shows the http warning.
 */
@SignedOut
class CertificatePromptTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val endpoint = TokklTestServer.tlsUrl.api("me")
    private val sha256 = TokklTestServer.tlsFingerprint.replace(":", "").lowercase()

    @Before
    fun awaitSignInScreen() {
        composeRule.awaitText(string(R.string.sign_in_server_url))
    }

    @Test
    fun untrustedCertificateShowsItsFingerprintAndSignsInOnceTrusted() {
        signIn()

        awaitPrompt(R.string.sign_in_certificate_title)
        composeRule.onNode(hasText(TokklTestServer.tlsFingerprint)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("CN=Tokkl instrumented tests")).performScrollTo().assertIsDisplayed()
        assertFalse("https shows no http warning", exists(hasText(string(R.string.sign_in_http_warning_title))))
        assertNull(ServerConfigStore(context).load())
        assertNull(ApiTokenStore(context).load())
        for (node in composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
            assertTrue("a control has no label: node ${node.id}", "$description $text".isNotBlank())
        }

        composeRule.onNode(hasText(string(R.string.sign_in_certificate_trust)) and hasClickAction()).performClick()

        composeRule.waitUntil("the home screen shows", NETWORK_TIMEOUT_MS) { exists(hasText(string(R.string.launch_message))) }
        assertEquals(sha256, ServerTrustStore(context).pinnedSha256(endpoint.host, endpoint.port))
        assertEquals(TokklTestServer.tlsUrl, ServerConfigStore(context).load())
    }

    @Test
    fun changedCertificatePromptsAgainAndCancelKeepsTheOldPin() {
        val old = "00".repeat(32)
        ServerTrustStore(context).pinCertificate(ServerCertificate(endpoint.host, endpoint.port, old, subject = ""))

        signIn()

        awaitPrompt(R.string.sign_in_certificate_changed_title)
        composeRule.onNode(hasText(TokklTestServer.tlsFingerprint)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.sign_in_warning_cancel)) and hasClickAction()).performClick()

        composeRule.waitUntil("the prompt closes") { !exists(hasText(string(R.string.sign_in_certificate_changed_title))) }
        assertEquals(old, ServerTrustStore(context).pinnedSha256(endpoint.host, endpoint.port))
        assertNull(ServerConfigStore(context).load())
    }

    @Test
    fun pinnedCertificateSignsInWithoutAPrompt() {
        ServerTrustStore(context).pinCertificate(ServerCertificate(endpoint.host, endpoint.port, sha256, subject = ""))

        signIn()

        composeRule.waitUntil("the home screen shows", NETWORK_TIMEOUT_MS) { exists(hasText(string(R.string.launch_message))) }
    }

    private fun signIn() {
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_server_url)))
            .performTextInput(TokklTestServer.tlsUrl.toString())
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_api_token)))
            .performTextInput(TokklTestServer.apiToken)
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performClick()
    }

    private fun awaitPrompt(@StringRes title: Int) {
        composeRule.waitUntil("the certificate prompt shows", NETWORK_TIMEOUT_MS) { exists(hasText(string(title))) }
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
