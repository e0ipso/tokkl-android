package com.github.e0ipso.tokkl.trust

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * A session signed in to the Tokkl container behind the harness's TLS proxy,
 * whose stored pin belongs to an earlier certificate: what the app sees after
 * the server's key was regenerated.
 */
@SignedOut
class ChangedCertificateWhileSignedInTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val endpoint = TokklTestServer.tlsUrl.api("me")
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(object : ExternalResource() {
            override fun before() {
                ServerTrustStore(context).pinCertificate(ServerCertificate(endpoint.host, endpoint.port, OLD_SHA256, subject = ""))
                Session(context).save(TokklTestServer.tlsUrl, TokklTestServer.apiToken)
            }
        })
        .around(composeRule)

    private val newSha256 = TokklTestServer.tlsFingerprint.replace(":", "").lowercase()

    @Test
    fun changedCertificateShowsThePromptOnTheHomeScreenAndConnectsOnlyOnceTrusted() {
        composeRule.awaitTimerPage()

        awaitNode(hasText(string(R.string.sign_in_certificate_changed_title)), "the certificate prompt shows")
        composeRule.onNode(hasText(TokklTestServer.tlsFingerprint)).performScrollTo().assertIsDisplayed()
        assertTrue("requests are refused before the user trusts it", me() is ApiResult.Failure)

        composeRule.onNode(hasText(string(R.string.sign_in_certificate_trust)) and hasClickAction()).performClick()

        awaitGone(hasText(string(R.string.sign_in_certificate_changed_title)), "the prompt closes")
        assertEquals(newSha256, ServerTrustStore(context).pinnedSha256(endpoint.host, endpoint.port))
        val body = (me() as? ApiResult.Success)?.value ?: throw AssertionError("GET /api/v9/me still fails after the user trusted the certificate")
        assertEquals(TokklTestServer.email, body.getValue("email").jsonPrimitive.content)
    }

    @Test
    fun cancellingKeepsRequestsRefusedAndTheNoticeReopensThePrompt() {
        composeRule.awaitTimerPage()
        awaitNode(hasText(string(R.string.sign_in_certificate_changed_title)), "the certificate prompt shows")

        composeRule.onNode(hasText(string(R.string.sign_in_warning_cancel)) and hasClickAction()).performClick()

        awaitNode(hasText(string(R.string.trust_refused_certificate)), "the notice shows")
        assertEquals(
            ApiError.UntrustedCertificate(ServerCertificate(endpoint.host, endpoint.port, newSha256, SUBJECT), changed = true),
            (me() as? ApiResult.Failure)?.error,
        )
        assertEquals(OLD_SHA256, ServerTrustStore(context).pinnedSha256(endpoint.host, endpoint.port))

        composeRule.onNode(hasText(string(R.string.trust_refused_review)) and hasClickAction()).performClick()

        awaitNode(hasText(string(R.string.sign_in_certificate_changed_title)), "the prompt opens again")
    }

    /** `GET /api/v9/me` with the stored session's client, as the app sends it. */
    private fun me(): ApiResult<JsonObject> {
        val client = Session(context).client() ?: throw AssertionError("no stored session")
        return client.get(endpoint, JsonObject.serializer())
    }

    private fun awaitGone(matcher: SemanticsMatcher, what: String) {
        composeRule.waitUntil(what, NETWORK_TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    private fun awaitNode(matcher: SemanticsMatcher, what: String) {
        composeRule.waitUntil(what, NETWORK_TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L

        /** The fingerprint of the certificate the user trusted before the server's key changed. */
        val OLD_SHA256 = "00".repeat(32)

        /** The subject of the certificate that scripts/instrumented-tests.sh makes. */
        const val SUBJECT = "CN=Tokkl instrumented tests"
    }
}
