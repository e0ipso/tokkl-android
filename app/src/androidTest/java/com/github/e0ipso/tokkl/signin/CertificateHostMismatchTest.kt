package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Signs in through the screen to the TLS proxy of scripts/instrumented-tests.sh under a host
 * name its certificate does not name. The certificate names only 10.0.2.2 and 127.0.0.1, as IP
 * addresses, so the URL uses [HOST], which this test's client resolves to the proxy's address.
 * The client is the shared one with that lookup added, so the trust checks are the app's. The
 * form shows the host name message, never the trust prompt or the unreachable message, and
 * nothing is stored.
 */
class CertificateHostMismatchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val proxy = TokklTestServer.tlsUrl.api("me")
    private val scope = MainScope()
    private var saved = false

    @Before
    fun showTheSignInScreen() {
        SessionRule.signOut()
        val client = SharedHttpClient.instance.newBuilder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname == HOST) InetAddress.getAllByName(proxy.host).toList() else Dns.SYSTEM.lookup(hostname)
            })
            .build()
        val model = SignInModel(
            signIn = SignIn(session = { _, _, _ -> saved = true }, httpClient = client),
            trust = ServerTrustStore(context),
            cooldowns = SignInCooldownStore(context),
            scope = scope,
            io = Dispatchers.IO,
            clock = System::currentTimeMillis,
        )
        composeRule.setContent { TokklTheme { SignInScreen(model, onSignedIn = {}) } }
    }

    @After
    fun forgetTheServer() {
        try { composeRule.checkAccessibility() } finally {
            scope.cancel()
            SessionRule.signOut()
        }
    }

    /** The certificate is not trusted yet, so the trust manager sees the mismatch before any prompt. */
    @Test
    fun anUnpinnedCertificateIsNeverOffered() {
        signIn()

        awaitHostMessage()
        assertFalse("the trust prompt shows", exists(hasText(string(R.string.sign_in_certificate_title))))
        assertNull(ServerTrustStore(context).pinnedSha256(HOST, proxy.port))
        assertFalse("the session was stored", saved)
    }

    /**
     * A pin stored before the app checked the host, or a CA, gets the certificate through the
     * trust manager. OkHttp's host name check still refuses it.
     */
    @Test
    fun aPinnedCertificateStillFailsTheHostNameCheck() {
        val sha256 = TokklTestServer.tlsFingerprint.replace(":", "").lowercase()
        ServerTrustStore(context).pinCertificate(ServerCertificate(HOST, proxy.port, sha256, subject = ""))

        signIn()

        awaitHostMessage()
        assertFalse("the session was stored", saved)
    }

    private fun signIn() {
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_server_url)))
            .performTextInput("https://$HOST:${proxy.port}")
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.sign_in_api_token)))
            .performTextInput(TokklTestServer.apiToken)
        // The brand above the form can push the button below the screen's or the keyboard's edge.
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun awaitHostMessage() {
        val message = context.getString(R.string.sign_in_error_certificate_host, HOST)
        composeRule.waitUntil("the host name message shows", NETWORK_TIMEOUT_MS) { exists(hasText(message)) }
        composeRule.onNode(hasText(message)).performScrollTo().assertIsDisplayed()
        assertFalse("the unreachable message shows", exists(hasText(string(R.string.sign_in_error_unreachable))))
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        /** A name no resolver knows, under the reserved .invalid top-level domain. */
        const val HOST = "tokkl.invalid"

        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
