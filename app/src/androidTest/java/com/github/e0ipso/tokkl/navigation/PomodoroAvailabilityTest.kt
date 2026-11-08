package com.github.e0ipso.tokkl.navigation

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklNavHost
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.core.ui.R
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Pomodoro destination follows the server's timer API in the production navigation host, in
 * the bottom bar and in the rail: without the capability it is not an item, and when the server
 * loses it while the page is open, the host returns to Timer. The capabilities are the test's own
 * flow, so no refresh can bring the flag back mid-test.
 */
class PomodoroAvailabilityTest {
    private val compose = createEmptyComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val capabilities = MutableStateFlow(
        Capabilities.NONE.copy(trash = true, favorites = true, timerState = true),
    )
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun closeTheHost() {
        scenario?.close()
    }

    @Test
    fun theBarOffersPomodoroOnlyWhileTheServerHasTheTimerApi() = exercise(width = 400)

    @Test
    fun theRailOffersPomodoroOnlyWhileTheServerHasTheTimerApi() = exercise(width = 700)

    private fun exercise(width: Int) {
        host(width)
        assertTrue("Pomodoro is an item with the timer API", exists(item()))
        compose.onNode(item()).performClick()
        compose.waitUntil(
            "the Pomodoro page opens",
            TIMEOUT_MS,
        ) { exists(hasText(string(R.string.pomodoro_title)) and isHeading()) }
        assertTrue("the item is marked", exists(item() and isSelected()))

        // The server loses the timer API while the page is open.
        capabilities.value = capabilities.value.copy(timerState = false)
        compose.waitUntil("the item goes", TIMEOUT_MS) { !exists(item()) }
        compose.awaitTimerPage(TIMEOUT_MS)
        assertTrue("Timer is shown instead", !exists(hasText(string(R.string.pomodoro_title)) and isHeading()))

        // And it comes back when the server has it again, without opening on its own.
        capabilities.value = capabilities.value.copy(timerState = true)
        compose.waitUntil("the item returns", TIMEOUT_MS) { exists(item()) }
        compose.awaitTimerPage(TIMEOUT_MS)
    }

    @Test
    fun withoutTheTimerApiThereIsNoPomodoroItemAtAnyWidth() {
        capabilities.value = capabilities.value.copy(timerState = false)
        for (width in listOf(400, 700)) {
            host(width)
            assertTrue("no Pomodoro item at $width dp", !exists(item()))
            scenario!!.close()
        }
    }

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun item() = hasText(
        string(R.string.pomodoro_title),
    ) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun string(id: Int) = context.getString(id)

    private fun host(width: Int) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            WindowCompat.enableEdgeToEdge(activity.window)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            activity.setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(DpSize(width.dp, 900.dp))) {
                    TokklTheme(darkTheme = false) {
                        TokklNavHost(capabilities = capabilities)
                    }
                }
            }
        }
        compose.awaitTimerPage(TIMEOUT_MS)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
