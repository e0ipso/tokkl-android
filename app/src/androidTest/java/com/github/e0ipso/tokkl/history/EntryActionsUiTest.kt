package com.github.e0ipso.tokkl.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createComposeRule
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeSender.UndoOutcome
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.EntryWithSendState
import com.github.e0ipso.tokkl.favorites.FavoritesModel
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The history's entry actions on screen, against a change queue and an Undo that record what they
 * are asked: the labels TalkBack reads for the menu, Duplicate, Split, the split time, Save as
 * template and Delete, the message whose Undo TalkBack announces, and the question before a delete
 * that a server without restore cannot undo. Save as template shows only with the timer templates
 * capability.
 */
class EntryActionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = CopyOnWriteArrayList<String>()
    private val saves = CopyOnWriteArrayList<List<EntryFields>>()
    private val scope = MainScope()

    @After
    fun tearDown() {
        try { composeRule.checkAccessibility() } finally { scope.cancel() }
    }

    @Test
    fun theMenuAndDeleteHaveTalkBackLabelsAndTalkBackAnnouncesUndo() {
        show(canRestore = true)

        val more = composeRule.onNode(
            hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        composeRule.onAllNodes(hasContentDescription(string(R.string.history_more_actions, string(R.string.history_no_description))))
            .assertCountEquals(1)
        more.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasText(string(R.string.history_delete)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(liveMessage(R.string.history_deleted)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("no question first", 0, composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        val undo = composeRule.onNode(
            hasText(string(R.string.history_undo)) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        undo.performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("Undo ran", TIMEOUT_MS) { calls.size == 2 }
        assertEquals(listOf("delete $ENTRY", "undo $ENTRY"), calls)
    }

    @Test
    fun withoutRestoreDeleteAsksFirstAndTheMessageOffersNoUndo() {
        show(canRestore = false)

        openMenuAndDelete()

        composeRule.onNode(isDialog() and hasAnyDescendant(hasText(string(R.string.history_delete_confirm_title)))).assertExists()
        composeRule.onNode(hasText(string(R.string.history_delete_confirm_message))).assertExists()
        assertEquals("nothing deleted before the answer", emptyList<String>(), calls)

        composeRule.onNode(hasText(string(R.string.history_delete_confirm)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(liveMessage(R.string.history_deleted)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(hasText(string(R.string.history_undo))).assertCountEquals(0)
        assertEquals(listOf("delete $ENTRY"), calls)
    }

    @Test
    fun cancellingTheQuestionDeletesNothing() {
        show(canRestore = false)

        openMenuAndDelete()
        composeRule.onNode(hasText(string(R.string.catalog_cancel)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun undoOfflineSaysItNeedsTheServerAndOffersUndoAgain() {
        show(canRestore = true, undo = UndoOutcome.NeedsServer)

        openMenuAndDelete()
        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(liveMessage(R.string.history_deleted)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(hasText(string(R.string.history_undo)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("the server is needed", TIMEOUT_MS) {
            composeRule.onAllNodes(liveMessage(R.string.history_undo_needs_server)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(string(R.string.history_undo)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil("Undo ran again", TIMEOUT_MS) { calls.size == 3 }
        assertEquals(listOf("delete $ENTRY", "undo $ENTRY", "undo $ENTRY"), calls)
    }

    @Test
    fun duplicateHasATalkBackLabelAndCopiesTheEntry() {
        show(canRestore = true)

        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        // TalkBack reads the item's text as its label.
        composeRule.onNode(hasText(string(R.string.history_duplicate)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(liveMessage(R.string.history_duplicated)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(hasText(string(R.string.history_undo))).assertCountEquals(0)
        assertEquals(listOf("create 10 ${CACHED.start} ${CACHED.stop}"), calls)
    }

    @Test
    fun splitAndItsTimeFieldHaveTalkBackLabelsAndSplitCutsTheEntry() {
        show(canRestore = true)

        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        // TalkBack reads the item's text as its label, and the field's label with the field.
        composeRule.onNode(hasText(string(R.string.history_split)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil("the dialog shows", TIMEOUT_MS) {
            composeRule.onAllNodes(isDialog() and hasAnyDescendant(hasText(string(R.string.history_split_title)))).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.history_split_field))).performTextReplacement("11:15")
        composeRule.onNode(hasText(string(R.string.history_split_confirm)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(liveMessage(R.string.history_split_done)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        assertEquals(
            listOf("create 10 2026-10-15T11:15:00Z ${CACHED.stop}", "update $ENTRY ${CACHED.start} 2026-10-15T11:15:00Z"),
            calls,
        )
    }

    @Test
    fun pinAsFavoriteHasATalkBackLabelAndPinsTheEntry() {
        show(canRestore = true, favorites = favorites(available = MutableStateFlow(true)))

        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        // TalkBack reads the item's text as its label.
        composeRule.onNode(hasText(string(R.string.history_pin)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil("the message shows", TIMEOUT_MS) { composeRule.onAllNodes(liveMessage(R.string.history_pinned)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(hasText(string(R.string.history_undo))).assertCountEquals(0)
        assertEquals(listOf(listOf(EntryFields(description = DESCRIPTION))), saves)
        assertEquals("nothing goes to the queue", emptyList<String>(), calls)
    }

    @Test
    fun withoutTheFavoritesCapabilityTheMenuHasNoPin() {
        val available = MutableStateFlow(false)
        show(canRestore = true, favorites = favorites(available))

        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodes(hasText(string(R.string.history_duplicate)) and hasClickAction()).assertCountEquals(1)
        composeRule.onAllNodes(hasText(string(R.string.history_pin))).assertCountEquals(0)

        // The same menu offers it once the server has favorites, so the menu did draw its items.
        available.value = true
        composeRule.waitUntil("Save as template shows", TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(string(R.string.history_pin)) and hasClickAction()).fetchSemanticsNodes().size == 1
        }
    }

    /** Favorites with none pinned yet, whose server stores every list in [saves]. */
    private fun favorites(available: MutableStateFlow<Boolean>) = FavoritesModel(
        favorites = MutableStateFlow(emptyList()),
        projects = MutableStateFlow(emptyList()),
        tasks = MutableStateFlow(emptyList()),
        available = available,
        save = { list ->
            saves += list
            ApiResult.Success(Unit)
        },
        scope = scope,
        io = Dispatchers.IO,
    )

    private fun openMenuAndDelete() {
        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasText(string(R.string.history_delete)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** A polite live region, as TalkBack announces it, that holds [text] and, when it offers one, Undo. */
    private fun liveMessage(text: Int) =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and hasAnyDescendant(hasText(string(text)))

    private fun show(canRestore: Boolean, undo: UndoOutcome = UndoOutcome.Restored, favorites: FavoritesModel? = null) {
        val model = EntryActionsModel(
            entries = MutableStateFlow(listOf(EntryWithSendState(CACHED, unsent = false))),
            changes = RecordingChanges(),
            undoDelete = { entryId ->
                calls += "undo $entryId"
                undo
            },
            canRestore = { canRestore },
            accountSettings = MutableStateFlow(HISTORY.settings),
            scope = scope,
        )
        composeRule.setContent {
            TokklTheme {
                val state by model.state.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    HistoryContent(
                        state = HISTORY,
                        onLoadOlder = {},
                        onDuplicate = model::duplicate,
                        onSplit = model::split,
                        onDelete = model::delete,
                        pin = favorites?.let { pinAction(model, it) },
                    )
                    EntryActionsHost(
                        state = state,
                        onUndo = model::undo,
                        onNoticeDone = model::noticeDone,
                        onConfirmDelete = model::confirmDelete,
                        onCancelDelete = model::cancelDelete,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        settings = HISTORY.settings,
                        onSplitTextChange = model::setSplitText,
                        onConfirmSplit = model::confirmSplit,
                        onCancelSplit = model::cancelSplit,
                    )
                }
            }
        }
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private inner class RecordingChanges : EntryChanges {
        override suspend fun delete(entryId: Long): Boolean {
            calls += "delete $entryId"
            return true
        }

        override suspend fun start(workspaceId: Long, fields: EntryFields): Long = throw AssertionError("no timer starts")

        override suspend fun stop(entryId: Long, fields: EntryFields?): Boolean = throw AssertionError("no timer stops")

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            calls += "create $workspaceId $start $stop"
            return -1
        }

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean {
            calls += "update $entryId $start $stop"
            return true
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val ENTRY = 1L
        const val DESCRIPTION = "Code review"

        val HISTORY = HistoryState.Shown(
            days = listOf(
                HistoryDay(
                    LocalDate.of(2026, 10, 15),
                    listOf(
                        entry(ENTRY, DESCRIPTION, "10:45", "11:50"),
                        entry(2, "", "09:00", "10:30"),
                    ),
                ),
            ),
            today = LocalDate.of(2026, 10, 15),
            zone = ZoneOffset.UTC,
            settings = AccountSettings(timeZone = "UTC", beginningOfWeek = DayOfWeek.MONDAY, timeResolutionMinutes = null),
            older = OlderDays.End,
        )

        /** The cache's row of the first entry in [HISTORY]. */
        val CACHED = CachedTimeEntry(
            id = ENTRY,
            workspaceId = 10,
            projectId = null,
            taskId = null,
            description = DESCRIPTION,
            start = Instant.parse("2026-10-15T10:45:00Z"),
            stop = Instant.parse("2026-10-15T11:50:00Z"),
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
        )

        fun entry(id: Long, description: String, start: String, stop: String) = HistoryEntry(
            id = id,
            description = description,
            project = null,
            projectColor = null,
            task = null,
            tags = emptyList(),
            billable = false,
            start = Instant.parse("2026-10-15T$start:00Z"),
            stop = Instant.parse("2026-10-15T$stop:00Z"),
            unsent = false,
        )
    }
}
