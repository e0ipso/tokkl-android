package com.github.e0ipso.tokkl.timerstate

import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.cache.runningCheckWrites
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the account's timer state source orders, keeps and applies the server's timer states. */
class TimerStateSourceTest {

    private val device = MutableClock(DEVICE_T0)
    private val cache = FakeCache()

    /** What the account's store file holds, as [TimerStateStore] writes it. */
    private var disk: String? = null
    private var saves = 0
    private var runningEnded = 0

    /** A source as a process starts it: from what the disk holds. */
    private fun source() = TimerStateSource(
        initial = disk?.let(::decodeStoredTimerState),
        save = {
            saves++
            disk = encodeStoredTimerState(it)
        },
        cache = cache,
        clock = device,
        onRunningEnded = { runningEnded++ },
    )

    private fun stored(): StoredTimerState? = disk?.let(::decodeStoredTimerState)

    @Test
    fun aSlowerAnswerWithALowerRevisionNeverReplacesTheNewerState() {
        val timer = source()
        val slow = timer.begin()
        val fast = timer.begin()

        assertTrue(timer.accept(fast, state(revision = 6, running = serverEntry(2, "Newer"))))
        assertFalse(timer.accept(slow, state(revision = 5, running = serverEntry(1, "Older"))))

        assertEquals(6L, timer.state.value?.state?.revision)
        assertEquals("the disk keeps the newer state", 6L, stored()?.held?.state?.revision)
        assertEquals("the cache keeps the newer running entry", listOf(2L), cache.running().map { it.id })
    }

    @Test
    fun anEqualRevisionRefreshesTheArrivalTimeAndClockOffset() {
        val timer = source()
        assertTrue(timer.accept(timer.begin(), state(revision = 4, serverTime = DEVICE_T0.plusSeconds(5))))
        assertEquals(Duration.ofSeconds(5), timer.state.value?.clockOffset)

        device.now = DEVICE_T0.plusSeconds(60)
        assertTrue(timer.accept(timer.begin(), state(revision = 4, serverTime = DEVICE_T0.plusSeconds(58))))

        val held = timer.state.value!!
        assertEquals(DEVICE_T0.plusSeconds(60), held.receivedAt)
        assertEquals(Duration.ofSeconds(-2), held.clockOffset)
        assertEquals("the disk holds the same arrival and offset", held, stored()?.held)
    }

    @Test
    fun aFreshProcessReadsTheStateAndItsClockWithoutARequest() {
        val timer = source()
        // The server's clock runs two minutes ahead of the phone's.
        val serverTime = DEVICE_T0.plusSeconds(120)
        val session = state(
            revision = 9,
            serverTime = serverTime,
            running = serverEntry(40, "Focus"),
            pomodoro = pomodoro(plannedEnd = serverTime.plusSeconds(1500)),
        )
        timer.accept(timer.begin(), session)

        val restarted = source()
        device.now = DEVICE_T0.plusSeconds(600)

        assertEquals(timer.state.value, restarted.state.value)
        assertEquals(
            "the countdown keeps the stored offset",
            Duration.ofSeconds(1500 - 600),
            restarted.state.value?.phaseRemaining(device.instant()),
        )
    }

    @Test
    fun aRetiredAccountsLateAnswerRestoresNothing() {
        val old = source()
        old.accept(old.begin(), state(revision = 2, running = serverEntry(1, "Old account")))
        val late = old.begin()
        old.retire()
        // Sign-out clears the cache and the store once the source is retired.
        cache.rows.clear()
        disk = null
        val savesAtSignOut = saves

        assertFalse(old.accept(late, state(revision = 3, running = serverEntry(1, "Old account"))))
        assertNull(old.state.value)
        assertNull("the store stays clear", disk)
        assertEquals(savesAtSignOut, saves)
        assertTrue("the cache stays clear", cache.rows.isEmpty())
        assertNull(old.takeDiscardNotice())

        val next = source()
        assertFalse("a read begun for the old account does not count for the next", next.accept(late, state(revision = 1)))
        assertNull(next.state.value)
    }

    @Test
    fun anIncomingNullCurrentEntryKeepsAPendingLocalStart() {
        val started = cached(-5, "Started on the phone")
        cache.rows[started.id] = started
        cache.pending += started.id
        val timer = source()

        timer.accept(timer.begin(), state(revision = 3, running = null))
        assertEquals(listOf(started), cache.running())

        // The create is answered and remapped while the next read is out.
        val read = timer.begin()
        cache.rows.remove(started.id)
        cache.rows[101] = started.copy(id = 101)
        cache.pending.clear()
        cache.mappings[started.id] = 101

        timer.accept(read, state(revision = 3, running = null))
        assertEquals("the sent start stays", listOf(101L), cache.running().map { it.id })

        timer.accept(timer.begin(), state(revision = 4, running = null))
        assertEquals("a read begun after the send takes the server's stop", emptyList<CachedTimeEntry>(), cache.running())
    }

    @Test
    fun anEditSentDuringTheReadIsNotUndoneByAStaleState() {
        val edited = cached(1, "Edited on the phone")
        cache.rows[1] = edited
        cache.pending += 1L
        val timer = source()
        val read = timer.begin()
        // The sender delivers the edit while the state is on its way.
        cache.pending.clear()

        timer.accept(read, state(revision = 7, running = serverEntry(1, "Before the edit")))
        assertEquals(edited, cache.rows[1])

        timer.accept(timer.begin(), state(revision = 8, running = serverEntry(1, "Edited elsewhere")))
        assertEquals("a read begun after the send takes the server's values", "Edited elsewhere", cache.rows[1]?.description)
    }

    @Test
    fun aTimerStartedElsewhereReplacesTheCachedOneAndAsksForItsStop() {
        cache.rows[1] = cached(1, "Stopped elsewhere")
        val timer = source()

        timer.accept(timer.begin(), state(revision = 2, running = serverEntry(2, "Started elsewhere")))

        assertEquals(listOf(cached(2, "Started elsewhere")), cache.running())
        assertEquals("a refresh brings the stopped entry back with its stop", 1, runningEnded)
    }

    @Test
    fun aNewerStateUpdatesTheCacheAfterAnOlderOneWroteDuringItsRead() {
        cache.rows[1] = cached(1, "Running")
        val timer = source()
        val action = timer.begin()
        val event = timer.begin()

        timer.accept(event, state(revision = 5, running = serverEntry(2, "From the stream")))
        timer.accept(action, state(revision = 6, running = serverEntry(3, "From the action")))

        assertEquals("the cache agrees with the held state", listOf(3L), cache.running().map { it.id })
    }

    @Test
    fun theCountdownAndOvertimeRunOnTheCorrectedClock() {
        // The server's clock runs two minutes ahead of the phone's.
        val end = DEVICE_T0.plusSeconds(120 + 300)
        val held = HeldTimerState(state(revision = 1, serverTime = DEVICE_T0.plusSeconds(120), pomodoro = pomodoro(end)), DEVICE_T0)

        assertEquals(Duration.ofSeconds(300), held.phaseRemaining(DEVICE_T0))
        assertEquals(Duration.ZERO, held.phaseOvertime(DEVICE_T0))
        assertEquals(Duration.ZERO, held.phaseRemaining(DEVICE_T0.plusSeconds(345)))
        assertEquals(Duration.ofSeconds(45), held.phaseOvertime(DEVICE_T0.plusSeconds(345)))
        assertNull("no session, no countdown", HeldTimerState(state(revision = 1), DEVICE_T0).phaseRemaining(DEVICE_T0))
    }

    @Test
    fun aPausedChainIsFrozenAndARunningCloneAddsItsTimeSinceItsStart() {
        val paused = HeldTimerState(state(revision = 1, pause = pause(seconds = 600, cloneEntryId = null)), DEVICE_T0)
        assertEquals(Duration.ofSeconds(600), paused.chainTotal(DEVICE_T0.plusSeconds(3600)))
        assertNull(paused.cloneElapsed(DEVICE_T0.plusSeconds(3600)))

        // The clone started at ENTRY_START. The server's clock runs a minute ahead of the phone's.
        val resumed = HeldTimerState(
            state(revision = 2, serverTime = ENTRY_START.plusSeconds(90), running = serverEntry(12, "Resumed"), pause = pause(600, cloneEntryId = 12)),
            receivedAt = ENTRY_START.plusSeconds(30),
        )
        assertEquals(Duration.ofSeconds(190), resumed.cloneElapsed(ENTRY_START.plusSeconds(130)))
        assertEquals(Duration.ofSeconds(790), resumed.chainTotal(ENTRY_START.plusSeconds(130)))
        assertNull("no chain, no total", HeldTimerState(state(revision = 1), DEVICE_T0).chainTotal(DEVICE_T0))
    }

    @Test
    fun aDiscardedPomodoroGivesOneNoticeAcrossReplaysAndRestarts() {
        val timer = source()
        val discarded = DiscardedPomodoro(77, ENTRY_START, ENTRY_START)
        timer.accept(timer.begin(), state(revision = 3, discarded = discarded))
        assertEquals(discarded, timer.discardNotice.value)

        assertEquals(discarded, timer.takeDiscardNotice())
        assertNull("a second surface finds nothing to show", timer.takeDiscardNotice())
        assertNull(timer.discardNotice.value)

        // The stream sends the same revision again when it reconnects.
        timer.accept(timer.begin(), state(revision = 3, discarded = discarded))
        assertNull(timer.discardNotice.value)

        val restarted = source()
        assertNull("a restart does not show it again", restarted.discardNotice.value)
        assertNull(restarted.takeDiscardNotice())

        val next = DiscardedPomodoro(78, ENTRY_START, ENTRY_START)
        restarted.accept(restarted.begin(), state(revision = 5, discarded = next))
        assertEquals(next, restarted.takeDiscardNotice())
    }

    @Test
    fun aNoticeNotShownBeforeARestartIsShownOnceAfterIt() {
        val discarded = DiscardedPomodoro(77, ENTRY_START, ENTRY_START)
        source().let { it.accept(it.begin(), state(revision = 3, discarded = discarded)) }

        assertEquals(discarded, source().takeDiscardNotice())
        assertNull(source().takeDiscardNotice())
    }

    @Test
    fun theServersTimerStateReadsFieldForField() {
        val body = """
            {"revision":12,"server_time":"2026-10-28T08:13:05.120Z",
             "running_entry":{"id":40,"workspace_id":7,"project_id":null,"task_id":null,"billable":false,
               "start":"2026-10-28T08:00:00+00:00","stop":null,"duration":-1,"description":"Focus",
               "tags":["deep"],"tag_ids":[3],"at":"2026-10-28T08:00:00+00:00","server_deleted_at":null,
               "project_name":null},
             "pause":null,
             "pomodoro":{"work_minutes":25,"short_rest_minutes":5,"long_rest_minutes":15,"long_rest_every":4,
               "phase":{"kind":"shortRest","start":"2026-10-28T08:25:00.000Z","completed":1,"planned_end":"2026-10-28T08:30:00.000Z"},
               "upcoming_phase":"work","transition":"shortRestToWork","number":1,"entry_id":40,
               "pomodoro_start":"2026-10-28T08:00:00.000Z"},
             "discarded_pomodoro":{"entry_id":39,"start":"2026-10-28T07:59:00.000Z","stop":"2026-10-28T07:59:00.000Z"}}
        """.trimIndent()

        val state = ApiJson.decodeFromString(TimerState.serializer(), body)

        assertEquals(Instant.parse("2026-10-28T08:13:05.120Z"), state.serverTime)
        assertEquals(40L, state.runningEntry?.id)
        val pomodoro = state.pomodoro!!
        assertEquals(PhaseKind.SHORT_REST, pomodoro.phase.kind)
        assertEquals(Instant.parse("2026-10-28T08:30:00Z"), pomodoro.phase.plannedEnd)
        assertEquals(PhaseKind.WORK, pomodoro.upcomingPhase)
        assertEquals(PhaseTransition.SHORT_REST_TO_WORK, pomodoro.transition)
        assertEquals(1 to 4, pomodoro.number to pomodoro.longRestEvery)
        assertEquals(39L, state.discardedPomodoro?.entryId)

        val paused = body.replace(
            "\"pause\":null",
            "\"pause\":{\"paused\":true,\"seconds\":600,\"workspace_id\":7,\"description\":\"Writing\",\"project_id\":5," +
                "\"task_id\":null,\"tag_ids\":[3],\"tags\":[\"deep\"],\"billable\":true,\"clone_entry_id\":null}",
        )
        assertEquals(
            TimerPause(true, 600, 7, "Writing", 5, null, listOf(3L), listOf("deep"), true, null),
            ApiJson.decodeFromString(TimerState.serializer(), paused).pause,
        )
    }

    private fun state(
        revision: Long,
        serverTime: Instant = DEVICE_T0,
        running: ServerTimeEntry? = null,
        pause: TimerPause? = null,
        pomodoro: TimerPomodoro? = null,
        discarded: DiscardedPomodoro? = null,
    ) = TimerState(revision, serverTime, running, pause, pomodoro, discarded)

    private fun serverEntry(id: Long, description: String) = ServerTimeEntry(
        id = id,
        workspaceId = WORKSPACE,
        description = description,
        start = "2026-10-28T08:00:00+00:00",
        billable = false,
        tags = emptyList(),
        tagIds = emptyList(),
    )

    private fun cached(id: Long, description: String) =
        CachedTimeEntry(id, WORKSPACE, null, null, description, ENTRY_START, null, false, emptyList(), emptyList())

    private fun pomodoro(plannedEnd: Instant) = TimerPomodoro(
        workMinutes = 25,
        shortRestMinutes = 5,
        longRestMinutes = 15,
        longRestEvery = 4,
        phase = TimerPomodoroPhase(PhaseKind.WORK, ENTRY_START, completed = 0, plannedEnd = plannedEnd),
        upcomingPhase = PhaseKind.SHORT_REST,
        transition = PhaseTransition.WORK_TO_SHORT_REST,
        number = 1,
        entryId = 40,
        pomodoroStart = ENTRY_START,
    )

    private fun pause(seconds: Long, cloneEntryId: Long?) = TimerPause(
        paused = cloneEntryId == null,
        seconds = seconds,
        workspaceId = WORKSPACE,
        description = "Writing",
        projectId = null,
        taskId = null,
        tagIds = emptyList(),
        tags = emptyList(),
        billable = false,
        cloneEntryId = cloneEntryId,
    )

    /**
     * The cache's rows, queue and id mappings in memory, applying an answer with the same
     * reconciliation as AccountCache.checkedRunning.
     */
    private class FakeCache : CurrentEntryCache {
        val rows = LinkedHashMap<Long, CachedTimeEntry>()
        val pending = mutableSetOf<Long>()
        val mappings = mutableMapOf<Long, Long>()

        fun running(): List<CachedTimeEntry> = rows.values.filter { it.stop == null }

        override fun runningCheckStart() = RunningCheckStart(running(), chain = null, pendingIds = pending.toSet())

        override fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?): RunningCheckWrites {
            val mapped = before.pendingIds.filter { it < 0 }.mapNotNull(mappings::get)
            val writes = runningCheckWrites(
                before = before.running,
                now = running(),
                server = server,
                unsent = { it in pending },
                cached = rows::get,
                pendingAtStart = before.pendingIds + mapped,
            )
            writes.delete.forEach(rows::remove)
            writes.upsert?.let { rows[it.id] = it }
            return writes
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private companion object {
        const val WORKSPACE = 7L
        val ENTRY_START: Instant = Instant.parse("2026-10-28T08:00:00Z")
        val DEVICE_T0: Instant = Instant.parse("2026-10-28T09:00:00Z")
    }
}
