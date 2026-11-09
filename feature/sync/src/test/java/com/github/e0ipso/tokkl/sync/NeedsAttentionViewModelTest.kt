package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.CountingRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

/** The "needs attention" screen's state, over a queue that stands in for the real one. */
class NeedsAttentionViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val held = MutableStateFlow(emptyList<HeldChange>())
    private val settings = MutableStateFlow<AccountSettings?>(null)
    private val model = NeedsAttentionViewModel(
        attention = NeedsAttention(
            changes = object : HeldChanges {
                override val held: Flow<List<HeldChange>> = this@NeedsAttentionViewModelTest.held

                override suspend fun sendAgain(sequence: Long) = true

                override suspend fun discard(sequence: Long) = true
            },
            refreshes = CountingRefresh(),
            scope = scope,
        ),
        settings = settings,
        scope = scope,
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun theScreenShowsTheHeldChangesInTheAccountsSettings() = runTest {
        val reader = scope.launch { model.state.collect {} }
        held.value = listOf(CHANGE)
        settings.value = SETTINGS

        assertEquals(NeedsAttentionState(listOf(CHANGE), SETTINGS), model.state.first { it.settings != null })
        reader.cancel()
    }

    private companion object {
        val SETTINGS = AccountSettings(
            timeZone = "UTC",
            beginningOfWeek = DayOfWeek.MONDAY,
            timeResolutionMinutes = null,
        )
        val CHANGE = HeldChange(
            sequence = 3,
            kind = ChangeKind.UPDATE,
            entry = TimeEntry(
                id = EntryId(42),
                workspaceId = WorkspaceId(7),
                projectId = null,
                taskId = null,
                description = "Refused",
                timing = EntryTiming.Running(Instant.parse("2026-10-19T07:00:00Z")),
                billable = false,
                tags = emptyList(),
                tagIds = emptyList(),
            ),
            refusal = "No",
        )
    }
}
