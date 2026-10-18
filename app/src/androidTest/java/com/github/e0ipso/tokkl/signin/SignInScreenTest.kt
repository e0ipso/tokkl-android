package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import java.io.File
import java.security.KeyStore
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Signs in through the screen against the real Tokkl container. No assertion
 * message carries the token or the password. A wrong password would count
 * towards Tokkl's lockout of the shared test account, so the 403 case uses a
 * wrong API token, which Tokkl does not rate limit.
 */
@SignedOut
class SignInScreenTest {

    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun awaitSignInScreen() {
        composeRule.awaitText(string(R.string.sign_in_server_url))
    }

    @Test
    fun apiTokenSignInOpensTheAppAndStoresTheTokenOnlyInTheTokenStore() {
        enterServerUrl()
        field(R.string.sign_in_api_token).performTextInput(TokklTestServer.apiToken)

        submit()

        awaitHome()
        assertEquals(TokklTestServer.url, ServerConfigStore(context).load())
        assertTrue("the token store holds the API token", ApiTokenStore(context).load() == TokklTestServer.apiToken)
        // The test account is in UTC (scripts/instrumented-tests.sh), with Tokkl's other defaults.
        val settings = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = 5.0)
        assertEquals(settings, AccountSettingsStore(context).load())
        assertEquals(settings, (context.applicationContext as TokklApplication).accountSettings.state.value)
        assertStoredNowhere(TokklTestServer.apiToken, "the API token in plain text")
    }

    @Test
    fun emailAndPasswordSignInKeepsTheTokenFromMeAndNeverThePassword() {
        enterServerUrl()
        composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable()).performClick()
        field(R.string.sign_in_email).performTextInput(TokklTestServer.email)
        field(R.string.sign_in_password).performTextInput(TokklTestServer.password)

        submit()

        awaitHome()
        assertEquals(TokklTestServer.url, ServerConfigStore(context).load())
        // scripts/instrumented-tests.sh read tokklApiToken from GET /api/v9/me.
        assertTrue("the stored token is the api_token from GET /api/v9/me", ApiTokenStore(context).load() == TokklTestServer.apiToken)
        assertStoredNowhere(TokklTestServer.password, "the password")
        assertStoredNowhere(TokklTestServer.apiToken, "the API token in plain text")
    }

    @Test
    fun wrongApiTokenShowsTheBadCredentialsMessageAndStoresNothing() {
        enterServerUrl()
        field(R.string.sign_in_api_token).performTextInput("not-the-api-token")

        submit()

        val message = hasText(string(R.string.sign_in_error_wrong_api_token))
        composeRule.waitUntil("the wrong token message shows", NETWORK_TIMEOUT_MS) { exists(message) }
        composeRule.onNode(message).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).assertIsDisplayed()
        assertNull(ServerConfigStore(context).load())
        assertNull(ApiTokenStore(context).load())
    }

    @Test
    fun everyFieldAndButtonHasATalkBackLabel() {
        assertEveryControlIsLabelled()

        composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable()).performClick()

        assertEveryControlIsLabelled()
    }

    private fun assertEveryControlIsLabelled() {
        val controls = composeRule.onAllNodes(hasSetTextAction() or hasClickAction()).fetchSemanticsNodes()
        assertTrue("the screen has fields and buttons", controls.size >= 4)
        for (node in controls) {
            assertTrue("a field or button has no label: node ${node.id}", node.label().isNotBlank())
        }
    }

    /** What TalkBack reads as the control's name: its content description or its text, not what was typed. */
    private fun SemanticsNode.label(): String {
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        return "$description $text"
    }

    private fun enterServerUrl() {
        field(R.string.sign_in_server_url).performTextInput(TokklTestServer.url.toString())
    }

    private fun submit() {
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performClick()
    }

    private fun awaitHome() {
        composeRule.waitUntil("the home screen shows", NETWORK_TIMEOUT_MS) { exists(hasText(string(R.string.launch_message))) }
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The text field whose label is [label]. */
    private fun field(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(label)))

    /**
     * No file in the app's storage and no Keystore alias holds [secret], in
     * UTF-8 or UTF-16. Preferences, databases and caches are files under these
     * directories. Failure messages name the file, never the secret.
     */
    private fun assertStoredNowhere(secret: String, what: String) {
        val roots = listOfNotNull(
            context.dataDir,
            context.createDeviceProtectedStorageContext().dataDir,
            context.getExternalFilesDir(null)?.parentFile,
        )
        val needles = listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).map { secret.toByteArray(it) }
        val holders = roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.canRead() }
                .filter { file -> file.name.contains(secret) || needles.any { file.holds(it) } }
                .map { it.relativeTo(root).path.replace(secret, "***") }
                .toList()
        }
        assertTrue("files hold $what: $holders", holders.isEmpty())

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = keyStore.aliases().toList()
        assertTrue("a Keystore alias holds $what", aliases.none { secret in it })
    }

    private fun File.holds(needle: ByteArray): Boolean {
        val bytes = readBytes()
        if (needle.isEmpty() || bytes.size < needle.size) return false
        return (0..bytes.size - needle.size).any { start -> needle.indices.all { bytes[start + it] == needle[it] } }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
