package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.Favorite
import com.github.e0ipso.tokkl.model.LocalEntryId
import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.ProjectId
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.model.TaskId
import com.github.e0ipso.tokkl.model.TimeEntry
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.storage.valueStore
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.ServerTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.timerPeriodTotals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class WidgetStateTest {

    private val start: Instant = Instant.parse("2026-10-20T08:15:30.250Z")
    private val project = Project(
        id = ProjectId(7),
        workspaceId = WorkspaceId(1),
        clientId = null,
        name = "Tokkl for Android",
        color = "#06aaf5",
        active = true,
        billable = false,
    )
    private val task = Task(
        id = TaskId(9),
        workspaceId = WorkspaceId(1),
        projectId = ProjectId(7),
        name = "Design",
        active = true,
    )
    private val settings = AccountSettings("Asia/Tokyo", DayOfWeek.MONDAY, timeResolutionMinutes = null)
    private val running = TimeEntry(
        id = EntryId(-3),
        workspaceId = WorkspaceId(1),
        projectId = ProjectId(7),
        taskId = TaskId(9),
        description = "Widget",
        timing = EntryTiming.Running(start),
        billable = false,
        tags = listOf("android"),
        tagIds = emptyList(),
    )
    private val favorites = listOf(
        Favorite(
            position = 1,
            description = "Review",
            projectId = null,
            taskId = null,
            tags = emptyList(),
            billable = false,
        ),
        Favorite(
            position = 0,
            description = "Write",
            projectId = ProjectId(7),
            taskId = TaskId(9),
            tags = listOf("docs"),
            billable = true,
        ),
    )

    /** The widget for the cache's running [entry] and the server's [held] timer state. */
    private fun state(
        workspaceId: WorkspaceId? = WorkspaceId(1),
        entry: TimeEntry? = running,
        favoritesAvailable: Boolean = true,
        projects: List<Project> = listOf(project),
        tasks: List<Task> = listOf(task),
        held: HeldTimerState? = null,
        timerApi: Boolean = true,
        entries: List<TimeEntry> = listOfNotNull(entry),
        settings: AccountSettings = this.settings,
        now: Instant = start.plusSeconds(1_800),
        serverIdOf: Map<LocalEntryId, EntryId> = emptyMap(),
    ) = widgetState(
        workspaceId,
        ServerTimerStates.controls(
            entry,
            held,
            timerApi,
        ),
        favorites, favoritesAvailable, projects, tasks,
        entries = entries, settings = settings, now = now, serverIdOf = serverIdOf,
    )

    @Test
    fun projectColourChangesRedrawRunningAndPausedWidgets() {
        val recoloured = project.copy(color = "#ff5500")
        assertNotEquals(state().key(), state(projects = listOf(recoloured)).key())

        val held = ServerTimerStates.held(
            3,
            start,
            ServerTimerStates.paused(600, EntryFields("Widget", projectId = ProjectId(7))),
        )
        assertNotEquals(
            state(entry = null, held = held).key(),
            state(entry = null, held = held, projects = listOf(recoloured)).key(),
        )
    }

    @Test
    fun showsTheRunningEntryWithItsProjectAndTaskAndPause() {
        val shown = state() as WidgetState.Ready

        assertEquals(
            WidgetRunning(
                entryId = EntryId(-3),
                description = "Widget",
                project = "Tokkl for Android",
                task = "Design",
                start = start,
                accumulatedSeconds = 0,
                target = TimerTarget.server(entryId = EntryId(-3), account = ServerTimerStates.ACCOUNT),
                pausable = true,
                projectColor = project.color,
            ),
            shown.running,
        )
    }

    @Test
    fun pauseIsHiddenWithoutTheTimerApiAndDuringAPomodoroSession() {
        val toggl = state(timerApi = false) as WidgetState.Ready
        val session = state(
            held = ServerTimerStates.held(
                revision = 4,
                at = start,
                pomodoro = ServerTimerStates.session(entryId = EntryId(-3), at = start),
            ),
        ) as WidgetState.Ready

        assertFalse("no timer API", toggl.running!!.pausable)
        assertFalse("the server refuses Pause during a session", session.running!!.pausable)
        assertEquals("Stop still acts on the entry", EntryId(-3), session.running.target.entryId)
    }

    @Test
    fun noRunningEntryShowsStart() {
        assertEquals(null, (state(entry = null) as WidgetState.Ready).running)
        assertEquals(
            "a stopped entry",
            null,
            (
                state(
                    entry = running.copy(timing = EntryTiming.Stopped(start, start.plusSeconds(60))),
                ) as WidgetState.Ready
                ).running,
        )
    }

    @Test
    fun withoutAWorkspaceTheWidgetOpensTheApp() {
        assertEquals(WidgetState.NoAccount, state(workspaceId = null))
    }

    @Test
    fun favoritesComeInTheUsersOrderWithWhatATapStarts() {
        val shown = state() as WidgetState.Ready

        assertEquals(
            listOf(
                WidgetFavorite(
                    EntryFields(
                        "Write",
                        projectId = ProjectId(7),
                        taskId = TaskId(9),
                        tags = listOf("docs"),
                        billable = true,
                    ),
                    "Tokkl for Android",
                    "Design",
                ),
                WidgetFavorite(EntryFields("Review"), project = null, task = null),
            ),
            shown.favorites,
        )
    }

    @Test
    fun favoritesAreHiddenWithoutTheCapability() {
        assertEquals(emptyList<WidgetFavorite>(), (state(favoritesAvailable = false) as WidgetState.Ready).favorites)
    }

    @Test
    fun aProjectOrTaskTheCacheLacksShowsNone() {
        val shown = state(projects = emptyList(), tasks = emptyList()) as WidgetState.Ready

        assertEquals(null, shown.running!!.project)
        assertEquals(null, shown.running!!.task)
        assertEquals(null, shown.favorites.first().project)
    }

    @Test
    fun theChronometerCountsFromTheEntrysStart() {
        val now = start.plusSeconds(90)

        assertEquals(1_000_000L - 90_000, chronometerBase(start, now, elapsedRealtime = 1_000_000))
        assertEquals(
            "a start after now counts from zero",
            1_000_000L,
            chronometerBase(now, start, elapsedRealtime = 1_000_000),
        )
    }

    @Test
    fun theChronometerAddsWhatTheServersChainStored() {
        val now = start.plusSeconds(90)

        assertEquals(
            1_000_000L - 90_000 - 600_000,
            chronometerBase(start, now, elapsedRealtime = 1_000_000, accumulatedSeconds = 600),
        )
        assertEquals(
            "a start after now counts from the stored time",
            1_000_000L - 600_000,
            chronometerBase(now, start, elapsedRealtime = 1_000_000, accumulatedSeconds = 600),
        )
    }

    @Test
    fun aPausedTimerShowsTheServersFrozenTotalAndWhatResumeStarts() {
        val held = ServerTimerStates.held(
            revision = 3,
            at = start,
            pause = ServerTimerStates.paused(600, EntryFields("Widget", projectId = ProjectId(7), taskId = TaskId(9))),
        )

        val shown = state(entry = null, held = held) as WidgetState.Ready

        assertEquals(null, shown.running)
        assertEquals(
            WidgetPaused(
                description = "Widget",
                project = "Tokkl for Android",
                task = "Design",
                seconds = 600,
                target = TimerTarget.server(revision = 3, account = ServerTimerStates.ACCOUNT),
                projectColor = project.color,
            ),
            shown.paused,
        )
        assertEquals("favorites stay", 2, shown.favorites.size)
        assertEquals(
            "no chain shows without the timer API",
            null,
            (state(entry = null, held = held, timerApi = false) as WidgetState.Ready).paused,
        )
    }

    @Test
    fun aResumedCloneCountsOnFromTheChainsSecondsOnTheServersClock() {
        // The server's clock runs 20.4 s ahead of the phone's, and the server set the clone's start.
        val held = ServerTimerStates.held(
            revision = 4,
            at = start,
            pause = ServerTimerStates.resumed(600, entryId = EntryId(-3)),
            offset = Duration.ofMillis(20_400),
        )

        val shown = (state(held = held) as WidgetState.Ready).running!!

        assertEquals(600L, shown.accumulatedSeconds)
        assertEquals("the clone's start on the phone's clock, to the second", start.minusSeconds(20), shown.start)
        assertEquals(TimerTarget.server(entryId = EntryId(-3), account = ServerTimerStates.ACCOUNT), shown.target)
        assertEquals(null, (state(held = held) as WidgetState.Ready).paused)
    }

    @Test
    fun aNewStateWithTheSameClockDoesNotRedrawTheWidget() {
        // Each state the phone receives gives a slightly different offset. The clock ticks on its own.
        fun clone(offsetMs: Long) = state(
            held = ServerTimerStates.held(
                4,
                start,
                ServerTimerStates.resumed(600, entryId = EntryId(-3)),
                offset = Duration.ofMillis(offsetMs),
            ),
        )

        assertEquals(clone(20_100).key(), clone(20_400).key())
        assertNotEquals("a clock a second off redraws", clone(20_100).key(), clone(21_100).key())
    }

    @Test
    fun theKeyChangesWithEachPauseResumeAndRevision() {
        fun paused(revision: Long, fields: EntryFields = EntryFields("Widget")) =
            state(entry = null, held = ServerTimerStates.held(revision, start, ServerTimerStates.paused(600, fields)))

        assertNotEquals(state().key(), paused(3).key())
        assertNotEquals("a later revision, which Resume sends", paused(3).key(), paused(5).key())
        assertNotEquals("other fields at Pause", paused(3).key(), paused(3, EntryFields("Edited")).key())
        assertNotEquals("Pause no longer offered", state().key(), state(timerApi = false).key())
    }

    @Test
    fun theKeyChangesWithWhatTheWidgetShows() {
        val shown = state()

        assertEquals("the same state", shown.key(), state().key())
        assertNotEquals(shown.key(), state(entry = running.copy(description = "Edited")).key())
        assertNotEquals(
            shown.key(),
            state(entry = running.copy(timing = EntryTiming.Running(start.plusMillis(1)))).key(),
        )
        assertNotEquals(shown.key(), state(entry = null).key())
        assertNotEquals(shown.key(), state(favoritesAvailable = false).key())
        assertNotEquals(shown.key(), state(workspaceId = null).key())
    }

    @Test
    fun dayReportMatchesTimerTotalsWithMappedRowsBillableTimeAndNoProject() {
        val local = running.copy(
            id = EntryId(-4),
            timing = EntryTiming.Stopped(start, start.plusSeconds(600)),
            billable = true,
        )
        val server = local.copy(id = EntryId(80), timing = EntryTiming.Stopped(start, start.plusSeconds(10)))
        val otherProject = project.copy(id = ProjectId(8), name = "Other", color = "#ff5500")
        val other = local.copy(
            id = EntryId(2),
            projectId = ProjectId(8),
            timing = EntryTiming.Stopped(start, start.plusSeconds(1_200)),
            billable = false,
        )
        val unassigned = local.copy(
            id = EntryId(3),
            projectId = null,
            timing = EntryTiming.Stopped(start, start.plusSeconds(300)),
        )
        val rows = listOf(server, local, local, other, unassigned)
        val mapping = mapOf(EntryId(-4).local!! to EntryId(80))
        val now = start.plusSeconds(1_800)
        val day = (
            state(
                entry = null,
                entries = rows,
                projects = listOf(project, otherProject),
                now = now,
                serverIdOf = mapping,
            ) as WidgetState.Ready
            ).day!!

        assertEquals(LocalDate.of(2026, 10, 20), day.date)
        assertEquals(timerPeriodTotals(rows, settings, now, mapping)!!.todaySeconds, day.totalSeconds(now))
        assertEquals(2_100L, day.stoppedSeconds)
        assertEquals(900L, day.billableSeconds(now))
        assertEquals(3, day.entryCount)
        assertEquals(listOf(8L, 7L, null), day.projects.map { it.projectId?.value })
        assertEquals(listOf(1_200L, 600L, 300L), day.projects.map { it.stoppedSeconds })
        assertEquals(listOf("Other", project.name, null), day.projects.map { it.project })
        assertEquals(listOf(otherProject.color, project.color, null), day.projects.map { it.projectColor })
        assertEquals(listOf("0:20", "0:10", "0:05"), day.projects.map { it.formattedStopped })
        assertTrue(day.projects.none { it.running })
        assertNull(day.running)
    }

    @Test
    fun resumedDayCountsActualSegmentsWithoutAddingThePauseChainAgain() {
        val stopped = running.copy(
            id = EntryId(1),
            timing = EntryTiming.Stopped(start.minusSeconds(900), start.minusSeconds(300)),
            billable = true,
        )
        val current = running.copy(billable = true)
        val held = ServerTimerStates.held(
            4,
            start,
            ServerTimerStates.resumed(600, entryId = EntryId(-3)),
            offset = Duration.ofSeconds(20),
        )
        val now = start.plusSeconds(100)
        val day = (
            state(
                entry = current,
                held = held,
                entries = listOf(stopped, current),
                now = now,
            ) as WidgetState.Ready
            ).day!!
        val segment = day.running!!

        assertEquals(600L, day.stoppedSeconds)
        assertEquals(700L, day.totalSeconds(now))
        assertEquals(timerPeriodTotals(listOf(stopped, current), settings, now)!!.todaySeconds, day.totalSeconds(now))
        assertEquals(700L, day.billableSeconds(now))
        assertEquals(2, day.entryCount)
        assertEquals(current.id, segment.entryId)
        assertEquals("the report uses the entry's real start, as the Timer page does", start, segment.start)
        assertTrue(day.projects.single().running)
        assertEquals(1_000_000L - 700_000, chronometerBase(segment.start, now, 1_000_000, day.stoppedSeconds))
        assertEquals("0:11:40", day.formattedTotal(now))
    }

    @Test
    fun aMappedRunningRowKeepsTheLocalContributionAndUnassignedRunningMarker() {
        val local = running.copy(projectId = null, billable = true)
        val server = local.copy(id = EntryId(80), timing = EntryTiming.Running(start.plusSeconds(50)))
        val now = start.plusSeconds(100)
        val rows = listOf(server, local)
        val mappings = mapOf(local.id.local!! to server.id)
        val day = (state(entry = server, entries = rows, now = now, serverIdOf = mappings) as WidgetState.Ready).day!!

        assertEquals(timerPeriodTotals(rows, settings, now, mappings)!!.todaySeconds, day.totalSeconds(now))
        assertEquals(100L, day.billableSeconds(now))
        assertEquals(1, day.entryCount)
        assertEquals(local.id, day.running!!.entryId)
        assertNull(day.projects.single().projectId)
        assertTrue(day.projects.single().running)
    }

    @Test
    fun dayRolloverUsesTheAccountStartDayWithoutSplittingOvernightEntries() {
        val before = Instant.parse("2026-10-20T14:59:00Z")
        val after = before.plusSeconds(120)
        val current = running.copy(timing = EntryTiming.Running(before.minusSeconds(60)))
        val stopped = running.copy(id = EntryId(8), timing = EntryTiming.Stopped(before.minusSeconds(300), after))
        val tomorrow = running.copy(
            id = EntryId(9),
            timing = EntryTiming.Stopped(after.plusSeconds(86_400), after.plusSeconds(86_500)),
        )
        val rows = listOf(current, stopped, tomorrow)
        val old = state(entry = current, entries = rows, now = before) as WidgetState.Ready
        val next = state(entry = current, entries = rows, now = after) as WidgetState.Ready
        val oldDay = old.day!!
        val nextDay = next.day!!

        assertEquals(LocalDate.of(2026, 10, 20), oldDay.date)
        assertEquals(LocalDate.of(2026, 10, 21), nextDay.date)
        assertEquals(timerPeriodTotals(rows, settings, before)!!.todaySeconds, oldDay.totalSeconds(before))
        assertEquals(0L, nextDay.totalSeconds(after))
        assertEquals(0, nextDay.entryCount)
        assertTrue(nextDay.projects.isEmpty())
        assertNull(nextDay.running)
        assertNotEquals(old.key(), next.key())
        assertEquals("the timer still runs", current.id, next.running!!.entryId)
    }

    @Test
    fun dayDurationsUseElapsedInstantsAcrossBothDstTransitions() {
        val account = settings.copy(timeZone = "America/New_York")
        for ((first, last) in listOf(
            "2026-03-08T06:30:00Z" to "2026-03-08T07:30:00Z",
            "2026-11-01T05:30:00Z" to "2026-11-01T06:30:00Z",
        )) {
            val row = running.copy(timing = EntryTiming.Stopped(Instant.parse(first), Instant.parse(last)))
            val now = row.stop!!.plusSeconds(60)
            val day = (
                state(
                    entry = null,
                    entries = listOf(row),
                    settings = account,
                    now = now,
                ) as WidgetState.Ready
                ).day!!
            assertEquals(3_600L, day.totalSeconds(now))
            assertEquals(timerPeriodTotals(listOf(row), account, now)!!.todaySeconds, day.totalSeconds(now))
        }
    }

    @Test
    fun unknownAccountZoneHidesTheDayReportAndKeepsTimerControls() {
        val shown = state(settings = settings.copy(timeZone = "GMT+5")) as WidgetState.Ready
        assertNull(shown.day)
        assertEquals(running.id, shown.running!!.entryId)
        assertEquals(2, shown.favorites.size)
    }

    @Test
    fun totalsAndProjectRowsUseTheAccountDurationFormat() {
        val row = running.copy(timing = EntryTiming.Stopped(start, start.plusSeconds(3_661)), billable = true)
        val now = row.stop!!
        for (format in DurationFormat.entries) {
            val account = settings.copy(durationFormat = format)
            val day = (
                state(
                    entry = null,
                    entries = listOf(row),
                    settings = account,
                    now = now,
                ) as WidgetState.Ready
                ).day!!
            assertEquals(account.formatDuration(3_661), day.formattedTotal(now))
            assertEquals(account.formatDuration(3_661), day.formattedBillable(now))
            assertEquals(account.formatDurationWithoutSeconds(3_661), day.projects.single().formattedStopped)
        }
    }

    @Test
    fun aTickingReportKeyStaysStableUntilTheDayOrCacheChanges() {
        val now = start.plusSeconds(100)
        assertEquals(state(now = now).key(), state(now = now.plusSeconds(300)).key())
        val first = state(now = now) as WidgetState.Ready
        val day = first.day!!
        val row = day.projects.single()
        val segment = day.running!!
        val changes = listOf(
            day.copy(date = day.date.plusDays(1)),
            day.copy(stoppedSeconds = 1),
            day.copy(stoppedBillableSeconds = 1),
            day.copy(entryCount = day.entryCount + 1),
            day.copy(durationFormat = DurationFormat.DECIMAL),
            day.copy(running = segment.copy(entryId = EntryId(50))),
            day.copy(running = segment.copy(start = start.plusSeconds(1))),
            day.copy(running = segment.copy(billable = true)),
            day.copy(projects = listOf(row.copy(projectId = ProjectId(50)))),
            day.copy(projects = listOf(row.copy(project = "Changed"))),
            day.copy(projects = listOf(row.copy(projectColor = "#ff5500"))),
            day.copy(projects = listOf(row.copy(stoppedSeconds = 50))),
            day.copy(projects = listOf(row.copy(formattedStopped = "changed"))),
            day.copy(projects = listOf(row.copy(running = false))),
        )
        for (changed in changes) assertNotEquals(first.key(), first.copy(day = changed).key())
    }

    @Test
    fun theSyncUpdatesTheWidgetOnlyWhenWhatItShowsChanged() = runTest {
        val first = state()
        val second = state(entry = null)
        var shown: String? = first.key()
        val updates = mutableListOf<String?>()
        val sync = WidgetSync(
            keys = flowOf(first, first, second).map { it.key() },
            shown = valueStore(load = { shown }, save = { shown = it }),
            redraw = { updates += shown },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        run { sync.follow() }

        assertEquals("one update, for the stop, before the key moved on", listOf(first.key()), updates)
        assertEquals(second.key(), shown)
    }

    @Test
    fun aNewProcessUpdatesTheWidgetWhenThePhoneChangedSinceItWasDrawn() = runTest {
        var updates = 0
        var shown: String? = state(entry = null).key()
        val sync = WidgetSync(
            keys = flowOf(state()).map { it.key() },
            shown = valueStore(load = { shown }, save = { shown = it }),
            redraw = { updates++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        run { sync.follow() }

        assertEquals(1, updates)
        assertEquals(state().key(), shown)
    }

    @Test
    fun aFailedSaveKeepsFollowing() = runTest {
        var updates = 0
        val sync = WidgetSync(
            keys = flowOf(state(), state(entry = null)).map { it.key() },
            shown = valueStore(load = { null }, save = { throw IOException("disk full") }),
            redraw = { updates++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        run { sync.follow() }

        assertEquals(2, updates)
    }
}
