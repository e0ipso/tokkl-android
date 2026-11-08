package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.session.AccountSettings
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
    private val held = MutableStateFlow(emptyList<PendingChange>())
    private val settings = MutableStateFlow<AccountSettings?>(null)
    private val model = NeedsAttentionViewModel(
        attention = NeedsAttention(
            changes = object : HeldChanges {
                override val held: Flow<List<PendingChange>> = this@NeedsAttentionViewModelTest.held

                override suspend fun sendAgain(sequence: Long) = true

                override suspend fun discard(sequence: Long) = true
            },
            afterDiscard = {},
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
        val CHANGE = PendingChange(
            sequence = 3,
            kind = ChangeKind.UPDATE,
            entryId = 42,
            workspaceId = 7,
            projectId = null,
            taskId = null,
            description = "Refused",
            start = Instant.parse("2026-10-19T07:00:00Z"),
            stop = null,
            billable = false,
            tags = emptyList(),
            tagIds = emptyList(),
            held = true,
            refusal = "No",
        )
    }
}
