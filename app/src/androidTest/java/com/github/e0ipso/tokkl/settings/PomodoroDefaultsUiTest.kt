package com.github.e0ipso.tokkl.settings

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The same edits and assertions run at normal and largest font/display sizes in both themes. */
@RunWith(Parameterized::class)
class PomodoroDefaultsUiTest(private val fontScale: Float, private val largeDisplay: Boolean, private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SystemFontScale(fontScale, largestDisplaySize = largeDisplay)).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var defaults by mutableStateOf(PomodoroDefaults.DEFAULT)
    private var changes = 0

    @After
    fun auditFinalState() { compose.checkAccessibility() }

    @Test
    fun eachLabelledDefaultRefusesInvalidInputAndAcceptsBothBounds() {
        show(shared = true)
        compose.onNode(hasText(string(R.string.settings_pomodoro)) and isHeading()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_pomodoro_shared)).performScrollTo().assertIsDisplayed()
        for (field in PomodoroDefaultField.entries) {
            open(field)
            val input = compose.onNode(hasText(string(field.label)) and hasSetTextAction())
            for (bad in listOf("", "0", "-1", "1.5", "999999999999", (field.range.last + 1).toString())) {
                val before = defaults
                input.performScrollTo().performTextReplacement(bad)
                button(R.string.settings_pomodoro_save).assertIsNotEnabled()
                assertEquals(before, defaults)
                assertEquals(0, changes)
            }
            input.performTextReplacement(field.range.first.toString())
            readableVisibleText()
            button(R.string.settings_pomodoro_save).assertIsEnabled().performTouchInput { click() }
            assertEquals(field.range.first, field.value(defaults))
            assertEquals(1, changes)
            changes = 0
            open(field)
            compose.onNode(hasText(string(field.label)) and hasSetTextAction()).performScrollTo().performTextReplacement(field.range.last.toString())
            readableVisibleText()
            button(R.string.settings_pomodoro_save).assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
            assertEquals(field.range.last, field.value(defaults))
            assertEquals(1, changes)
            changes = 0
        }
        assertEquals(PomodoroDefaults(180, 60, 60, 12), defaults)
    }

    @Test
    fun cancelLeavesAllDefaultsAloneAndLocalAccountsExplainWhereTheyAreKept() {
        show(shared = false)
        compose.onNodeWithText(string(R.string.settings_pomodoro_local)).performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.settings_pomodoro_shared))).fetchSemanticsNodes().size)
        for (field in PomodoroDefaultField.entries) {
            open(field)
            compose.onNode(hasText(string(field.label)) and hasSetTextAction()).performScrollTo().performTextReplacement("2")
            button(R.string.settings_pomodoro_cancel).performClick()
            assertEquals(PomodoroDefaults.DEFAULT, defaults)
            assertEquals(0, changes)
        }
    }

    @Test
    fun savingOneDefaultKeepsARefreshOfTheOtherDefaults() {
        show(shared = true)
        open(PomodoroDefaultField.WORK)
        compose.onNode(hasText(string(R.string.pomodoro_work_minutes)) and hasSetTextAction()).performScrollTo().performTextReplacement("45")
        compose.runOnIdle { defaults = PomodoroDefaults(50, 10, 30, 3) }
        button(R.string.settings_pomodoro_save).performClick()
        assertEquals(PomodoroDefaults(45, 10, 30, 3), defaults)
        assertEquals(1, changes)
    }

    private fun show(shared: Boolean) {
        compose.activityRule.scenario.onActivity {
            WindowCompat.enableEdgeToEdge(it.window)
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            TokklTheme(darkTheme = dark) {
                SettingsContent(
                    settings = ThemeSettings(), onModeChange = {}, onDynamicColorChange = {},
                    signOut = SignOutModel.State(), onSignOut = {}, onConfirmSignOut = {}, onCancelSignOut = {}, onOpenLicences = {},
                    pomodoro = defaults, pomodoroSharedWithWeb = shared, onPomodoroChange = { defaults = it; changes++ },
                )
            }
        }
    }

    private fun open(field: PomodoroDefaultField) {
        compose.onNode(hasText(string(field.label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNode(hasText(string(field.label)) and hasSetTextAction()).assertIsDisplayed()
        readableVisibleText()
    }

    private fun button(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())
    private fun string(label: Int) = context.getString(label)

    private fun readableVisibleText() {
        compose.checkAccessibility()
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the editor has readable text", nodes.isNotEmpty())
        for (node in nodes) {
            if (node.boundsInRoot.height == 0f) continue
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            for (result in results) assertFalse("clipped text: ${result.layoutInput.text}", result.didOverflowHeight)
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "font={0}, display={1}, dark={2}")
        fun configurations(): List<Array<Any>> = listOf(
            arrayOf(1f, false, false), arrayOf(1f, false, true),
            arrayOf(SystemFontScale.LARGEST, true, false), arrayOf(SystemFontScale.LARGEST, true, true),
        )
    }
}
