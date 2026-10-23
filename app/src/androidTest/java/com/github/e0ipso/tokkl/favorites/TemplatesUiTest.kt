package com.github.e0ipso.tokkl.favorites

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.entry.EntryLabels
import com.github.e0ipso.tokkl.navigation.AppNavigationBar
import com.github.e0ipso.tokkl.navigation.AppNavigationRail
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Timer templates without a server (docs/PRD.md T5): the panel popup, the management form, and
 * the More destination. FavoritesServerTest covers the same flows against Tokkl.
 */
class TemplatesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun thePopupHidesWithoutTheCapabilityThenStartsInSavedOrder() {
        var state by mutableStateOf(FavoritesState(available = false, favorites = listOf(REPORT, WEBSITE)))
        var started: EntryFields? = null
        composeRule.setContent {
            TokklTheme {
                TemplatesPopup(
                    state = state,
                    timerFields = EntryFields(description = "Now"),
                    onStart = { started = it },
                    onSave = { _, done -> done(null) },
                    onManage = {},
                    offline = false,
                )
            }
        }
        composeRule.onNode(hasContentDescription(string(R.string.favorites_open))).assertDoesNotExist()

        state = state.copy(available = true)
        composeRule.waitForIdle()
        openPopup()
        val expected = listOf(
            string(R.string.favorites_start_spoken, string(R.string.favorites_with_project, "Write the report", "Website")),
            string(R.string.favorites_start_spoken, "Website"),
        )
        assertEquals(expected, startLabels())
        composeRule.onNode(hasContentDescription(expected.first()) and hasClickAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(REPORT.fields, started)
        composeRule.onNode(hasContentDescription(expected.first())).assertDoesNotExist()
    }

    @Test
    fun anEmptyPopupOffersSaveAndManage() {
        var managed = false
        var saved: EntryFields? = null
        composeRule.setContent {
            TokklTheme {
                TemplatesPopup(
                    state = FavoritesState(available = true),
                    timerFields = EntryFields(),
                    onStart = {},
                    onSave = { fields, done ->
                        saved = fields
                        done(null)
                    },
                    onManage = { managed = true },
                    offline = false,
                )
            }
        }
        openPopup()
        composeRule.onNode(hasText(string(R.string.favorites_empty))).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.favorites_save)) and hasClickAction()).assertIsEnabled()
        click(R.string.favorites_save)
        assertEquals(EntryFields(), saved)
        click(R.string.favorites_manage)
        assertTrue(managed)
        composeRule.onNode(hasText(string(R.string.favorites_manage))).assertDoesNotExist()
    }

    @Test
    fun saveKeepsThePopupOpen() {
        var saved: EntryFields? = null
        val current = EntryFields(description = "Now", tags = listOf("call"), billable = true)
        composeRule.setContent {
            TokklTheme {
                TemplatesPopup(
                    state = FavoritesState(available = true, favorites = listOf(REPORT)),
                    timerFields = current,
                    onStart = {},
                    onSave = { fields, done ->
                        saved = fields
                        done(null)
                    },
                    onManage = {},
                    offline = false,
                )
            }
        }
        openPopup()
        click(R.string.favorites_save)
        assertEquals(current, saved)
        composeRule.onNode(hasText(string(R.string.history_pinned))).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.favorites_manage)) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun aFullListDisablesSave() {
        val nine = (1..MAX_FAVORITES).map { FavoriteItem(EntryFields(description = "Favorite $it"), EntryLabels()) }
        composeRule.setContent {
            TokklTheme {
                TemplatesPopup(
                    state = FavoritesState(available = true, favorites = nine),
                    timerFields = EntryFields(description = "A tenth"),
                    onStart = {},
                    onSave = { _, done -> done(null) },
                    onManage = {},
                    offline = false,
                )
            }
        }
        openPopup()
        val full = context.resources.getQuantityString(R.plurals.favorites_full, MAX_FAVORITES, MAX_FAVORITES)
        composeRule.onNode(hasText(full)).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.favorites_save))).assertIsNotEnabled()
    }

    @Test
    fun cancelLeavesTheListAndSaveStoresANewTemplate() {
        var added: EntryFields? = null
        showManagement(FavoritesState(available = true), onAdd = { fields, done ->
            added = fields
            done(null)
        })
        click(R.string.favorites_new)
        composeRule.onNode(hasSetTextAction()).performTextInput("Fresh")
        click(R.string.favorites_cancel)
        assertNull(added)
        composeRule.onNode(hasText(string(R.string.favorites_new)) and hasClickAction()).assertIsDisplayed()

        click(R.string.favorites_new)
        composeRule.onNode(hasSetTextAction()).performTextInput("Fresh")
        click(R.string.favorites_save_action)
        assertEquals(EntryFields(description = "Fresh"), added)
    }

    @Test
    fun editingSavesInPlaceAndCancelLeavesTheRow() {
        var replaced: Pair<Int, EntryFields>? = null
        showManagement(
            FavoritesState(available = true, favorites = listOf(REPORT)),
            onReplace = { index, fields, done ->
                replaced = index to fields
                done(null)
            },
        )
        clickDescription(R.string.favorites_edit_spoken, "Write the report")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Rewritten")
        click(R.string.favorites_cancel)
        assertNull(replaced)
        composeRule.onNode(hasText("Write the report")).assertIsDisplayed()

        clickDescription(R.string.favorites_edit_spoken, "Write the report")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Rewritten")
        click(R.string.favorites_save_action)
        assertEquals(0 to REPORT.fields.copy(description = "Rewritten"), replaced)
    }

    @Test
    fun offlineDisablesManagement() {
        showManagement(FavoritesState(available = true, favorites = listOf(REPORT)), offline = true)
        composeRule.onNode(hasText(string(R.string.favorites_offline))).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(string(R.string.favorites_delete, "Write the report")) and hasClickAction()).assertIsNotEnabled()
        composeRule.onNode(hasText(string(R.string.favorites_new)) and hasClickAction()).assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.favorites_move_up, "Write the report")) and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun theBarListsTemplatesFirstAndHidesThemWithoutTheCapability() = assertMoreOrder(rail = false)

    @Test
    fun theRailListsTemplatesFirstAndHidesThemWithoutTheCapability() = assertMoreOrder(rail = true)

    private fun assertMoreOrder(rail: Boolean) {
        var hidden by mutableStateOf(setOf(TopLevelPage.TEMPLATES))
        var opened by mutableStateOf<TopLevelPage?>(null)
        composeRule.setContent {
            TokklTheme {
                if (rail) {
                    AppNavigationRail(current = TopLevelPage.TIMER, onOpen = { opened = it }, hidden = hidden)
                } else {
                    AppNavigationBar(current = TopLevelPage.TIMER, onOpen = { opened = it }, hidden = hidden)
                }
            }
        }
        openMore()
        composeRule.onNode(isSelectable() and hasText(string(R.string.nav_templates))).assertDoesNotExist()
        composeRule.onNode(isSelectable() and hasText(string(R.string.nav_clients))).assertIsDisplayed()

        hidden = emptySet()
        composeRule.waitForIdle()
        assertEquals(moreLabels(), sheetLabels())
        composeRule.onNode(isSelectable() and hasText(string(R.string.nav_templates))).performClick()
        assertEquals(TopLevelPage.TEMPLATES, opened)
    }

    private fun showManagement(
        state: FavoritesState,
        offline: Boolean = false,
        onAdd: (EntryFields, (FavoritesProblem?) -> Unit) -> Unit = { _, done -> done(null) },
        onReplace: (Int, EntryFields, (FavoritesProblem?) -> Unit) -> Unit = { _, _, done -> done(null) },
    ) {
        composeRule.setContent {
            TokklTheme {
                TemplatesContent(
                    state = state,
                    projects = emptyList(),
                    tasks = emptyList(),
                    offline = offline,
                    done = null,
                    onDoneShown = {},
                    onAdd = onAdd,
                    onReplace = onReplace,
                    onRemove = {},
                    onMove = { _, _ -> },
                )
            }
        }
    }

    private fun openPopup() {
        composeRule.onNode(hasContentDescription(string(R.string.favorites_open)) and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    private fun openMore() {
        composeRule.onNode(hasText(string(R.string.nav_more)) and hasClickAction()).performClick()
        composeRule.waitUntil("the More sheet opens", 5_000) {
            composeRule.onAllNodes(hasText(string(R.string.nav_more)) and isHeading()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(@StringRes label: Int) {
        composeRule.onNode(hasText(string(label)) and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    private fun clickDescription(@StringRes label: Int, name: String) {
        composeRule.onNode(hasContentDescription(string(label, name)) and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    /** The popup's start items, top to bottom, as TalkBack reads them. */
    private fun startLabels(): List<String> =
        composeRule.onAllNodes(hasClickAction() and hasContentDescription("Start ", substring = true))
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.ContentDescription].joinToString(" ") }

    private fun sheetLabels(): List<String> =
        composeRule.onAllNodes(sheetItem())
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config[SemanticsProperties.Text].joinToString(" ") { it.text } }

    private fun sheetItem() =
        isSelectable() and (
            hasText(string(R.string.nav_templates)) or hasText(string(R.string.nav_clients)) or
                hasText(string(R.string.nav_tags)) or hasText(string(R.string.nav_trash))
            )

    private fun moreLabels(): List<String> =
        listOf(R.string.nav_templates, R.string.nav_clients, R.string.nav_tags, R.string.nav_trash).map { string(it) }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private companion object {
        val REPORT = FavoriteItem(
            EntryFields(description = "Write the report", projectId = 1, tags = listOf("call"), billable = true),
            EntryLabels(project = "Website"),
        )
        val WEBSITE = FavoriteItem(EntryFields(projectId = 1), EntryLabels(project = "Website"))
    }
}
