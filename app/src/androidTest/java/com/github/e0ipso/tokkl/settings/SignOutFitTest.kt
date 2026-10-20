package com.github.e0ipso.tokkl.settings

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The sign-out warning and the failed sign-out's banner at each font scale, in both themes: no
 * text is cut off, and TalkBack reads the warning's title as a heading and each button by its
 * label.
 */
abstract class SignOutFitTest(private val fontScale: Float) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(fontScale)).around(composeRule)

    @Test
    fun theWarningAboutUnsentChanges() {
        for (dark in listOf(false, true)) {
            show(SignOutModel.State(unsentToConfirm = 12), dark)
            val warning = InstrumentationRegistry.getInstrumentation().targetContext.resources
                .getQuantityString(R.plurals.settings_sign_out_unsent, 12, 12)

            composeRule.onNode(hasText(string(R.string.settings_sign_out_title)) and isHeading() and inDialog()).assertIsDisplayed()
            composeRule.onNode(hasText(warning) and inDialog()).assertIsDisplayed()
            composeRule.onNode(hasText(string(R.string.catalog_cancel)) and hasClickAction() and inDialog()).assertIsDisplayed()
            composeRule.onNode(hasText(string(R.string.settings_sign_out)) and hasClickAction() and inDialog()).assertIsDisplayed()
            assertNoTextCutOff("the warning, dark $dark")
        }
    }

    @Test
    fun aFailedSignOut() {
        for (dark in listOf(false, true)) {
            show(SignOutModel.State(failed = true), dark)

            composeRule.onNode(hasText(string(R.string.settings_sign_out_failed)), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            assertNoTextCutOff("the failed sign-out, dark $dark")
        }
    }

    /** What the screen shows, which each step of a test changes. setContent runs once a test. */
    private var shown by mutableStateOf<Pair<SignOutModel.State, Boolean>?>(null)

    private fun show(signOut: SignOutModel.State, dark: Boolean) {
        val first = shown == null
        shown = signOut to dark
        if (first) {
            composeRule.setContent {
                val (state, darkTheme) = checkNotNull(shown)
                TokklTheme(darkTheme = darkTheme) {
                    SettingsContent(
                        settings = ThemeSettings(),
                        onModeChange = {},
                        onDynamicColorChange = {},
                        signOut = state,
                        onSignOut = {},
                        onConfirmSignOut = {},
                        onCancelSignOut = {},
                        onOpenLicences = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** No text shown, in the page or in the dialog over it, is cut off in height. */
    private fun assertNoTextCutOff(where: String) {
        assertEquals(fontScale, composeRule.activity.resources.configuration.fontScale)
        val nodes = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: continue
            if (layout.layoutInput.overflow == TextOverflow.Ellipsis) continue
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: layout.layoutInput.text.text
            assertFalse("text cut off in $where: $text", layout.didOverflowHeight)
        }
    }

    private fun inDialog(): SemanticsMatcher = hasAnyAncestor(isDialog())

    private fun string(@StringRes id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}

class UsualFontSignOutFitTest : SignOutFitTest(fontScale = 1f)

class LargestFontSignOutFitTest : SignOutFitTest(fontScale = SystemFontScale.LARGEST)
