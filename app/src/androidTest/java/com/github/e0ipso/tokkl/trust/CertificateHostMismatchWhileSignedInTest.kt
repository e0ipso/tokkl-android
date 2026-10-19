package com.github.e0ipso.tokkl.trust

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.LoopbackRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * A stored session for a server whose certificate does not name the host in its URL: the
 * harness's TLS proxy under `localhost`, while its certificate names only the IP addresses
 * 10.0.2.2 and 127.0.0.1. A relay on the phone's loopback carries the connection to the proxy,
 * so the app's own client and trust checks see the proxy's certificate. No answer to a prompt
 * fixes that, so the Timer page offers none.
 */
@SignedOut
class CertificateHostMismatchWhileSignedInTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val proxy = TokklTestServer.tlsUrl.api("me")
    private val relay = LoopbackRelay(proxy.host, proxy.port)
    private val server = ServerUrl.parse("https://$HOST:${relay.localPort}") ?: error("not a URL")
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(object : ExternalResource() {
            override fun before() {
                Session(context).save(server, TokklTestServer.apiToken)
            }

            override fun after() {
                relay.close()
            }
        })
        .around(composeRule)

    @Test
    fun theNoticeNamesTheHostAndTheFixAndNothingIsPinned() {
        composeRule.awaitTimerPage()

        val notice = context.getString(R.string.trust_refused_certificate_host, HOST)
        composeRule.waitUntil("the host name notice shows", NETWORK_TIMEOUT_MS) { exists(hasText(notice)) }
        composeRule.onNode(hasText(notice)).assertIsDisplayed()
        assertFalse("the notice offers to review the connection", exists(hasText(string(R.string.trust_refused_review))))
        assertFalse("a certificate prompt shows", exists(hasText(string(R.string.sign_in_certificate_title))))
        assertFalse("a certificate prompt shows", exists(hasText(string(R.string.sign_in_certificate_changed_title))))
        assertEquals(ApiError.CertificateHostMismatch(HOST), (me() as? ApiResult.Failure)?.error)
        assertNull("a pin for $HOST", ServerTrustStore(context).pinnedSha256(HOST, relay.localPort))
        assertNull("a pin for the proxy", ServerTrustStore(context).pinnedSha256(proxy.host, proxy.port))
    }

    /** `GET /api/v9/me` with the stored session's client, as the app sends it. */
    private fun me(): ApiResult<JsonObject> {
        val client = Session(context).client() ?: throw AssertionError("no stored session")
        return client.get(server.api("me"), JsonObject.serializer())
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        /** A name the phone resolves to its loopback, which the certificate does not name. */
        const val HOST = "localhost"

        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
