package com.github.e0ipso.tokkl.trust

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * A session stored for the plain http Tokkl container with no confirmation
 * recorded, as after an install from before the http warning existed.
 */
@SignedOut
class UnconfirmedHttpWhileSignedInTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(object : ExternalResource() {
            override fun before() {
                Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
            }
        })
        .around(composeRule)

    @Test
    fun unconfirmedHttpSessionShowsTheWarningAndSendsNothingUntilConfirmed() {
        composeRule.awaitText(string(R.string.launch_message))

        awaitNode(hasText(string(R.string.sign_in_http_warning_title)), "the http warning shows")
        continueButton().assertIsNotEnabled()
        // Neither a tap outside the dialog nor Back closes it.
        DeviceShell.run("input tap 4 ${context.resources.displayMetrics.heightPixels / 2}")
        DeviceShell.run("input keyevent KEYCODE_BACK")
        composeRule.waitForIdle()
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_title))).assertIsDisplayed()
        assertEquals(ApiError.CleartextNotConfirmed(TokklTestServer.url), (me() as? ApiResult.Failure)?.error)

        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_understood)) and isToggleable()).performClick()
        continueButton().assertIsEnabled().performClick()

        awaitGone(hasText(string(R.string.sign_in_http_warning_title)), "the warning closes")
        assertTrue("the confirmation is stored", ServerTrustStore(context).isCleartextConfirmed(TokklTestServer.url))
        assertTrue("requests reach the server", me() is ApiResult.Success)
    }

    @Test
    fun cancellingKeepsRequestsRefusedAndTheNoticeReopensTheWarning() {
        composeRule.awaitText(string(R.string.launch_message))
        awaitNode(hasText(string(R.string.sign_in_http_warning_title)), "the http warning shows")

        composeRule.onNode(hasText(string(R.string.sign_in_warning_cancel)) and hasClickAction()).performClick()

        awaitNode(hasText(string(R.string.trust_refused_http)), "the notice shows")
        assertEquals(ApiError.CleartextNotConfirmed(TokklTestServer.url), (me() as? ApiResult.Failure)?.error)
        assertFalse(ServerTrustStore(context).isCleartextConfirmed(TokklTestServer.url))

        composeRule.onNode(hasText(string(R.string.trust_refused_review)) and hasClickAction()).performClick()

        awaitNode(hasText(string(R.string.sign_in_http_warning_title)), "the warning opens again")
    }

    private fun continueButton() =
        composeRule.onNode(hasText(string(R.string.sign_in_http_warning_continue)) and hasClickAction())

    /** `GET /api/v9/me` with the stored session's client, as the app sends it. */
    private fun me(): ApiResult<JsonObject> {
        val client = Session(context).client() ?: throw AssertionError("no stored session")
        return client.get(client.server.api("me"), JsonObject.serializer())
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
    }
}
