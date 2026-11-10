package com.github.e0ipso.tokkl.entries

import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * Where the entry form's edit of a stopped entry goes: the cache's row while the cache holds it,
 * the whole entry for one the cache does not hold or holds from before its window.
 */
class SessionEntriesTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val changes = RecordingChanges()
    private val entries = sessionEntries(
        MutableStateFlow(emptyList()),
        MutableStateFlow<AccountSettings?>(AccountSettings("UTC", DayOfWeek.MONDAY, null)),
        scope,
        Clock.fixed(NOW, ZoneOffset.UTC),
        changes = changes,
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun anEditTheCacheTakesGoesToItsRow() {
        assertTrue(save(onPhone = true, opened = NOW.minusSeconds(HOUR)))

        assertEquals(listOf("update 7"), changes.calls)
    }

    @Test
    fun anEditOfAnEntryThePhoneDoesNotHoldGoesWhole() {
        changes.updates = false

        assertTrue(save(onPhone = false, opened = NOW.minusSeconds(HOUR)))

        assertEquals(listOf("updateOlder 7 3"), changes.calls)
    }

    @Test
    fun anOlderEntryTheCacheDroppedAfterItsSendGoesWhole() {
        changes.updates = false

        assertTrue(save(onPhone = true, opened = Instant.parse("2026-09-01T09:00:00Z")))

        assertEquals(listOf("update 7", "updateOlder 7 3"), changes.calls)
    }

    @Test
    fun anEntryThatLeftTheCacheInsideItsWindowSavesNothing() {
        changes.updates = false

        assertFalse(save(onPhone = true, opened = NOW.minusSeconds(HOUR)))

        assertEquals(listOf("update 7"), changes.calls)
    }

    /**
     * A delete made before a sign-in again, which may be another account, hides nothing after it:
     * the account signing in can hold an entry under the same local id.
     */
    @Test
    fun aSignInAgainForgetsTheDeletesMadeBefore() {
        val rows = MutableStateFlow<List<EntryWithSendState>>(emptyList())
        val accounts = MutableStateFlow(1)
        val signedIn = sessionEntries(
            rows,
            MutableStateFlow<AccountSettings?>(AccountSettings("UTC", DayOfWeek.MONDAY, null)),
            scope,
            Clock.fixed(NOW, ZoneOffset.UTC),
            accounts = accounts,
        )
        scope.launch { signedIn.history.collect {} }
        val before = TimeEntry(
            EntryId(-5), WorkspaceId(3), null, null, "Before",
            EntryTiming.Stopped(NOW.minusSeconds(2 * HOUR), NOW.minusSeconds(HOUR)), false, emptyList(), emptyList(),
        )
        signedIn.deleted(before)

        accounts.value = 2
        val after = before.copy(description = "After")
        rows.value = listOf(EntryWithSendState(after, unsent = true))

        val shown = signedIn.history.value as HistoryEntries.Shown
        assertEquals(listOf(after), shown.current.map { it.entry })
    }

    private fun save(onPhone: Boolean, opened: Instant) = runBlocking {
        entries.saveEdit(
            EntryId(7),
            WorkspaceId(3),
            opened,
            onPhone,
            EntryFields(description = "Write"),
            EntryTiming.Stopped(opened, opened.plusSeconds(HOUR)),
        )
    }

    /** Records the edits; [updates] is what an edit of the cache's row answers. */
    private class RecordingChanges : EntryChanges {
        val calls = mutableListOf<String>()
        var updates = true

        override suspend fun currentId(entryId: EntryId): EntryId = entryId

        override suspend fun update(entryId: EntryId, fields: EntryFields, timing: EntryTiming): Boolean {
            calls += "update $entryId"
            return updates
        }

        override suspend fun updateOlder(
            entryId: EntryId,
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ) {
            calls += "updateOlder $entryId $workspaceId"
        }

        override suspend fun create(
            workspaceId: WorkspaceId,
            fields: EntryFields,
            timing: EntryTiming.Ended,
        ): EntryId = throw AssertionError("unexpected create")

        override suspend fun delete(entryId: EntryId): Boolean = throw AssertionError("unexpected delete")

        override suspend fun delete(entry: TimeEntry): TimeEntry? = throw AssertionError("unexpected delete")
    }

    private companion object {
        const val HOUR = 3_600L
        val NOW: Instant = Instant.parse("2026-10-19T10:00:00Z")
    }
}
