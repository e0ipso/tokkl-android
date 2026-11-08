package com.github.e0ipso.tokkl.accessibility

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.bottomBarItem
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.clients.ClientsRoute
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.navhost.SignedInFrame
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.label
import com.github.e0ipso.tokkl.navigation.topLevelPages
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.sync.NeedsAttention
import com.github.e0ipso.tokkl.sync.NeedsAttentionScreen
import com.github.e0ipso.tokkl.sync.NeedsAttentionViewModel
import com.github.e0ipso.tokkl.tags.TagsRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.DayOfWeek
import java.time.Instant
import com.github.e0ipso.tokkl.core.ui.R as UiR
import com.github.e0ipso.tokkl.feature.sync.R as SyncR

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
    fun pomodoroAndItsSpokenRunningMarkFitAtMaximumSizes() {
        val running = mutableStateOf(true)
        show {
            SignedInFrame(
                pages = topLevelPages,
                current = PomodoroRoute,
                onOpen = {},
                pomodoroRunning = running.value,
            ) {
                Text(context.getString(UiR.string.pomodoro_title))
            }
        }
        val marker = bottomBarItem(UiR.string.pomodoro_title) and
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.nav_pomodoro_running),
            )
        compose.onNode(marker).assertIsDisplayed()
        assertVisibleTextFits()
        compose.runOnIdle { running.value = false }
        assertEquals(0, compose.onAllNodes(marker).fetchSemanticsNodes().size)
        compose.onNode(bottomBarItem(UiR.string.pomodoro_title)).assertIsDisplayed()
        compose.checkAccessibility()
    }

    @Test
    fun everyMoreChoiceIsReachableAtMaximumSizes() {
        val current = mutableStateOf<Route>(Route.Timer)
        show {
            SignedInFrame(
                pages = topLevelPages,
                current = current.value,
                onOpen = { current.value = it },
                timerRunning = true,
            ) {
                Text(context.getString(current.value.label))
            }
        }
        for (page in listOf(
            ReportsRoute,
            TemplatesRoute,
            ClientsRoute,
            TagsRoute,
            TrashRoute,
        )) {
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
        val held = MutableStateFlow(emptyList<HeldChange>())
        val settings = MutableStateFlow<AccountSettings?>(
            AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null),
        )
        var edited: EntryId? = null
        var resent: Long? = null
        val refused = held
        val attention = NeedsAttention(
            changes = object : HeldChanges {
                override val held: Flow<List<HeldChange>> = refused

                override suspend fun sendAgain(sequence: Long): Boolean {
                    resent = sequence
                    return true
                }

                override suspend fun discard(sequence: Long): Boolean {
                    refused.value = refused.value.filterNot { it.sequence == sequence }
                    return true
                }
            },
            afterDiscard = {},
            scope = scope,
        )
        val model = NeedsAttentionViewModel(attention, settings, scope)
        show {
            NeedsAttentionScreen(model, onEdit = { edited = it }, onBack = {})
        }
        compose.onNodeWithText(context.getString(SyncR.string.needs_attention_empty)).assertIsDisplayed()
        assertVisibleTextFits()
        for (kind in ChangeKind.entries) {
            val change = HeldChange(
                sequence = 1,
                kind = kind,
                entry = TimeEntry(
                    id = EntryId(42), workspaceId = WorkspaceId(7),
                    projectId = null, taskId = null, description = "A refused change with a long description",
                    start = Instant.parse("2026-10-19T07:00:00Z"), stop = null, billable = true,
                    tags = emptyList(), tagIds = emptyList(),
                ),
                refusal = "Time entries can not be longer than 999 hours",
            )
            held.value = listOf(change)
            val reason = context.getString(SyncR.string.needs_attention_reason, checkNotNull(change.refusal))
            compose.onNodeWithText(reason).performScrollTo().assertIsDisplayed()
            assertVisibleTextFits()
            val edit = compose.onNode(
                hasContentDescription(
                    context.getString(SyncR.string.needs_attention_edit_named, change.entry.description),
                ) and hasClickAction(),
            )
            if (kind != ChangeKind.DELETE) {
                edit.performScrollTo().assertIsDisplayed().performTouchInput { click() }
                assertEquals(EntryId(42), edited)
            } else {
                edit.assertDoesNotExist()
            }
            resent = null
            action(SyncR.string.needs_attention_send_again)
            compose.waitUntil("Send again was reachable", 5_000) { resent == change.sequence }
            action(SyncR.string.needs_attention_discard)
            compose.onNodeWithText(context.getString(SyncR.string.needs_attention_empty)).assertIsDisplayed()
            assertEquals(emptyList<HeldChange>(), held.value)
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
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
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
