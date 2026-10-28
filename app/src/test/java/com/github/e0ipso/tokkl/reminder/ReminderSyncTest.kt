package com.github.e0ipso.tokkl.reminder

import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.timer.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReminderSyncTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val entry = CachedTimeEntry(1, 1, null, null, "Work", now.minusSeconds(9 * 3600), null, false, emptyList(), emptyList())
    private var input = ReminderInput(entry, ReminderPreferences())
    private var stored: ReminderTimer? = null
    private val waiting = mutableListOf<ReminderTimer>()
    private val posted = mutableListOf<ReminderTimer>()
    private val stopped = mutableListOf<Long>()
    private val mappings = mutableMapOf<Long, Long>()
    private var removals = 0
    private fun sync() = ReminderSync(
        latest = { input }, currentId = { mappings[it] ?: it }, load = { stored }, save = { stored = it },
        schedule = { if (waiting.none { old -> old.id == it.id && old.start == it.start && old.due == it.due }) waiting += it },
        cancel = { waiting.clear() }, post = { posted += it }, remove = {}, stop = { stopped += it; true },
    )

    @Test fun oneDelayAtStartPlusHoursAndNoDelayWhenIdleOrDisabled() = runBlocking {
        val sync = sync()
        sync.reconcile(); sync.reconcile()
        assertEquals(1, waiting.size)
        assertEquals(entry.start.plusSeconds(8 * 3600).toEpochMilli(), waiting.single().due)
        input = input.copy(preferences = ReminderPreferences(false, 8)); sync.reconcile()
        assertTrue(waiting.isEmpty())
        input = input.copy(running = null); sync.reconcile()
        assertNull(stored); assertTrue(waiting.isEmpty())
    }

    @Test fun oncePerTimerAcrossRestartDescriptionEditAndPreferenceChanges() = runBlocking {
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        input = input.copy(running = entry.copy(description = "Edited"))
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        input = input.copy(preferences = ReminderPreferences(true, 1))
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        assertTrue(stored!!.handled)
    }

    @Test fun keepRunningConfirmsAndCorrectingStartResetsIt() = runBlocking {
        val sync = sync()
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        sync.act(1, entry.start.epochSecond, false)
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size); assertTrue(stopped.isEmpty())
        val corrected = entry.copy(start = entry.start.minusSeconds(3600))
        input = input.copy(running = corrected)
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals("old worker cannot deliver corrected timer", 1, posted.size)
        sync.fire(1, corrected.start.epochSecond, corrected.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(2, posted.size)
    }

    @Test fun stopBeforeDueAndStaleActionsNeverStopAnotherTimer() = runBlocking {
        val sync = sync()
        sync.reconcile()
        input = input.copy(running = null)
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertTrue(posted.isEmpty()); assertTrue(waiting.isEmpty())
        input = input.copy(running = entry.copy(id = 2))
        sync.act(1, entry.start.epochSecond, true)
        assertTrue(stopped.isEmpty())
        sync.fire(2, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        sync.act(2, entry.start.epochSecond, true)
        assertEquals(listOf(2L), stopped)
    }

    @Test fun startCorrectionInvalidatesTheOldStopAction() = runBlocking {
        val sync = sync()
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        input = input.copy(running = entry.copy(start = entry.start.plusSeconds(3600)))
        sync.act(1, entry.start.epochSecond, true)
        assertTrue(stopped.isEmpty())
    }

    @Test fun serverIdMappingKeepsConfirmationAndResolvesStop() = runBlocking {
        input = input.copy(running = entry.copy(id = -1))
        sync().fire(-1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        mappings[-1] = 1
        input = input.copy(running = entry)
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        sync().act(-1, entry.start.epochSecond, true)
        assertEquals(listOf(1L), stopped)
    }

    @Test fun anOldThresholdCannotDeliverAfterTheHoursIncrease() = runBlocking {
        val sync = sync()
        sync.reconcile()
        val old = waiting.single()
        input = input.copy(preferences = ReminderPreferences(hours = 12))
        sync.fire(old.id, old.start, old.due)
        assertTrue(posted.isEmpty())
        assertEquals(entry.start.plusSeconds(12 * 3600).toEpochMilli(), waiting.single().due)
    }

    @Test fun pauseGoesToTheServerAfterTheBroadcastEndsAndResumeRemindsFromTheNewSegmentsStart() = runBlocking {
        val events = mutableListOf<String>()
        val sync = pausing { id -> SurfaceTap.Request { events += "pause $id"; TimerActionResult.Done } }
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        val removed = removals

        val result = sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true, beforeRequest = { events += "broadcast ends" })

        assertEquals(TimerActionResult.Done, result)
        assertEquals(listOf("broadcast ends", "pause 1"), events)
        assertTrue(stopped.isEmpty())
        assertEquals("the server has it, so the reminder goes", removed + 1, removals)
        input = input.copy(running = null)
        sync.reconcile()
        assertTrue("a paused timer has no reminder", waiting.isEmpty())
        input = input.copy(running = entry.copy(id = 2, start = now))
        sync.reconcile()
        assertEquals(2L, waiting.single().id)
        assertEquals(now.plusSeconds(8 * 3600).toEpochMilli(), waiting.single().due)
    }

    @Test fun anOfflineOrRefusedPauseKeepsTheReminderAndSaysWhy() = runBlocking {
        var answer: TimerActionResult = TimerActionResult.Offline
        val sync = pausing { SurfaceTap.Request { answer } }
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        val removed = removals

        assertEquals(TimerActionResult.Offline, sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true))
        answer = TimerActionResult.Refused
        assertEquals(TimerActionResult.Refused, sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true))

        assertEquals("the reminder stays for another try", removed, removals)
        assertTrue("Pause never queues a Stop", stopped.isEmpty())
    }

    @Test fun aPauseNotOfferedNowOrForAnotherTimerSendsNothing() = runBlocking {
        val asked = mutableListOf<Long>()
        // No timer API, or a Pomodoro session: the app offers no Pause request.
        val sync = pausing { id -> asked += id; null }
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        val removed = removals
        var ended = false

        assertNull(sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true, beforeRequest = { ended = true }))
        assertEquals(listOf(1L), asked)
        input = input.copy(running = entry.copy(id = 2))
        assertNull(sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true))

        assertEquals("another timer runs now, so Pause is not even asked", listOf(1L), asked)
        assertFalse(ended)
        assertEquals(removed, removals)
    }

    private fun pausing(pause: suspend (Long) -> SurfaceTap.Request?) = ReminderSync(
        latest = { input }, currentId = { mappings[it] ?: it }, load = { stored }, save = { stored = it },
        schedule = { waiting += it }, cancel = { waiting.clear() }, post = { posted += it }, remove = { removals++ },
        stop = { stopped += it; true }, pause = pause,
    )

    @Test fun futureTimerWaitsAndChangingHoursReplacesTheDelay() = runBlocking {
        input = input.copy(running = entry.copy(start = now))
        val sync = sync()
        sync.reconcile()
        assertTrue(posted.isEmpty())
        input = input.copy(preferences = ReminderPreferences(hours = 2))
        sync.reconcile()
        assertEquals(now.plusSeconds(7200).toEpochMilli(), waiting.single().due)
        sync.forget()
        assertNull(stored); assertTrue(waiting.isEmpty())
    }
}
