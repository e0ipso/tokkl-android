package com.github.e0ipso.tokkl.trash

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.tags.Tag
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsContent
import com.github.e0ipso.tokkl.tags.TagsData
import com.github.e0ipso.tokkl.tags.TagsState
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Stable row identity and reading order deep in both lists at Android's largest font scale. */
class LazyCatalogAccessibilityTest {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        compose.checkAccessibility()
    }

    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(SystemFontScale.LARGEST, largestDisplaySize = true)).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun trashKeepsItsVisibleEntryAndReadingOrderAfterRemovingAnEarlierEntry() {
        val entries = mutableStateOf(List(1000) { index ->
            TrashEntry(index.toLong(), "Trash $index", "2026-10-19T08:00:00Z", "2026-10-19T08:01:00Z", 60)
        })
        compose.activityRule.scenario.onActivity { WindowCompat.enableEdgeToEdge(it.window) }
        compose.setContent {
            TokklTheme(darkTheme = false) { TrashContent(TrashState(entries = entries.value), false, null, TrashActions()) }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(242)
        val row = compose.onNode(hasText("Trash 240", substring = true))
        val before = row.assertIsDisplayed().getBoundsInRoot()
        val restore = context.getString(R.string.trash_restore_named, "Trash 240")
        val purge = context.getString(R.string.trash_purge_named, "Trash 240")
        compose.onNode(hasContentDescription(restore)).assertIsDisplayed()
        compose.onNode(hasContentDescription(purge)).assertIsDisplayed()
        assertReadingOrder("Trash 240", restore, purge)
        compose.runOnUiThread { entries.value = entries.value.drop(1) }
        assertEquals("the same entry stays at the same position", before, row.getBoundsInRoot())
        assertReadingOrder("Trash 240", restore, purge)
    }

    @Test
    fun tagsKeepTheirVisibleTagAndReadingOrderAfterRemovingAnEarlierTag() {
        val tags = mutableStateOf(List(500) { index -> Tag(index.toLong(), 7, "Tag $index") })
        compose.activityRule.scenario.onActivity { WindowCompat.enableEdgeToEdge(it.window) }
        compose.setContent {
            TokklTheme(darkTheme = false) { TagsContent(TagsState(data = TagsData(tags.value)), false, emptyList(), TagsActions()) }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(242)
        val row = compose.onNode(hasText("Tag 240"))
        val before = row.assertIsDisplayed().getBoundsInRoot()
        val delete = context.getString(R.string.tags_delete_named, "Tag 240")
        compose.onNode(hasContentDescription(delete)).assertIsDisplayed()
        assertReadingOrder("Tag 240", delete)
        compose.runOnUiThread { tags.value = tags.value.drop(1) }
        assertEquals("the same tag stays at the same position", before, row.getBoundsInRoot())
        assertReadingOrder("Tag 240", delete)
    }

    private fun assertReadingOrder(vararg labels: String) {
        fun flatten(node: SemanticsNode): List<String> = buildList {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
            node.children.forEach { addAll(flatten(it)) }
        }
        val spoken = flatten(compose.onRoot().fetchSemanticsNode())
        val positions = labels.map { label -> spoken.indexOfFirst { it == label } }
        assertTrue("every row label is in the merged semantics tree: $labels", positions.all { it >= 0 })
        assertEquals("the entry name precedes its controls", positions.sorted(), positions)
    }
}
