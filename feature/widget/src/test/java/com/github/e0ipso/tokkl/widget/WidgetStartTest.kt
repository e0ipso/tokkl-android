package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget's Start and its templates, checked against the account signed in when they run. */
class WidgetStartTest {

    private val queued = mutableListOf<Pair<WorkspaceId, EntryFields>>()

    private suspend fun WidgetStart.runFor(account: String, workspaceId: WorkspaceId? = WorkspaceId(7)) =
        run(account, workspaceId) { workspace, fields -> queued += workspace to fields }

    @Test
    fun aStartDrawnForTheSignedInAccountQueuesItsFields() = runTest {
        val fields = EntryFields("Template", tags = listOf("focus"))

        assertTrue(WidgetStart("account-a", fields).runFor("account-a"))

        assertEquals(listOf(WorkspaceId(7) to fields), queued)
    }

    /** A template drawn before a sign-out and a sign-in names the account before: it starts nothing. */
    @Test
    fun aStartDrawnForAnotherAccountQueuesNothing() = runTest {
        assertFalse(WidgetStart("account-a", EntryFields("Old template")).runFor("account-b"))

        assertEquals(emptyList<Pair<WorkspaceId, EntryFields>>(), queued)
    }

    /** A widget drawn by a version before the account was carried names none. */
    @Test
    fun aStartThatNamesNoAccountQueuesNothing() = runTest {
        assertFalse(WidgetStart("", EntryFields()).runFor("account-a"))

        assertEquals(emptyList<Pair<WorkspaceId, EntryFields>>(), queued)
    }

    @Test
    fun signedOutNothingStarts() = runTest {
        assertFalse(WidgetStart("account-a", EntryFields()).runFor("account-a", workspaceId = null))

        assertEquals(emptyList<Pair<WorkspaceId, EntryFields>>(), queued)
    }
}
