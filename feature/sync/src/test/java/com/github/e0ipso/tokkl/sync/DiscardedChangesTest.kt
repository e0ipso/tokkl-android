package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscardedChangesTest {
    @Test fun discardsAggregateUntilDismissedAndAreSavedForTheNextProcess() {
        var saved = 2
        val discarded = DiscardedChanges(InMemoryValueStore(saved) { saved = it })
        discarded.add(1)
        discarded.add(2)
        assertEquals(5, discarded.state.value)
        assertEquals(5, saved)
        val restarted = DiscardedChanges(InMemoryValueStore(saved) { saved = it })
        assertEquals(5, restarted.state.value)
        restarted.dismiss()
        assertEquals(0, restarted.state.value)
        assertEquals(0, saved)
    }

    @Test fun nonpositiveCountsLeaveTheNoticeAlone() {
        var writes = 0
        val discarded = DiscardedChanges(InMemoryValueStore(1) { writes++ })
        discarded.add(0)
        discarded.add(-1)
        assertEquals(1, discarded.state.value)
        assertEquals(0, writes)
    }
}
