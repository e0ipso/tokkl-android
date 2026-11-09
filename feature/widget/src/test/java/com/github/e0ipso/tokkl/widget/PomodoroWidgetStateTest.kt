package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.CurrentEntryCache
import com.github.e0ipso.tokkl.cache.RunningCheckStart
import com.github.e0ipso.tokkl.cache.RunningCheckWrites
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryWithSendState
import com.github.e0ipso.tokkl.model.PhaseKind
import com.github.e0ipso.tokkl.model.PhaseTransition
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.timerstate.ServerTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.held
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PomodoroWidgetStateTest {
    private val at = Instant.parse("2026-10-29T08:00:00Z")
    private val account = "account-a"
    private val fields = EntryFields("Build widget", ProjectId(7), TaskId(9), listOf("android", "focus"), true)
    private val project = Project(ProjectId(7), WorkspaceId(1), null, "Tokkl", "#06aaf5", true, true)
    private val task = Task(TaskId(9), WorkspaceId(1), ProjectId(7), "Widget", true)
    private val serverEntry = ServerTimeEntry(
        id = 42,
        workspaceId = 1,
        projectId = 7,
        taskId = 9,
        description = fields.description,
        start = at.toString(),
        billable = true,
        tags = fields.tags,
    )
    private val session = ServerTimerStates.session(EntryId(42), at)
    private val server = ServerTimerStates.state(17, at, pomodoro = session, offset = Duration.ofSeconds(120))
        .copy(runningEntry = serverEntry)
    private val held = server.held(at)
    private val remembered = RememberedPomodoroFields(
        account,
        WorkspaceId(1),
        fields.description,
        ProjectId(7),
        TaskId(9),
        fields.tags,
        true,
    )

    private fun state(
        held: HeldTimerState? = this.held,
        workspace: Long? = 1,
        account: String? = this.account,
        available: Boolean = true,
        remembered: RememberedPomodoroFields? = this.remembered,
        defaults: PomodoroDefaults = PomodoroDefaults.DEFAULT,
        projects: List<Project> = listOf(project),
        tasks: List<Task> = listOf(task),
        now: Instant = at,
        ringing: PomodoroPhaseKey? = null,
    ) = pomodoroWidgetState(
        workspace?.let(
            ::WorkspaceId,
        ),
        account, available, held, defaults, remembered, projects, tasks, now, ringing,
    )

    @Test fun noAccountAndUnsupportedNeverExposeHeldFields() {
        assertEquals(PomodoroWidgetState.NoAccount, state(account = null))
        assertEquals(PomodoroWidgetState.NoAccount, state(workspace = null))
        assertEquals(PomodoroWidgetState.Unsupported, state(available = false))
    }

    @Test fun idleUsesAllStoredDefaultsAndLastFieldsInTheirWorkspace() {
        val defaults = PomodoroDefaults(45, 8, 20, 3)
        val idle = state(held = null, workspace = 99, defaults = defaults) as PomodoroWidgetState.Idle
        assertEquals(defaults, idle.defaults)
        assertEquals(WorkspaceId(1), idle.entry.workspaceId)
        assertEquals(fields, idle.entry.fields)
        assertEquals("Tokkl", idle.entry.project)
        assertEquals("#06aaf5", idle.entry.projectColor)
        assertEquals("Widget", idle.entry.task)
    }

    @Test fun firstUseAndAnotherAccountStartWithEmptyFields() {
        for (last in listOf(null, remembered.copy(account = "retired"))) {
            val idle = state(held = null, remembered = last, workspace = 99) as PomodoroWidgetState.Idle
            assertEquals(EntryFields(), idle.entry.fields)
            assertEquals(WorkspaceId(99), idle.entry.workspaceId)
        }
    }

    @Test fun activeKeepsServerCycleRevisionAndEntryFieldsAndCorrectsBothBoundaries() {
        val active = state() as PomodoroWidgetState.Active
        assertEquals(account, active.account)
        assertEquals(17L, active.revision)
        assertEquals(held.pomodoro, active.session)
        assertEquals(at.minusSeconds(120), active.start)
        assertEquals(at.plusSeconds(1380), active.end)
        assertEquals(fields, active.entry.fields)
        assertFalse(active.ended)
        assertEquals("#06aaf5", active.entry.projectColor)
    }

    @Test fun bothRestKindsRetainTheServersCompletedWorkAndUpcomingTransition() {
        for (kind in listOf(PhaseKind.SHORT_REST, PhaseKind.LONG_REST)) {
            val rest = session.copy(
                phase = session.phase.copy(kind = kind, completed = 4),
                number = 4,
                upcomingPhase = PhaseKind.WORK,
                transition = if (kind ==
                    PhaseKind.SHORT_REST
                ) {
                    PhaseTransition.SHORT_REST_TO_WORK
                } else {
                    PhaseTransition.LONG_REST_TO_WORK
                },
            )
            val restHeld = server.copy(pomodoro = rest).held(at)
            val active = state(held = restHeld) as PomodoroWidgetState.Active
            assertEquals(restHeld.pomodoro, active.session)
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
        val missing = state(
            held = server.copy(runningEntry = null).held(at),
        ) as PomodoroWidgetState.Active
        val wrong = state(
            held = server.copy(runningEntry = serverEntry.copy(id = 99)).held(at),
        ) as PomodoroWidgetState.Active
        assertEquals(fields, missing.entry.fields)
        assertEquals(fields, wrong.entry.fields)
        val unknown = state(projects = emptyList(), tasks = emptyList()) as PomodoroWidgetState.Active
        assertNull(unknown.entry.project)
        assertNull(unknown.entry.projectColor)
        assertNull(unknown.entry.task)
    }

    @Test fun catalogLabelsAreScopedToTheEntryWorkspace() {
        val active = state(
            projects = listOf(project.copy(workspaceId = WorkspaceId(2))),
            tasks = listOf(task.copy(workspaceId = WorkspaceId(2))),
        ) as PomodoroWidgetState.Active
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
        assertNotEquals(
            active.key(),
            active.copy(entry = active.entry.copy(fields = fields.copy(tags = listOf("changed")))).key(),
        )
        assertNotEquals(
            active.key(),
            active.copy(entry = active.entry.copy(fields = fields.copy(billable = false))).key(),
        )
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
        assertNull(rememberedPomodoroFields(account, server.copy(pomodoro = null).held(at)))
        assertNull(rememberedPomodoroFields(account, server.copy(runningEntry = null).held(at)))
        assertNull(
            rememberedPomodoroFields(
                account,
                server.copy(runningEntry = serverEntry.copy(id = 99)).held(at),
            ),
        )
    }

    @Test fun acceptedStatesRememberFieldsBeforeIdleAndRejectStaleAndRetiredResponses() {
        var disk: String? = null
        val cache = object : CurrentEntryCache {
            override fun latestRunning(): EntryWithSendState? = null

            override fun runningCheckStart() = RunningCheckStart(emptyList())
            override fun checkedRunning(before: RunningCheckStart, server: TimeEntry?) = RunningCheckWrites()
        }
        val source = TimerStateSource(
            initial = null,
            store = { stored ->
                rememberedPomodoroFields(account, stored.held)?.let { disk = encodeRememberedPomodoroFields(it) }
            },
            cache = cache,
            clock = Clock.fixed(at, ZoneOffset.UTC),
            runningEnded = {},
        )
        val slowRead = source.begin()
        assertTrue(source.accept(source.begin(), server))
        assertEquals(remembered, disk?.let { decodeRememberedPomodoroFields(it, account) })
        assertFalse(
            source.accept(
                slowRead,
                server.copy(revision = 16, runningEntry = serverEntry.copy(description = "Stale")),
            ),
        )
        assertEquals(fields, disk?.let { decodeRememberedPomodoroFields(it, account) }?.fields())
        val lateRead = source.begin()
        assertTrue(source.accept(source.begin(), server.copy(revision = 18, pomodoro = null, runningEntry = null)))
        val idle = state(
            held = source.state.value,
            remembered = disk?.let { decodeRememberedPomodoroFields(it, account) },
        ) as PomodoroWidgetState.Idle
        assertEquals(fields, idle.entry.fields)
        source.retire()
        disk = null // Sign-out clears only after retirement has waited for accepted writes.
        assertFalse(source.accept(lateRead, server.copy(revision = 19)))
        assertNull(disk)
    }

    @Test fun keysDistinguishTagNamesContainingSeparators() {
        val active = state() as PomodoroWidgetState.Active
        fun tags(vararg names: String) = active.copy(
            entry = active.entry.copy(fields = fields.copy(tags = names.toList())),
        ).key()
        assertNotEquals(tags("a, b"), tags("a", "b"))
    }
}
