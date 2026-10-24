package com.github.e0ipso.tokkl.picker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.cache.CacheContents
import com.github.e0ipso.tokkl.cache.CacheDatabase
import com.github.e0ipso.tokkl.cache.CachedClient
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTag
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * At the largest font scale, every kind of option in the picker can still be scrolled to, read
 * whole and tapped: an archived project and task, a tag, and adding a typed tag. The rows come
 * from the cache alone, as offline.
 */
class EntityPickerFontScaleTest {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }


    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST, largestDisplaySize = true))
        .around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as TokklApplication
    private var picked by mutableStateOf(Picked())

    @Test
    fun everyKindOfOptionCanBeReachedAndTappedAtTheLargestFontScale() {
        // Another instance on the same file, as in AccountCacheServerTest.
        CacheDatabase.open(context).run {
            try {
                cache().refresh { _, _ -> ROWS }
            } finally {
                close()
            }
        }
        val original = Picked(OLD_SITE, OLD_TASK, listOf("legacy"))
        picked = original
        composeRule.setContent {
            TokklTheme {
                EntityPicker(
                    cache = app.cache,
                    workspaceId = WORKSPACE,
                    original = original,
                    picked = picked,
                    onPickedChange = { picked = it },
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }

        val archived = context.getString(R.string.picker_archived)
        option("A project with a long name that wraps at large sizes").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(Picked(LONG_NAME, null, listOf("legacy")), picked)
        option("Old site").assert(hasText(archived)).assertIsDisplayed()
        option("Old task").assert(hasText(archived)).assertIsDisplayed().performClick()
        assertEquals(Picked(OLD_SITE, OLD_TASK, listOf("legacy")), picked)
        option("Old task").assertIsSelected()
        option("zeta").assertIsDisplayed().performClick()
        option("zeta").assertIsOn()

        composeRule.onNode(hasSetTextAction()).performTextInput("client call")
        scrolledTo(hasText(context.getString(R.string.picker_add_tag, "client call"))).assertIsDisplayed().performClick()
        assertEquals(listOf("legacy", "zeta", "client call"), picked.tags)
    }

    private fun option(name: String): SemanticsNodeInteraction =
        scrolledTo(hasText(name) and (hasRole(Role.RadioButton) or hasRole(Role.Checkbox)))

    private fun scrolledTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        return composeRule.onNode(matcher)
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private companion object {
        const val WORKSPACE = 1L
        const val ACME = 10L
        const val OLD_SITE = 21L
        const val LONG_NAME = 22L
        const val OLD_TASK = 32L

        val ROWS = CacheContents(
            projects = listOf(
                CachedProject(LONG_NAME, WORKSPACE, ACME, "A project with a long name that wraps at large sizes", "#0b83d9", true, false),
                CachedProject(OLD_SITE, WORKSPACE, ACME, "Old site", "#e36a00", active = false, billable = false),
            ) + (1L..8L).map { CachedProject(100 + it, WORKSPACE, null, "Project $it", "#2da608", true, false) },
            clients = listOf(CachedClient(ACME, WORKSPACE, "Acme", archived = false)),
            tasks = listOf(CachedTask(OLD_TASK, WORKSPACE, OLD_SITE, "Old task", active = false)),
            tags = listOf(CachedTag(1, WORKSPACE, "legacy"), CachedTag(2, WORKSPACE, "zeta")),
        )
    }
}
