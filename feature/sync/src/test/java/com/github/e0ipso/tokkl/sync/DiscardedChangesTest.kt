package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/** The count with a store in memory. Unconfined runs each write to the end inside the call. */
class DiscardedChangesTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test fun discardsAggregateUntilDismissedAndAreSavedForTheNextProcess() {
        var saved = 2
        val discarded = DiscardedChanges(InMemoryValueStore(saved) { saved = it }, scope)
        discarded.add(1)
        discarded.add(2)
        assertEquals(5, discarded.state.value)
        assertEquals(5, saved)
        val restarted = DiscardedChanges(InMemoryValueStore(saved) { saved = it }, scope)
        assertEquals(5, restarted.state.value)
        restarted.dismiss()
        assertEquals(0, restarted.state.value)
        assertEquals(0, saved)
    }

    @Test fun nonpositiveCountsLeaveTheNoticeAlone() {
        var writes = 0
        val discarded = DiscardedChanges(InMemoryValueStore(1) { writes++ }, scope)
        discarded.add(0)
        discarded.add(-1)
        assertEquals(1, discarded.state.value)
        assertEquals(0, writes)
    }
}
