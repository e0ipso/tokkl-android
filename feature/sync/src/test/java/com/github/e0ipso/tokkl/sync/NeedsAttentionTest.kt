package com.github.e0ipso.tokkl.sync

import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.HeldChange
import com.github.e0ipso.tokkl.cache.HeldChanges
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.CountingRefresh
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** Send again and Discard on the "needs attention" list when the phone's storage refuses the write. */
class NeedsAttentionTest {

    /** What escaped the writes, which on the phone would reach the main thread and end the app. */
    private val escaped = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(
        Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, thrown -> escaped += thrown },
    )
    private val refreshes = CountingRefresh()
    private val change = HeldChange(
        sequence = 4,
        kind = ChangeKind.UPDATE,
        entry = TimeEntry(
            EntryId(70), WorkspaceId(10), null, null, "Refused",
            EntryTiming.Stopped(Instant.parse("2026-10-19T08:00:00Z"), Instant.parse("2026-10-19T09:00:00Z")),
            false, emptyList(), emptyList(),
        ),
        refusal = "Project archived",
    )

    /** A queue whose storage refuses every write, as a full disk does. The change stays held. */
    private val refusing = object : HeldChanges {
        override val held: Flow<List<HeldChange>> = MutableStateFlow(listOf(change))

        override suspend fun sendAgain(sequence: Long): Boolean = throw IOException("disk full")

        override suspend fun discard(sequence: Long): Boolean = throw IOException("disk full")
    }

    @Test
    fun aSendAgainOrDiscardTheStorageRefusesThrowsNothingAndKeepsTheChangeHeld() {
        val attention = NeedsAttention(refusing, refreshes, scope)

        attention.sendAgain(change)
        attention.discard(change)

        assertEquals(emptyList<Throwable>(), escaped.toList())
        assertEquals("nothing was dropped, so nothing is read again", 0, refreshes.refreshes)
    }
}
