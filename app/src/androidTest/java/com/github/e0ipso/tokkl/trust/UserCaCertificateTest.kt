package com.github.e0ipso.tokkl.trust

import android.content.Context
import androidx.annotation.StringRes
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
import com.github.e0ipso.tokkl.UserCaInstalled
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Signs in through the screen to the Tokkl container behind the harness's proxy port whose
 * certificate a CA made for the run signed. scripts/instrumented-tests.sh installs that CA as a
 * user CA, so the user trust anchors in res/xml/network_security_config.xml and the platform trust
 * manager that the shared client asks first accept the certificate on Android's own TLS stack,
 * with no prompt and no pin.
 */
@SignedOut
@UserCaInstalled
class UserCaCertificateTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val endpoint = TokklTestServer.userCaTlsUrl.api("me")
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    @Test
    fun aCertificateAUserCaSignedSignsInWithoutAPromptOrAPin() {
        assertTrue("the user CA store holds $CA_SUBJECT: ${userCas()}", CA_SUBJECT in userCas())

        signIn()

        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
        assertFalse("a certificate prompt shows", exists(R.string.sign_in_certificate_title))
        assertFalse("a certificate prompt shows", exists(R.string.sign_in_certificate_changed_title))
        assertNull("a pin for the server", ServerTrustStore(context).pinnedSha256(endpoint.host, endpoint.port))
        assertEquals(TokklTestServer.userCaTlsUrl, ServerConfigStore(context).load())
        val client = Session(context).client() ?: throw AssertionError("no stored session")
        val body = (client.get(endpoint, JsonObject.serializer()) as? ApiResult.Success)?.value
            ?: throw AssertionError("GET /api/v9/me failed without a pin")
        assertEquals(TokklTestServer.email, body.getValue("email").jsonPrimitive.content)
    }

    /** The subjects of the user-installed CAs, as Settings lists them under User credentials. */
    private fun userCas(): List<String> {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        return store.aliases().toList().filter { it.startsWith("user:") }
            .map { (store.getCertificate(it) as X509Certificate).subjectX500Principal.name }
    }

    private fun signIn() {
        composeRule.awaitText(string(R.string.sign_in_server_url))
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_server_url)))
            .performTextInput(TokklTestServer.userCaTlsUrl.toString())
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_api_token)))
            .performTextInput(TokklTestServer.apiToken)
        // The brand above the form can push the button below the screen's or the keyboard's edge.
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun exists(@StringRes text: Int): Boolean =
        composeRule.onAllNodes(hasText(string(text))).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L

        /** The subject of the CA that scripts/instrumented-tests.sh makes and installs. */
        const val CA_SUBJECT = "CN=Tokkl instrumented tests CA"
    }
}
