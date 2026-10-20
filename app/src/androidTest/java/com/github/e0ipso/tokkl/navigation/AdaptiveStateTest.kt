package com.github.e0ipso.tokkl.navigation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.e0ipso.tokkl.Destination
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedInFrame
import com.github.e0ipso.tokkl.SignedInPanes
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.timer.TimerView
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.windowWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import com.github.e0ipso.tokkl.showsEntryBeside as entryBeside

/**
 * The entry form and the Timer page keep their state when a fold or a rotation moves them between
 * a screen of their own and the two panes of an expanded window. Activity recreation saves and
 * restores the composition, as [StateRestorationTester] does.
 */
class AdaptiveStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The window size the next composition sees, read without observation, as after a recreation. */
    private var window = EXPANDED_WINDOW

    @Test
    fun aFoldKeepsTheFormAndThePageBesideIt() {
        var stack by mutableStateOf(listOf(Destination.TIMER, Destination.ENTRY))
        StateRestorationTester(composeRule).apply {
            setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(window)) {
                    TokklTheme(darkTheme = false) { Panes(stack) }
                }
            }
            composeRule.onNodeWithText("$PAGE 0").performClick()
            composeRule.onNodeWithText("$FORM 0").performClick()
            composeRule.onNodeWithText("$FORM 1").performClick()

            // Folded to a phone's width: the form takes the screen and keeps its state.
            window = COMPACT_WINDOW
            emulateSavedInstanceStateRestore()
            assertShows("$FORM 2")
            assertGone(PAGE)

            // Unfolded: the form and the page beside it come back with their state. The page's
            // saved state outlived the fold, while it was not on the screen.
            window = EXPANDED_WINDOW
            emulateSavedInstanceStateRestore()
            assertShows("$FORM 2")
            assertShows("$PAGE 1")
            composeRule.onNodeWithText("$PAGE 1").performClick()

            // Closing the form leaves the page with its state, and so does a fold after that.
            composeRule.runOnIdle { stack = listOf(Destination.TIMER) }
            assertShows("$PAGE 2")
            assertGone(FORM)
            window = MEDIUM_WINDOW
            emulateSavedInstanceStateRestore()
            assertShows("$PAGE 2")
        }
    }

    /** What TokklNavHost draws for [stack] at the window's width, with a saved counter for each screen. */
    @Composable
    private fun Panes(stack: List<Destination>) {
        val width = windowWidth()
        SignedInFrame(current = TopLevelPage.TIMER, onOpen = {}, width = width) {
            SignedInPanes(destination = stack.last(), entryBeside = entryBeside(width, TimerView.LIST, stack), entryKey = 0) { shown ->
                when (shown) {
                    Destination.TIMER -> Counter(PAGE)
                    Destination.ENTRY -> Counter(FORM)
                    else -> error("$shown is not on this stack")
                }
            }
        }
    }

    /** A button that counts its taps in saved state. */
    @Composable
    private fun Counter(name: String) {
        var taps by rememberSaveable { mutableIntStateOf(0) }
        Box {
            Button(onClick = { taps++ }) { Text("$name $taps") }
        }
    }

    private fun assertShows(text: String) {
        assertEquals("\"$text\" shows", 1, composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().size)
    }

    private fun assertGone(prefix: String) {
        assertEquals("nothing of $prefix shows", 0, composeRule.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().size)
    }

    private companion object {
        const val PAGE = "Timer page"
        const val FORM = "Entry form"
        val COMPACT_WINDOW = DpSize(411.dp, 891.dp)
        val MEDIUM_WINDOW = DpSize(700.dp, 1000.dp)
        val EXPANDED_WINDOW = DpSize(1280.dp, 800.dp)
    }
}

/**
 * The real app, rotated while a past entry is being added: the form keeps what was typed, and on a
 * window that turns expanded the Timer page's List view shows beside it. Rotating back shows the
 * form alone again, still with the text.
 */
class AdaptiveRotationTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun aRotationKeepsTheEntryBeingAdded() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.awaitTimerPage()
            composeRule.onNode(hasText(string(R.string.timer_add_entry)) and hasClickAction()).performClick()
            composeRule.awaitText(string(R.string.entry_form_new))
            composeRule.waitUntil("the account's workspace is known", TIMEOUT_MS) {
                composeRule.onAllNodes(descriptionField() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(descriptionField()).performTextInput(TYPED)
            composeRule.onNode(descriptionField()).performImeAction()
            composeRule.waitForIdle()

            for ((requested, orientation) in ROTATIONS) {
                val before = resumedActivity()
                scenario.onActivity { it.requestedOrientation = requested }
                composeRule.waitUntil("the activity is recreated", TIMEOUT_MS) {
                    resumedActivity()?.let { it !== before && it.resources.configuration.orientation == orientation } == true
                }
                composeRule.waitForIdle()
                val expanded = resumedActivity()!!.let { activity ->
                    activity.windowManager.currentWindowMetrics.bounds.width() / activity.resources.displayMetrics.density >= EXPANDED_DP
                }

                assertEquals("the form keeps its heading", 1, composeRule.onAllNodes(hasText(string(R.string.entry_form_new)) and isHeading()).fetchSemanticsNodes().size)
                assertTrue("the form keeps what was typed", composeRule.onAllNodes(descriptionField() and hasText(TYPED)).fetchSemanticsNodes().isNotEmpty())
                val listSwitch = composeRule.onAllNodes(hasText(string(R.string.timer_view_list)) and isSelectable()).fetchSemanticsNodes()
                assertEquals("the Timer page shows beside the form only when expanded", expanded, listSwitch.isNotEmpty())
            }
        }
    }

    private fun descriptionField(): SemanticsMatcher = hasSetTextAction() and hasText(string(R.string.entry_description))

    private fun resumedActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        }
        return resumed
    }

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val TYPED = "Plan the fold"

        /** Where Material's expanded widths start. */
        const val EXPANDED_DP = 840f

        val ROTATIONS = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
        )
    }
}
