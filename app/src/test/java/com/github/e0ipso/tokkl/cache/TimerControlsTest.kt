package com.github.e0ipso.tokkl.cache

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** What every timer surface shows for the running entry, the pause chain and the session. */
class TimerControlsTest {

    private val start: Instant = Instant.parse("2026-10-23T09:00:00Z")
    private val kept = EntryFields("Write the PRD", projectId = 7, taskId = 9, tags = listOf("docs"), billable = true)

    @Test
    fun aRunningEntryOutsideAChainShowsItsOwnTime() {
        val running = entry(id = 42)

        val shown = timerControls(EntryWithSendState(running, unsent = true), chain = null, session = SESSION)

        assertEquals(TimerControls.Running(running, unsent = true, accumulatedSeconds = 0, target = TimerTarget(SESSION, entryId = 42)), shown)
    }

    @Test
    fun theChainsCloneAddsTheTimeTheStoppedSegmentsStore() {
        val clone = entry(id = -5)
        val chain = chain(runningId = -5, stoppedSeconds = 600)

        val shown = timerControls(EntryWithSendState(clone, unsent = false), chain, SESSION) as TimerControls.Running

        assertEquals(600L, shown.accumulatedSeconds)
        assertEquals(TimerTarget(SESSION, chainId = CHAIN, revision = 4, entryId = -5), shown.target)
        assertEquals("the stored time plus the running segment's", 600L + 90, shown.seconds(start.plusSeconds(90)))
    }

    @Test
    fun correctingTheClonesStartChangesOnlyItsOwnTime() {
        val corrected = entry(id = -5).copy(start = start.minusSeconds(300))

        val shown = timerControls(EntryWithSendState(corrected, unsent = true), chain(runningId = -5, stoppedSeconds = 600), SESSION) as TimerControls.Running

        assertEquals(600L, shown.accumulatedSeconds)
        assertEquals(600L + 300 + 90, shown.seconds(start.plusSeconds(90)))
    }

    @Test
    fun aRunningEntryThatIsNotTheCloneWinsAndAddsNothing() {
        val elsewhere = entry(id = 77)

        val paused = timerControls(EntryWithSendState(elsewhere, unsent = false), chain(runningId = null, stoppedSeconds = 600), SESSION)
        val replaced = timerControls(EntryWithSendState(elsewhere, unsent = false), chain(runningId = -5, stoppedSeconds = 600), SESSION)

        val expected = TimerControls.Running(elsewhere, unsent = false, accumulatedSeconds = 0, target = TimerTarget(SESSION, entryId = 77))
        assertEquals("a timer started elsewhere while paused", expected, paused)
        assertEquals("a timer that replaced the clone", expected, replaced)
    }

    @Test
    fun aPausedChainShowsItsFrozenTotalAndWhatResumeStarts() {
        val shown = timerControls(running = null, chain = chain(runningId = null, stoppedSeconds = 3_725), session = SESSION)

        assertEquals(TimerControls.Paused(seconds = 3_725, workspaceId = 3, fields = kept, target = TimerTarget(SESSION, chainId = CHAIN, revision = 4)), shown)
    }

    @Test
    fun aChainWhoseCloneNoLongerRunsShowsNothing() {
        assertEquals(TimerControls.Idle, timerControls(running = null, chain = chain(runningId = -5, stoppedSeconds = 600), session = SESSION))
    }

    @Test
    fun withNoTimerAndNoChainTheControlsAreIdle() {
        assertEquals(TimerControls.Idle, timerControls(running = null, chain = null, session = SESSION))
    }

    @Test
    fun theClockNeverCountsBackBeforeTheSegmentsStart() {
        val shown = timerControls(EntryWithSendState(entry(id = -5), unsent = false), chain(runningId = -5, stoppedSeconds = 600), SESSION) as TimerControls.Running

        assertEquals(600L, shown.seconds(start.minusSeconds(30)))
    }

    private fun entry(id: Long) = CachedTimeEntry(
        id = id,
        workspaceId = 3,
        projectId = kept.projectId,
        taskId = kept.taskId,
        description = kept.description,
        start = start,
        stop = null,
        billable = kept.billable,
        tags = kept.tags,
        tagIds = emptyList(),
    )

    private fun chain(runningId: Long?, stoppedSeconds: Long) = PauseChain(
        chainId = CHAIN,
        revision = 4,
        epoch = 9,
        stoppedSeconds = stoppedSeconds,
        runningId = runningId,
        workspaceId = 3,
        fields = kept,
    )

    private companion object {
        const val SESSION = 1_234L
        const val CHAIN = 5_678L
    }
}
