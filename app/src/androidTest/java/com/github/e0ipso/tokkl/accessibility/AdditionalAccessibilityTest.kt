package com.github.e0ipso.tokkl.accessibility

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SignedInFrame
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.sync.NeedsAttentionModel
import com.github.e0ipso.tokkl.sync.NeedsAttentionScreen
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.Instant
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** More and the actual Needs attention screen, without server or account fixtures. */
abstract class AdditionalAccessibilityTest(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        SystemFontScale(SystemFontScale.LARGEST, largestDisplaySize = true),
    ).around(compose)

    @After
    fun disposeScope() {
        scope.cancel()
    }

    @Test
    fun everyMoreChoiceIsReachableAtMaximumSizes() {
        val current = mutableStateOf(TopLevelPage.TIMER)
        show {
            SignedInFrame(current = current.value, onOpen = { current.value = it }, timerRunning = true) {
                Text(context.getString(current.value.label))
            }
        }
        for (page in listOf(TopLevelPage.CLIENTS, TopLevelPage.TAGS, TopLevelPage.TRASH)) {
            compose.onNode(bottomBarItem(R.string.nav_more)).performClick()
            val choice = compose.onNode(hasText(context.getString(page.label)) and hasClickAction())
            choice.performScrollTo().assertIsDisplayed()
            assertVisibleTextFits()
            choice.performTouchInput { click() }
            compose.waitUntil("More opens $page", 5_000) { current.value == page }
            compose.checkAccessibility()
        }
    }

    @Test
    fun refusedChangesExposeTheirReasonAndReachableActionsAtMaximumSizes() {
        val held = MutableStateFlow(emptyList<PendingChange>())
        val settings = MutableStateFlow<AccountSettings?>(AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null))
        var edited: Long? = null
        var resent: Long? = null
        val model = NeedsAttentionModel(
            held = held,
            release = { resent = it; true },
            drop = { sequence -> held.value = held.value.filterNot { it.sequence == sequence }; true },
            afterDiscard = {},
            scope = scope,
        )
        show {
            NeedsAttentionScreen(model, settings, onEdit = { edited = it }, onBack = {})
        }
        compose.onNodeWithText(context.getString(R.string.needs_attention_empty)).assertIsDisplayed()
        assertVisibleTextFits()
        for (kind in ChangeKind.entries) {
            val change = PendingChange(
                sequence = 1, kind = kind, entryId = 42, workspaceId = 7,
                projectId = null, taskId = null, description = "A refused change with a long description",
                start = Instant.parse("2026-10-19T07:00:00Z"), stop = null, billable = true,
                tags = emptyList(), tagIds = emptyList(), held = true,
                refusal = "Time entries can not be longer than 999 hours",
            )
            held.value = listOf(change)
            val reason = context.getString(R.string.needs_attention_reason, checkNotNull(change.refusal))
            compose.onNodeWithText(reason).performScrollTo().assertIsDisplayed()
            assertVisibleTextFits()
            if (kind != ChangeKind.DELETE) {
                action(R.string.needs_attention_edit)
                assertEquals(42L, edited)
            } else {
                compose.onNodeWithText(context.getString(R.string.needs_attention_edit)).assertDoesNotExist()
            }
            resent = null
            action(R.string.needs_attention_send_again)
            compose.waitUntil("Send again was reachable", 5_000) { resent == change.sequence }
            action(R.string.needs_attention_discard)
            compose.onNodeWithText(context.getString(R.string.needs_attention_empty)).assertIsDisplayed()
            assertEquals(emptyList<PendingChange>(), held.value)
            compose.checkAccessibility()
        }
    }

    private fun action(label: Int) {
        compose.onNode(hasText(context.getString(label)) and hasClickAction())
            .performScrollTo().assertIsDisplayed().performTouchInput { click() }
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.activityRule.scenario.onActivity {
            WindowCompat.enableEdgeToEdge(it.window)
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent { TokklTheme(darkTheme = dark) { content() } }
    }

    private fun assertVisibleTextFits() {
        compose.checkAccessibility()
        val nodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true,
        ).fetchSemanticsNodes()
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            for (layout in layouts) assertFalse("text is cut off: ${layout.layoutInput.text}", layout.didOverflowHeight)
        }
    }
}

class LightAdditionalAccessibilityTest : AdditionalAccessibilityTest(dark = false)
class DarkAdditionalAccessibilityTest : AdditionalAccessibilityTest(dark = true)
