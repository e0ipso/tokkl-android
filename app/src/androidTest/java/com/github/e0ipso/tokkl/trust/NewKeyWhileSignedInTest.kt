package com.github.e0ipso.tokkl.trust

import android.content.Context
import androidx.annotation.StringRes
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
import com.github.e0ipso.tokkl.LoopbackRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * The server's key changes under a signed-in session. The app signs in to the harness's TLS proxy
 * and pins its certificate. Then the proxy's second certificate, made for the run with a key of its
 * own, takes over, as when the server restarts with a regenerated key.
 *
 * The app reaches the proxy through a relay on the phone's loopback, as `https://127.0.0.1:<port>`,
 * a name both certificates hold. The pin belongs to that host and port. To swap the key, the test
 * points the relay at the second certificate's port and cuts the open connections, so the app's
 * next request meets the new certificate at the same host and port.
 */
@SignedOut
class NewKeyWhileSignedInTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val firstKey = TokklTestServer.tlsUrl.api("me")
    private val newKey = TokklTestServer.newKeyTlsUrl.api("me")
    private val relay = LoopbackRelay(firstKey.host, firstKey.port)
    private val server = ServerUrl.parse("https://$LOOPBACK:${relay.localPort}") ?: error("not a URL")
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(object : ExternalResource() {
            override fun after() {
                relay.close()
            }
        })
        .around(composeRule)

    private val firstSha256 = TokklTestServer.tlsFingerprint.replace(":", "").lowercase()
    private val newSha256 = TokklTestServer.newKeyTlsFingerprint.replace(":", "").lowercase()

    @Test
    fun aNewKeyShowsTheChangedPromptWithItsFingerprintAndConnectsOnceTrusted() {
        signIn()
        awaitNode(hasText(string(R.string.sign_in_certificate_title)), "the certificate prompt shows")
        composeRule.onNode(hasText(TokklTestServer.tlsFingerprint)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.sign_in_certificate_trust)) and hasClickAction()).performClick()
        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        assertEquals(firstSha256, pinnedSha256())
        assertEquals(TokklTestServer.email, email(me()))

        relay.relayTo(newKey.host, newKey.port)
        relay.cutRelayed()

        assertEquals(
            ApiError.UntrustedCertificate(ServerCertificate(LOOPBACK, relay.localPort, newSha256, SUBJECT), changed = true),
            (me() as? ApiResult.Failure)?.error,
        )
        awaitNode(hasText(string(R.string.sign_in_certificate_changed_title)), "the changed certificate prompt shows")
        composeRule.onNode(hasText(TokklTestServer.newKeyTlsFingerprint)).performScrollTo().assertIsDisplayed()
        assertEquals("the old pin stays until the user trusts the new certificate", firstSha256, pinnedSha256())

        composeRule.onNode(hasText(string(R.string.sign_in_certificate_trust)) and hasClickAction()).performClick()

        awaitGone(hasText(string(R.string.sign_in_certificate_changed_title)), "the prompt closes")
        assertEquals(newSha256, pinnedSha256())
        assertEquals(TokklTestServer.email, email(me()))
        assertEquals("connections the relay could not pass on", emptyList<String>(), relay.failures.toList())
    }

    private fun signIn() {
        composeRule.awaitText(string(R.string.sign_in_server_url))
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_server_url)))
            .performTextInput(server.toString())
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_api_token)))
            .performTextInput(TokklTestServer.apiToken)
        // The brand above the form can push the button below the screen's or the keyboard's edge.
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun pinnedSha256(): String? = ServerTrustStore(context).pinnedSha256(LOOPBACK, relay.localPort)

    /** `GET /api/v9/me` with the stored session's client, as the app sends it. */
    private fun me(): ApiResult<JsonObject> {
        val client = Session(context).client() ?: throw AssertionError("no stored session")
        return client.get(server.api("me"), JsonObject.serializer())
    }

    private fun email(result: ApiResult<JsonObject>): String {
        val body = (result as? ApiResult.Success)?.value ?: throw AssertionError("GET /api/v9/me failed: $result")
        return body.getValue("email").jsonPrimitive.content
    }

    private fun awaitGone(matcher: SemanticsMatcher, what: String) {
        composeRule.waitUntil(what, NETWORK_TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    private fun awaitNode(matcher: SemanticsMatcher, what: String) {
        composeRule.waitUntil(what, NETWORK_TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        /** The phone's loopback, which both certificates name. */
        const val LOOPBACK = "127.0.0.1"

        const val NETWORK_TIMEOUT_MS = 15_000L

        /** The subject of both certificates that scripts/instrumented-tests.sh makes for the proxy. */
        const val SUBJECT = "CN=Tokkl instrumented tests"
    }
}
