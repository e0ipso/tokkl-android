package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.ServerTimerStates
import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class PomodoroWidgetStateTest {
    private val at = Instant.parse("2026-10-29T08:00:00Z")
    private val account = "account-a"
    private val fields = EntryFields("Build widget", 7, 9, listOf("android", "focus"), true)
    private val project = CachedProject(7, 1, null, "Tokkl", "#06aaf5", true, true)
    private val task = CachedTask(9, 1, 7, "Widget", true)
    private val serverEntry = ServerTimeEntry(
        id = 42, workspaceId = 1, projectId = 7, taskId = 9, description = fields.description,
        start = at.toString(), billable = true, tags = fields.tags,
    )
    private val session = ServerTimerStates.session(42, at)
    private val held = ServerTimerStates.held(17, at, pomodoro = session, offset = Duration.ofSeconds(120))
        .let { it.copy(state = it.state.copy(runningEntry = serverEntry)) }
    private val remembered = RememberedPomodoroFields(account, 1, fields.description, 7, 9, fields.tags, true)

    private fun state(
        held: HeldTimerState? = this.held,
        workspace: Long? = 1,
        account: String? = this.account,
        available: Boolean = true,
        remembered: RememberedPomodoroFields? = this.remembered,
        defaults: PomodoroDefaults = PomodoroDefaults.DEFAULT,
        projects: List<CachedProject> = listOf(project),
        tasks: List<CachedTask> = listOf(task),
        now: Instant = at,
        ringing: PomodoroPhaseKey? = null,
    ) = pomodoroWidgetState(workspace, account, available, held, defaults, remembered, projects, tasks, now, ringing)

    @Test fun noAccountAndUnsupportedNeverExposeHeldFields() {
        assertEquals(PomodoroWidgetState.NoAccount, state(account = null))
        assertEquals(PomodoroWidgetState.NoAccount, state(workspace = null))
        assertEquals(PomodoroWidgetState.Unsupported, state(available = false))
    }

    @Test fun idleUsesAllStoredDefaultsAndLastFieldsInTheirWorkspace() {
        val defaults = PomodoroDefaults(45, 8, 20, 3)
        val idle = state(held = null, workspace = 99, defaults = defaults) as PomodoroWidgetState.Idle
        assertEquals(defaults, idle.defaults)
        assertEquals(1L, idle.entry.workspaceId)
        assertEquals(fields, idle.entry.fields)
        assertEquals("Tokkl", idle.entry.project)
        assertEquals("#06aaf5", idle.entry.projectColor)
        assertEquals("Widget", idle.entry.task)
    }

    @Test fun firstUseAndAnotherAccountStartWithEmptyFields() {
        for (last in listOf(null, remembered.copy(account = "retired"))) {
            val idle = state(held = null, remembered = last, workspace = 99) as PomodoroWidgetState.Idle
            assertEquals(EntryFields(), idle.entry.fields)
            assertEquals(99L, idle.entry.workspaceId)
        }
    }

    @Test fun activeKeepsServerCycleRevisionAndEntryFieldsAndCorrectsBothBoundaries() {
        val active = state() as PomodoroWidgetState.Active
        assertEquals(account, active.account)
        assertEquals(17L, active.revision)
        assertEquals(session, active.session)
        assertEquals(at.minusSeconds(120), active.start)
        assertEquals(at.plusSeconds(1380), active.end)
        assertEquals(fields, active.entry.fields)
        assertFalse(active.ended)
        assertEquals("#06aaf5", active.entry.projectColor)
    }

    @Test fun bothRestKindsRetainTheServersCompletedWorkAndUpcomingTransition() {
        for (kind in listOf(PhaseKind.SHORT_REST, PhaseKind.LONG_REST)) {
            val rest = session.copy(
                phase = session.phase.copy(kind = kind, completed = 4), number = 4,
                upcomingPhase = PhaseKind.WORK,
                transition = if (kind == PhaseKind.SHORT_REST) PhaseTransition.SHORT_REST_TO_WORK else PhaseTransition.LONG_REST_TO_WORK,
            )
            val active = state(held = held.copy(state = held.state.copy(pomodoro = rest))) as PomodoroWidgetState.Active
            assertEquals(rest, active.session)
            assertEquals(4, active.session.number)
            assertEquals(4, active.session.longRestEvery)
        }
    }

    @Test fun plannedEndAndMatchingRingRedrawWithoutAdvancingThePhase() {
        val before = state(now = at.plusSeconds(1379)) as PomodoroWidgetState.Active
        val ended = state(now = before.end) as PomodoroWidgetState.Active
        assertTrue(ended.ended)
        assertEquals(before.session, ended.session)
        assertNotEquals(before.key(), ended.key())
        assertTrue((state(ringing = PomodoroPhaseKey(session.phase.start)) as PomodoroWidgetState.Active).ended)
        assertFalse((state(ringing = PomodoroPhaseKey(at.minusSeconds(60))) as PomodoroWidgetState.Active).ended)
    }

    @Test fun missingOrWrongEntryDoesNotLeakAnotherTimersFields() {
        val missing = state(held = held.copy(state = held.state.copy(runningEntry = null))) as PomodoroWidgetState.Active
        val wrong = state(held = held.copy(state = held.state.copy(runningEntry = serverEntry.copy(id = 99)))) as PomodoroWidgetState.Active
        assertEquals(fields, missing.entry.fields)
        assertEquals(fields, wrong.entry.fields)
        val unknown = state(projects = emptyList(), tasks = emptyList()) as PomodoroWidgetState.Active
        assertNull(unknown.entry.project)
        assertNull(unknown.entry.projectColor)
        assertNull(unknown.entry.task)
    }

    @Test fun catalogLabelsAreScopedToTheEntryWorkspace() {
        val active = state(projects = listOf(project.copy(workspaceId = 2)), tasks = listOf(task.copy(workspaceId = 2))) as PomodoroWidgetState.Active
        assertNull(active.entry.project)
        assertNull(active.entry.projectColor)
        assertNull(active.entry.task)
    }

    @Test fun keyFollowsEverythingDrawnAndControlsButNotTheTickingClock() {
        val active = state() as PomodoroWidgetState.Active
        assertEquals(active.key(), state(now = at.plusSeconds(1)).key())
        assertNotEquals(active.key(), active.copy(revision = 18).key())
        assertNotEquals(active.key(), active.copy(account = "account-b").key())
        assertNotEquals(active.key(), state(projects = listOf(project.copy(color = "#aabbcc"))).key())
        assertNotEquals(active.key(), state(projects = listOf(project.copy(name = "Renamed"))).key())
        assertNotEquals(active.key(), active.copy(entry = active.entry.copy(fields = fields.copy(tags = listOf("changed")))).key())
        assertNotEquals(active.key(), active.copy(entry = active.entry.copy(fields = fields.copy(billable = false))).key())
        assertNotEquals(active.key(), active.copy(end = active.end.plusSeconds(1)).key())
        assertNotEquals(state(held = null).key(), state(held = null, defaults = PomodoroDefaults(50, 8, 20, 3)).key())
        assertEquals(64, active.key().length)
    }

    @Test fun rememberedFieldsRoundTripOnlyForTheirAccountAndInvalidDataIsIgnored() {
        assertEquals(remembered, decodeRememberedPomodoroFields(encodeRememberedPomodoroFields(remembered), account))
        assertNull(decodeRememberedPomodoroFields(encodeRememberedPomodoroFields(remembered), "account-b"))
        assertNull(decodeRememberedPomodoroFields("not json", account))
        assertNull(decodeRememberedPomodoroFields("{}", account))
        assertEquals(fields, remembered.fields())
    }

    @Test fun onlyTheSessionsMatchingRunningEntryIsRemembered() {
        assertEquals(remembered, rememberedPomodoroFields(account, held))
        assertNull(rememberedPomodoroFields(account, held.copy(state = held.state.copy(pomodoro = null))))
        assertNull(rememberedPomodoroFields(account, held.copy(state = held.state.copy(runningEntry = null))))
        assertNull(rememberedPomodoroFields(account, held.copy(state = held.state.copy(runningEntry = serverEntry.copy(id = 99)))))
    }

    @Test fun acceptedStatesRememberFieldsBeforeIdleAndRejectStaleAndRetiredResponses() {
        var disk: String? = null
        val cache = object : CurrentEntryCache {
            override fun runningCheckStart() = RunningCheckStart(emptyList())
            override fun checkedRunning(before: RunningCheckStart, server: CachedTimeEntry?) = RunningCheckWrites()
        }
        val source = TimerStateSource(
            initial = null,
            save = { stored ->
                rememberedPomodoroFields(account, stored.held)?.let { disk = encodeRememberedPomodoroFields(it) }
            },
            cache = cache, clock = Clock.fixed(at, ZoneOffset.UTC),
        )
        val slowRead = source.begin()
        assertTrue(source.accept(source.begin(), held.state))
        assertEquals(remembered, disk?.let { decodeRememberedPomodoroFields(it, account) })
        assertFalse(source.accept(slowRead, held.state.copy(revision = 16, runningEntry = serverEntry.copy(description = "Stale"))))
        assertEquals(fields, disk?.let { decodeRememberedPomodoroFields(it, account) }?.fields())
        val lateRead = source.begin()
        assertTrue(source.accept(source.begin(), held.state.copy(revision = 18, pomodoro = null, runningEntry = null)))
        val idle = state(held = source.state.value, remembered = disk?.let { decodeRememberedPomodoroFields(it, account) }) as PomodoroWidgetState.Idle
        assertEquals(fields, idle.entry.fields)
        source.retire()
        disk = null // Sign-out clears only after retirement has waited for accepted writes.
        assertFalse(source.accept(lateRead, held.state.copy(revision = 19)))
        assertNull(disk)
    }

    @Test fun keysDistinguishTagNamesContainingSeparators() {
        val active = state() as PomodoroWidgetState.Active
        fun tags(vararg names: String) = active.copy(entry = active.entry.copy(fields = fields.copy(tags = names.toList()))).key()
        assertNotEquals(tags("a, b"), tags("a", "b"))
    }
}
