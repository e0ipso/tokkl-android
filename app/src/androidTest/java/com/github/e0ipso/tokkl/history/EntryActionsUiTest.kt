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
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.ChangeSender.UndoOutcome
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The history's entry actions on screen, against a change queue and an Undo that record what they
 * are asked: the labels TalkBack reads for the menu and Delete, the message whose Undo TalkBack
 * announces, and the question before a delete that a server without restore cannot undo.
 */
class EntryActionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = CopyOnWriteArrayList<String>()
    private val scope = MainScope()

    @After
    fun tearDown() = scope.cancel()

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

    private fun openMenuAndDelete() {
        composeRule.onNode(hasContentDescription(string(R.string.history_more_actions, DESCRIPTION)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasText(string(R.string.history_delete)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** A polite live region, as TalkBack announces it, that holds [text] and, when it offers one, Undo. */
    private fun liveMessage(text: Int) =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and hasAnyDescendant(hasText(string(text)))

    private fun show(canRestore: Boolean, undo: UndoOutcome = UndoOutcome.Restored) {
        val model = EntryActionsModel(
            changes = RecordingChanges(),
            undoDelete = { entryId ->
                calls += "undo $entryId"
                undo
            },
            canRestore = { canRestore },
            scope = scope,
        )
        composeRule.setContent {
            TokklTheme {
                val state by model.state.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    HistoryContent(state = HISTORY, onLoadOlder = {}, onDelete = model::delete)
                    EntryActionsHost(
                        state = state,
                        onUndo = model::undo,
                        onNoticeDone = model::noticeDone,
                        onConfirmDelete = model::confirmDelete,
                        onCancelDelete = model::cancelDelete,
                        modifier = Modifier.align(Alignment.BottomCenter),
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

        override suspend fun create(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long =
            throw AssertionError("nothing is created")

        override suspend fun update(entryId: Long, fields: EntryFields, start: Instant, stop: Instant?): Boolean =
            throw AssertionError("nothing is edited")
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
