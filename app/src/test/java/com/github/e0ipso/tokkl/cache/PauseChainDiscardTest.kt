package com.github.e0ipso.tokkl.cache

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exact segment accounting and pre-start snapshots for refused pause-chain creates. */
class PauseChainDiscardTest {

    private val start: Instant = Instant.parse("2026-10-23T09:00:00Z")
    private val fields = EntryFields("Pause the timer", projectId = 3, taskId = 9, tags = listOf("android"), billable = true)
    private val changed = EntryFields("Changed", projectId = 4, taskId = 10, tags = listOf("other"), billable = false)

    @Test
    fun discardingARefusedStartOfAPausedSegmentLeavesTheStoredTimeFromBeforeThatStart() {
        val segment = entry(id = -5, start = start.plusSeconds(900), stop = start.plusSeconds(1_200))
        val overcounted = chain(stoppedSeconds = PAUSED_TOTAL + SEGMENT_SECONDS, runningId = null)

        val result = pauseChainAfterDiscardedCreate(overcounted, segment.id, listOf(checkpoint(segment.id)))
        val after = result.chain

        assertEquals(overcounted.revision + 1, after.revision)
        assertEquals(overcounted.epoch + 1, after.epoch)
        assertNull(after.runningId)
        assertEquals(PAUSED_TOTAL, after.stoppedSeconds)
        assertEquals(1, result.removedSlot)
        val paused = timerControls(running = null, chain = after, session = SESSION) as TimerControls.Paused
        assertEquals("the stored time is the total from before the refused start", PAUSED_TOTAL, paused.seconds)
        assertEquals(fields, paused.fields)

        val cloneId = -8L
        val resumedChain = after.copy(revision = after.revision + 1, epoch = after.epoch + 1, runningId = cloneId)
        val clone = segment.copy(id = cloneId, start = start.plusSeconds(1_500), stop = null)
        val resumed = timerControls(EntryWithSendState(clone, unsent = true), resumedChain, SESSION) as TimerControls.Running
        assertEquals("Resume goes on from the paused total", PAUSED_TOTAL, resumed.accumulatedSeconds)
        assertEquals(fields, resumedChain.fields)
        assertEquals(fields, EntryFields(clone.description, clone.projectId, clone.taskId, clone.tags, clone.billable))
        assertEquals(after.chainId, resumed.target.chainId)
    }

    @Test
    fun discardingARefusedStartThatIsStillRunningPausesAgainAtTheSameTotal() {
        val clone = entry(id = -5, start = start.plusSeconds(900), stop = null)
        val paused = chain(stoppedSeconds = PAUSED_TOTAL, runningId = clone.id)
        val segment = checkpoint(clone.id).copy(seconds = 0, paused = false)

        val after = pauseChainAfterDiscardedCreate(paused, clone.id, listOf(segment)).chain

        assertNull(after.runningId)
        assertEquals(PAUSED_TOTAL, after.stoppedSeconds)
        assertEquals(fields, after.fields)
        val shown = timerControls(running = null, chain = after, session = SESSION) as TimerControls.Paused
        assertEquals(PAUSED_TOTAL, shown.seconds)
    }

    @Test
    fun discardingAStoppedCreateTheChainDidNotPauseLeavesTheStoredTime() {
        val manual = entry(id = -3, start = start, stop = start.plusSeconds(100), description = "Added by hand")
        val paused = chain(stoppedSeconds = PAUSED_TOTAL, runningId = null)
        val sameSnapshot = manual.copy(description = fields.description)

        val differentSnapshot = pauseChainAfterDiscardedCreate(paused, manual.id, emptyList())
        val notPaused = pauseChainAfterDiscardedCreate(paused, sameSnapshot.id, listOf(checkpoint(-5)))

        assertEquals(paused, differentSnapshot.chain)
        assertEquals(paused, notPaused.chain)
        assertNull(notPaused.removedSlot)
        assertNull(notPaused.rebased)
    }

    @Test
    fun aLaterKeptSegmentWithTheSameSnapshotKeepsOnlyItsOwnSeconds() {
        val earlier = checkpoint(-5)
        val later = checkpoint(-8, slot = 2, order = 6).copy(seconds = 120)
        val paused = chain(stoppedSeconds = PAUSED_TOTAL + SEGMENT_SECONDS + later.seconds, runningId = null)

        val after = pauseChainAfterDiscardedCreate(paused, earlier.entryId, listOf(later, earlier)).chain

        assertEquals(PAUSED_TOTAL + later.seconds, after.stoppedSeconds)
        assertEquals(paused.fields, after.fields)
        assertNull(after.runningId)
    }

    @Test
    fun editingThePausedSnapshotDoesNotHideTheDiscardedContribution() {
        val edited = chain(PAUSED_TOTAL + SEGMENT_SECONDS, null).copy(fields = changed, workspaceId = 99)
        val segment = checkpoint(-5)

        val after = pauseChainAfterDiscardedCreate(edited, segment.entryId, listOf(segment)).chain

        assertEquals(PAUSED_TOTAL, after.stoppedSeconds)
        assertEquals("all pre-start fields return", fields, after.fields)
        assertEquals(WORKSPACE, after.workspaceId)
        assertNull(after.runningId)
    }

    @Test
    fun discardingAnEarlierPausePreservesALaterRunningSegmentAndRebasesItsSnapshot() {
        val earlier = checkpoint(-5)
        val later = checkpoint(-8, slot = 2, order = 6).copy(seconds = 0, paused = false, fields = changed)
        val running = chain(PAUSED_TOTAL + SEGMENT_SECONDS, later.entryId).copy(fields = changed)

        val after = pauseChainAfterDiscardedCreate(running, earlier.entryId, listOf(earlier, later))

        assertEquals(PAUSED_TOTAL, after.chain.stoppedSeconds)
        assertEquals(later.entryId, after.chain.runningId)
        assertEquals(fields, after.chain.fields)
        assertEquals(later.copy(fields = fields), after.rebased)
    }

    @Test
    fun discardingTheNextSegmentCannotRestoreAnAlreadyDiscardedSegmentsFields() {
        val earlier = checkpoint(-5)
        val later = checkpoint(-8, slot = 2, order = 6).copy(seconds = 120, fields = changed)
        val paused = chain(PAUSED_TOTAL + SEGMENT_SECONDS + later.seconds, null).copy(fields = changed)
        val first = pauseChainAfterDiscardedCreate(paused, earlier.entryId, listOf(earlier, later))

        val second = pauseChainAfterDiscardedCreate(first.chain, later.entryId, listOf(requireNotNull(first.rebased)))

        assertEquals(PAUSED_TOTAL, second.chain.stoppedSeconds)
        assertEquals(fields, second.chain.fields)
        assertNull(second.chain.runningId)
    }

    @Test
    fun discardingTheLatestSegmentFirstAlsoRestoresTheOriginalSnapshot() {
        val earlier = checkpoint(-5)
        val later = checkpoint(-8, slot = 2, order = 6).copy(seconds = 120, fields = changed)
        val paused = chain(PAUSED_TOTAL + SEGMENT_SECONDS + later.seconds, null).copy(fields = changed)
        val first = pauseChainAfterDiscardedCreate(paused, later.entryId, listOf(earlier, later))

        val second = pauseChainAfterDiscardedCreate(first.chain, earlier.entryId, listOf(earlier))

        assertEquals(PAUSED_TOTAL, second.chain.stoppedSeconds)
        assertEquals(fields, second.chain.fields)
    }

    @Test
    fun aRepeatedDiscardHasNoFurtherEffect() {
        val segment = checkpoint(-5)
        val paused = chain(PAUSED_TOTAL + SEGMENT_SECONDS, null)
        val first = pauseChainAfterDiscardedCreate(paused, segment.entryId, listOf(segment))

        val second = pauseChainAfterDiscardedCreate(first.chain, segment.entryId, emptyList())

        assertEquals(first.chain, second.chain)
        assertNull(second.removedSlot)
        assertNull(second.rebased)
    }

    @Test
    fun aCheckpointFromAnotherChainCannotClaimAnUnrelatedCoincidentSnapshot() {
        val paused = chain(PAUSED_TOTAL, null)
        val other = checkpoint(-5).copy(chainId = CHAIN + 1)

        val after = pauseChainAfterDiscardedCreate(paused, other.entryId, listOf(other))

        assertEquals(paused, after.chain)
        assertNull(after.removedSlot)
    }

    @Test
    fun aShortPausedSegmentRestoresMetadataWithoutSubtractingSeconds() {
        val segment = checkpoint(-5).copy(seconds = 0)
        val paused = chain(PAUSED_TOTAL, null).copy(fields = changed)

        val after = pauseChainAfterDiscardedCreate(paused, segment.entryId, listOf(segment)).chain

        assertEquals(PAUSED_TOTAL, after.stoppedSeconds)
        assertEquals(fields, after.fields)
    }

    @Test
    fun discardUsesTheRecordedContributionRatherThanRecomputingAnEditedSpan() {
        val segment = checkpoint(-5).copy(seconds = 240)
        val paused = chain(PAUSED_TOTAL + segment.seconds, null)

        val after = pauseChainAfterDiscardedCreate(paused, segment.entryId, listOf(segment)).chain

        assertEquals(PAUSED_TOTAL, after.stoppedSeconds)
    }

    @Test
    fun aRunningChainWithoutACheckpointStillPausesByExactIdentity() {
        val running = chain(PAUSED_TOTAL, -5)

        val after = pauseChainAfterDiscardedCreate(running, -5, emptyList()).chain

        assertNull(after.runningId)
        assertEquals(PAUSED_TOTAL, after.stoppedSeconds)
        assertEquals(fields, after.fields)
    }

    private fun checkpoint(entryId: Long, slot: Int = 1, order: Long = 2) = PauseSegmentCheckpoint(
        slot = slot,
        chainId = CHAIN,
        order = order,
        entryId = entryId,
        seconds = SEGMENT_SECONDS,
        paused = true,
        workspaceId = WORKSPACE,
        fields = fields,
    )

    private fun entry(id: Long, start: Instant, stop: Instant?, description: String = fields.description) = CachedTimeEntry(
        id = id,
        workspaceId = WORKSPACE,
        projectId = fields.projectId,
        taskId = fields.taskId,
        description = description,
        start = start,
        stop = stop,
        billable = fields.billable,
        tags = fields.tags,
        tagIds = emptyList(),
    )

    private fun chain(stoppedSeconds: Long, runningId: Long?) = PauseChain(
        chainId = CHAIN,
        revision = 8,
        epoch = 12,
        stoppedSeconds = stoppedSeconds,
        runningId = runningId,
        workspaceId = WORKSPACE,
        fields = fields,
    )

    private companion object {
        const val SESSION = 11L
        const val CHAIN = 22L
        const val WORKSPACE = 7L
        const val PAUSED_TOTAL = 600L
        const val SEGMENT_SECONDS = 300L
    }
}
