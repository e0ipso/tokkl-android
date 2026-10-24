package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.ui.assertBrandReadsOnce
import java.io.File
import java.security.KeyStore
import java.time.DayOfWeek
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Signs in through the screen against the real Tokkl container. No assertion
 * message carries the token or the password. A wrong password would count
 * towards Tokkl's lockout of the shared test account, so the 403 case uses a
 * wrong API token, which Tokkl does not rate limit. The container serves
 * plain http, so each sign-in confirms the unencrypted connection warning.
 */
@SignedOut
class SignInScreenTest {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }


    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun awaitSignInScreen() {
        composeRule.awaitText(string(R.string.sign_in_server_url))
    }

    /** TalkBack reads the brand above the form, the wordmark alone, once, as "Tokkl". */
    @Test
    fun theBrandIsOneTalkBackNode() {
        composeRule.assertBrandReadsOnce()
    }

    @Test
    fun apiTokenSignInOpensTheAppAndStoresTheTokenOnlyInTheTokenStore() {
        enterServerUrl()
        field(R.string.sign_in_api_token).performTextInput(TokklTestServer.apiToken)

        submit()
        confirmHttpWarning()

        awaitHome()
        assertEquals(TokklTestServer.url, ServerConfigStore(context).load())
        assertTrue("the token store holds the API token", ApiTokenStore(context).load() == TokklTestServer.apiToken)
        // The test account is in UTC (scripts/instrumented-tests.sh), with Tokkl's other defaults.
        val settings = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = 5.0, defaultWorkspaceId = serverId("default_workspace_id"), userId = serverId("id"))
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
        confirmHttpWarning()

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
        confirmHttpWarning()

        val message = hasText(string(R.string.sign_in_error_wrong_api_token))
        composeRule.waitUntil("the wrong token message shows", NETWORK_TIMEOUT_MS) { exists(message) }
        composeRule.onNode(message).assertIsDisplayed()
        // Still there for another try. With the keyboard open it can sit below the keyboard's edge.
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().assertIsDisplayed()
        assertNull(ServerConfigStore(context).load())
        assertNull(ApiTokenStore(context).load())
    }

    @Test
    fun httpServerWarnsAndSendsNothingUntilTheUserConfirmsTwice() {
        enterServerUrl()
        field(R.string.sign_in_api_token).performTextInput(TokklTestServer.apiToken)
        submit()
        awaitHttpWarning()
        val body = string(R.string.sign_in_http_warning_body, TokklTestServer.url.toString())
        assertTrue("the warning names the token or password", "API token or password" in body)
        assertTrue("the warning names Tailscale", "Tailscale" in body)
        composeRule.onNode(hasText(body)).assertIsDisplayed()
        continueButton().assertIsNotEnabled()

        // Neither a tap outside the dialog nor Back closes it.
        val screen = context.resources.displayMetrics
        DeviceShell.run("input tap 4 ${screen.heightPixels / 2}")
        DeviceShell.run("input keyevent KEYCODE_BACK")
        composeRule.waitForIdle()
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_title))).assertIsDisplayed()
        assertNothingStored()

        composeRule.onNode(hasText(string(R.string.sign_in_warning_cancel)) and hasClickAction()).performClick()
        composeRule.waitUntil("the warning closes") { !exists(hasText(string(R.string.sign_in_http_warning_title))) }
        assertNothingStored()

        submit()
        awaitHttpWarning()
        understood().performClick()
        continueButton().assertIsEnabled().performClick()

        awaitHome()
        assertTrue("the confirmation is stored", ServerTrustStore(context).isCleartextConfirmed(TokklTestServer.url))
    }

    @Test
    fun confirmedUrlSignsInWithoutTheWarning() {
        ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
        enterServerUrl()
        field(R.string.sign_in_api_token).performTextInput(TokklTestServer.apiToken)

        submit()

        awaitHome()
    }

    /**
     * The email field keeps its supporting line with or without an error, so the error takes the
     * hint's place and the fields under it stay put. Positions are taken from the email field,
     * which an open keyboard or the scroll to the sign-in button may move, so the offsets match to
     * within a pixel: each is the difference of two positions, which loses float precision.
     */
    @Test
    fun emailErrorLeavesTheFieldsBelowInPlace() {
        composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable()).performClick()
        enterServerUrl()
        val before = passwordOffset()
        assertEmailFieldReads(R.string.sign_in_email_hint, not = R.string.sign_in_error_email)

        submit()
        composeRule.onNode(hasText(string(R.string.sign_in_error_email)), useUnmergedTree = true).assertExists()
        // The field's error semantics make TalkBack announce it.
        field(R.string.sign_in_email).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        assertEmailFieldReads(R.string.sign_in_error_email, not = R.string.sign_in_email_hint)
        val withError = passwordOffset()
        field(R.string.sign_in_email).performTextInput(TokklTestServer.email)
        composeRule.onNode(hasText(string(R.string.sign_in_error_email)), useUnmergedTree = true).assertDoesNotExist()
        field(R.string.sign_in_email).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        val cleared = passwordOffset()

        assertEquals("the password field moved when the email error showed", before.value, withError.value, SLACK_DP)
        assertEquals("the password field moved when the email error cleared", before.value, cleared.value, SLACK_DP)
    }

    /**
     * Every field keeps its height and its place when its error shows or clears, the server URL
     * field's too.
     */
    @Test
    fun fieldErrorsLeaveTheFormInPlace() {
        composeRule.assertFieldErrorsLeaveTheFormInPlace()
    }

    @Test
    fun theServerUrlErrorFitsInTheHintsLines() {
        composeRule.assertServerUrlErrorFitsTheHint()
    }

    @Test
    fun everyFieldAndButtonHasATalkBackLabel() {
        assertEveryControlIsLabelled()

        composeRule.onNode(hasText(string(R.string.sign_in_method_email_password)) and isSelectable()).performClick()

        assertEveryControlIsLabelled()
    }

    @Test
    fun everyControlOfTheHttpWarningHasATalkBackLabel() {
        enterServerUrl()
        field(R.string.sign_in_api_token).performTextInput(TokklTestServer.apiToken)
        submit()
        awaitHttpWarning()

        assertEveryControlIsLabelled()
        val toggles = composeRule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assertTrue("the warning has its checkbox", toggles.isNotEmpty())
        for (node in toggles) {
            assertTrue("a checkbox has no label: node ${node.id}", node.label().isNotBlank())
        }
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

    /** The brand above the form can push the button below the screen's or the keyboard's edge. */
    private fun submit() {
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun awaitHttpWarning() {
        composeRule.waitUntil("the http warning shows", NETWORK_TIMEOUT_MS) {
            exists(hasText(string(R.string.sign_in_http_warning_title)))
        }
    }

    /** Ticks that the user understands, then continues: the two steps the warning asks for. */
    private fun confirmHttpWarning() {
        awaitHttpWarning()
        understood().performClick()
        continueButton().performClick()
    }

    private fun understood(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_understood)) and isToggleable())

    private fun continueButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_continue)) and hasClickAction())

    private fun assertNothingStored() {
        assertNull(ServerConfigStore(context).load())
        assertNull(ApiTokenStore(context).load())
        assertFalse("no confirmation is stored", ServerTrustStore(context).isCleartextConfirmed(TokklTestServer.url))
    }

    private fun awaitHome() {
        composeRule.awaitTimerPage(NETWORK_TIMEOUT_MS)
    }

    /** The test account's default workspace, read straight from the server, past the app's guard. */
    private fun serverId(field: String): Long {
        val api = TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(TokklTestServer.apiToken), httpClient = OkHttpClient())
        return api.get(api.server.api("me"), JsonObject.serializer()).orFail("GET /api/v9/me")
            .getValue(field).jsonPrimitive.long
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** The text field whose label is [label]. */
    private fun field(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(label)))

    /** TalkBack reads [shown] with the email field, and never [not], which the line only reserves room for. */
    private fun assertEmailFieldReads(@StringRes shown: Int, @StringRes not: Int) {
        val read = field(R.string.sign_in_email).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertTrue("the email field does not read \"${string(shown)}\"", string(shown) in read)
        assertFalse("the email field reads \"${string(not)}\"", string(not) in read)
    }

    /** How far below the email field's top the password field's top is. */
    private fun passwordOffset(): Dp =
        field(R.string.sign_in_password).getUnclippedBoundsInRoot().top - field(R.string.sign_in_email).getUnclippedBoundsInRoot().top

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

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L

        /** Less than a pixel, in dp. */
        const val SLACK_DP = 0.5f
    }
}
