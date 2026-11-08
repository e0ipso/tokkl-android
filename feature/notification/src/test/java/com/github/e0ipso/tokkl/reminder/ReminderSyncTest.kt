package com.github.e0ipso.tokkl.reminder

import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.preferences.ReminderPreferences
import com.github.e0ipso.tokkl.storage.valueStore
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderSyncTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val entry = TimeEntry(
        EntryId(1), WorkspaceId(1), null, null, "Work",
        now.minusSeconds(
            9 * 3600,
        ),
        null, false, emptyList(), emptyList(),
    )
    private var input = ReminderInput(entry, ReminderPreferences())
    private var stored: ReminderTimer? = null
    private val waiting = mutableListOf<ReminderTimer>()
    private val posted = mutableListOf<ReminderTimer>()
    private val stopped = mutableListOf<EntryId>()
    private val mappings = mutableMapOf<EntryId, EntryId>()
    private var removals = 0
    private fun sync() = ReminderSync(
        inputs = { input },
        ids = { mappings[it] ?: it },
        timers = valueStore(load = { stored }, save = { stored = it }),
        delays = object : ReminderDelays {
            override fun schedule(timer: ReminderTimer) {
                if (waiting.none { old -> old.id == timer.id && old.start == timer.start && old.due == timer.due }) {
                    waiting += timer
                }
            }

            override fun cancel() = waiting.clear()
        },
        notice = notice(onRemove = {}),
        stop = {
            stopped += it
            true
        },
        pause = { null },
    )

    /** The reminder notification as a list of posts, with [onRemove] for its removals. */
    private fun notice(onRemove: () -> Unit) = object : ReminderNotice {
        override suspend fun post(timer: ReminderTimer) {
            posted += timer
        }

        override fun remove() = onRemove()
    }

    @Test fun oneDelayAtStartPlusHoursAndNoDelayWhenIdleOrDisabled() = runTest {
        val sync = sync()
        sync.reconcile()
        sync.reconcile()
        assertEquals(1, waiting.size)
        assertEquals(entry.start.plusSeconds(8 * 3600).toEpochMilli(), waiting.single().due)
        input = input.copy(preferences = ReminderPreferences(false, 8))
        sync.reconcile()
        assertTrue(waiting.isEmpty())
        input = input.copy(running = null)
        sync.reconcile()
        assertNull(stored)
        assertTrue(waiting.isEmpty())
    }

    @Test fun oncePerTimerAcrossRestartDescriptionEditAndPreferenceChanges() = runTest {
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        input = input.copy(running = entry.copy(description = "Edited"))
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        input = input.copy(preferences = ReminderPreferences(true, 1))
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        assertTrue(stored!!.handled)
    }

    @Test fun keepRunningConfirmsAndCorrectingStartResetsIt() = runTest {
        val sync = sync()
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        sync.act(1, entry.start.epochSecond, false)
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        assertTrue(stopped.isEmpty())
        val corrected = entry.copy(start = entry.start.minusSeconds(3600))
        input = input.copy(running = corrected)
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals("old worker cannot deliver corrected timer", 1, posted.size)
        sync.fire(
            1,
            corrected.start.epochSecond,
            corrected.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L,
        )
        assertEquals(2, posted.size)
    }

    @Test fun stopBeforeDueAndStaleActionsNeverStopAnotherTimer() = runTest {
        val sync = sync()
        sync.reconcile()
        input = input.copy(running = null)
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertTrue(posted.isEmpty())
        assertTrue(waiting.isEmpty())
        input = input.copy(running = entry.copy(id = EntryId(2)))
        sync.act(1, entry.start.epochSecond, true)
        assertTrue(stopped.isEmpty())
        sync.fire(2, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        sync.act(2, entry.start.epochSecond, true)
        assertEquals(listOf(EntryId(2)), stopped)
    }

    @Test fun startCorrectionInvalidatesTheOldStopAction() = runTest {
        val sync = sync()
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        input = input.copy(running = entry.copy(start = entry.start.plusSeconds(3600)))
        sync.act(1, entry.start.epochSecond, true)
        assertTrue(stopped.isEmpty())
    }

    @Test fun serverIdMappingKeepsConfirmationAndResolvesStop() = runTest {
        input = input.copy(running = entry.copy(id = EntryId(-1)))
        sync().fire(-1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        mappings[EntryId(-1)] = EntryId(1)
        input = input.copy(running = entry)
        sync().fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        assertEquals(1, posted.size)
        sync().act(-1, entry.start.epochSecond, true)
        assertEquals(listOf(EntryId(1)), stopped)
    }

    @Test fun anOldThresholdCannotDeliverAfterTheHoursIncrease() = runTest {
        val sync = sync()
        sync.reconcile()
        val old = waiting.single()
        input = input.copy(preferences = ReminderPreferences(hours = 12))
        sync.fire(old.id, old.start, old.due)
        assertTrue(posted.isEmpty())
        assertEquals(entry.start.plusSeconds(12 * 3600).toEpochMilli(), waiting.single().due)
    }

    @Test fun pauseGoesToTheServerAfterTheBroadcastEndsAndResumeRemindsFromTheNewSegmentsStart() = runTest {
        val events = mutableListOf<String>()
        val sync = pausing { id ->
            SurfaceTap.Request {
                events += "pause $id"
                TimerActionResult.Done
            }
        }
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        val removed = removals

        val result = sync.act(
            1,
            entry.start.epochSecond,
            stopTimer = false,
            pauseTimer = true,
            beforeRequest = { events += "broadcast ends" },
        )

        assertEquals(TimerActionResult.Done, result)
        assertEquals(listOf("broadcast ends", "pause 1"), events)
        assertTrue(stopped.isEmpty())
        assertEquals("the server has it, so the reminder goes", removed + 1, removals)
        input = input.copy(running = null)
        sync.reconcile()
        assertTrue("a paused timer has no reminder", waiting.isEmpty())
        input = input.copy(running = entry.copy(id = EntryId(2), start = now))
        sync.reconcile()
        assertEquals(2L, waiting.single().id)
        assertEquals(now.plusSeconds(8 * 3600).toEpochMilli(), waiting.single().due)
    }

    @Test fun anOfflineOrRefusedPauseKeepsTheReminderAndSaysWhy() = runTest {
        var answer: TimerActionResult = TimerActionResult.Offline
        val sync = pausing { SurfaceTap.Request { answer } }
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        val removed = removals

        assertEquals(
            TimerActionResult.Offline,
            sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true),
        )
        answer = TimerActionResult.Refused
        assertEquals(
            TimerActionResult.Refused,
            sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true),
        )

        assertEquals("the reminder stays for another try", removed, removals)
        assertTrue("Pause never queues a Stop", stopped.isEmpty())
    }

    @Test fun aPauseNotOfferedNowOrForAnotherTimerSendsNothing() = runTest {
        val asked = mutableListOf<EntryId>()
        // No timer API, or a Pomodoro session: the app offers no Pause request.
        val sync = pausing { id ->
            asked += id
            null
        }
        sync.fire(1, entry.start.epochSecond, entry.start.epochSecond * 1000 + input.preferences.hours * 3_600_000L)
        val removed = removals
        var ended = false

        assertNull(
            sync.act(
                1,
                entry.start.epochSecond,
                stopTimer = false,
                pauseTimer = true,
                beforeRequest = { ended = true },
            ),
        )
        assertEquals(listOf(EntryId(1)), asked)
        input = input.copy(running = entry.copy(id = EntryId(2)))
        assertNull(sync.act(1, entry.start.epochSecond, stopTimer = false, pauseTimer = true))

        assertEquals("another timer runs now, so Pause is not even asked", listOf(EntryId(1)), asked)
        assertFalse(ended)
        assertEquals(removed, removals)
    }

    private fun pausing(pause: ReminderPause) = ReminderSync(
        inputs = { input },
        ids = { mappings[it] ?: it },
        timers = valueStore(
            load = { stored },
            save = { stored = it },
        ),
        delays = object : ReminderDelays {
            override fun schedule(timer: ReminderTimer) {
                waiting += timer
            }

            override fun cancel() = waiting.clear()
        },
        notice = notice(onRemove = { removals++ }),
        stop = {
            stopped += it
            true
        },
        pause = pause,
    )

    @Test fun futureTimerWaitsAndChangingHoursReplacesTheDelay() = runTest {
        input = input.copy(running = entry.copy(start = now))
        val sync = sync()
        sync.reconcile()
        assertTrue(posted.isEmpty())
        input = input.copy(preferences = ReminderPreferences(hours = 2))
        sync.reconcile()
        assertEquals(now.plusSeconds(7200).toEpochMilli(), waiting.single().due)
        sync.forget()
        assertNull(stored)
        assertTrue(waiting.isEmpty())
    }
}
