package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.ServerTimerStates
import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetStateTest {

    private val start: Instant = Instant.parse("2026-10-20T08:15:30.250Z")
    private val project = CachedProject(id = 7, workspaceId = 1, clientId = null, name = "Tokkl for Android", color = "#06aaf5", active = true, billable = false)
    private val task = CachedTask(id = 9, workspaceId = 1, projectId = 7, name = "Design", active = true)
    private val running = CachedTimeEntry(
        id = -3,
        workspaceId = 1,
        projectId = 7,
        taskId = 9,
        description = "Widget",
        start = start,
        stop = null,
        billable = false,
        tags = listOf("android"),
        tagIds = emptyList(),
    )
    private val favorites = listOf(
        CachedFavorite(position = 1, description = "Review", projectId = null, taskId = null, tags = emptyList(), billable = false),
        CachedFavorite(position = 0, description = "Write", projectId = 7, taskId = 9, tags = listOf("docs"), billable = true),
    )

    /** The widget for the cache's running [entry] and the server's [held] timer state. */
    private fun state(
        workspaceId: Long? = 1,
        entry: CachedTimeEntry? = running,
        favoritesAvailable: Boolean = true,
        projects: List<CachedProject> = listOf(project),
        tasks: List<CachedTask> = listOf(task),
        held: HeldTimerState? = null,
        timerApi: Boolean = true,
    ) = widgetState(workspaceId, ServerTimerStates.controls(entry, held, timerApi), favorites, favoritesAvailable, projects, tasks)

    @Test
    fun showsTheRunningEntryWithItsProjectAndTaskAndPause() {
        val shown = state() as WidgetState.Ready

        assertEquals(
            WidgetRunning(
                entryId = -3,
                description = "Widget",
                project = "Tokkl for Android",
                task = "Design",
                start = start,
                accumulatedSeconds = 0,
                target = TimerTarget.server(entryId = -3, account = ServerTimerStates.ACCOUNT),
                pausable = true,
            ),
            shown.running,
        )
    }

    @Test
    fun pauseIsHiddenWithoutTheTimerApiAndDuringAPomodoroSession() {
        val toggl = state(timerApi = false) as WidgetState.Ready
        val session = state(held = ServerTimerStates.held(revision = 4, at = start, pomodoro = ServerTimerStates.session(entryId = -3, at = start))) as WidgetState.Ready

        assertFalse("no timer API", toggl.running!!.pausable)
        assertFalse("the server refuses Pause during a session", session.running!!.pausable)
        assertEquals("Stop still acts on the entry", -3L, session.running.target.entryId)
    }

    @Test
    fun noRunningEntryShowsStart() {
        assertEquals(null, (state(entry = null) as WidgetState.Ready).running)
        assertEquals("a stopped entry", null, (state(entry = running.copy(stop = start.plusSeconds(60))) as WidgetState.Ready).running)
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
                WidgetFavorite(EntryFields("Write", projectId = 7, taskId = 9, tags = listOf("docs"), billable = true), "Tokkl for Android", "Design"),
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
    fun theWidgetShowsAsManyFavoritesAsItsHeightFits() {
        val timerOnly = 2 * WidgetLayout.PADDING_DP + WidgetLayout.TIMER_ROW_DP
        assertEquals(0, favoritesThatFit(40f, count = 5))
        assertEquals(0, favoritesThatFit(timerOnly, count = 5))
        assertEquals(0, favoritesThatFit(timerOnly + WidgetLayout.FAVORITE_ROW_DP - 1, count = 5))
        assertEquals(1, favoritesThatFit(timerOnly + WidgetLayout.FAVORITE_ROW_DP, count = 5))
        assertEquals(3, favoritesThatFit(timerOnly + 3.5f * WidgetLayout.FAVORITE_ROW_DP, count = 5))
        assertEquals("never more than there are", 2, favoritesThatFit(1_000f, count = 2))
    }

    @Test
    fun largerFontsReserveRoomForTheClockAndEachFavorite() {
        assertEquals(128f, WidgetLayout.timerRowHeight(2f))
        assertEquals(96f, WidgetLayout.favoriteRowHeight(2f))
        assertEquals(0, favoritesThatFit(152f, count = 5, fontScale = 2f))
        assertEquals(1, favoritesThatFit(248f, count = 5, fontScale = 2f))
        assertEquals(1, favoritesThatFit(320f, count = 5, fontScale = 2f))
        assertEquals(2, favoritesThatFit(344f, count = 5, fontScale = 2f))
    }

    @Test
    fun theChronometerCountsFromTheEntrysStart() {
        val now = start.plusSeconds(90)

        assertEquals(1_000_000L - 90_000, chronometerBase(start, now, elapsedRealtime = 1_000_000))
        assertEquals("a start after now counts from zero", 1_000_000L, chronometerBase(now, start, elapsedRealtime = 1_000_000))
    }

    @Test
    fun theChronometerAddsWhatTheServersChainStored() {
        val now = start.plusSeconds(90)

        assertEquals(1_000_000L - 90_000 - 600_000, chronometerBase(start, now, elapsedRealtime = 1_000_000, accumulatedSeconds = 600))
        assertEquals("a start after now counts from the stored time", 1_000_000L - 600_000, chronometerBase(now, start, elapsedRealtime = 1_000_000, accumulatedSeconds = 600))
    }

    @Test
    fun aPausedTimerShowsTheServersFrozenTotalAndWhatResumeStarts() {
        val held = ServerTimerStates.held(revision = 3, at = start, pause = ServerTimerStates.paused(600, EntryFields("Widget", projectId = 7, taskId = 9)))

        val shown = state(entry = null, held = held) as WidgetState.Ready

        assertEquals(null, shown.running)
        assertEquals(
            WidgetPaused(
                description = "Widget",
                project = "Tokkl for Android",
                task = "Design",
                seconds = 600,
                target = TimerTarget.server(revision = 3, account = ServerTimerStates.ACCOUNT),
            ),
            shown.paused,
        )
        assertEquals("favorites stay", 2, shown.favorites.size)
        assertEquals("no chain shows without the timer API", null, (state(entry = null, held = held, timerApi = false) as WidgetState.Ready).paused)
    }

    @Test
    fun aResumedCloneCountsOnFromTheChainsSecondsOnTheServersClock() {
        // The server's clock runs 20.4 s ahead of the phone's, and the server set the clone's start.
        val held = ServerTimerStates.held(revision = 4, at = start, pause = ServerTimerStates.resumed(600, entryId = -3), offset = Duration.ofMillis(20_400))

        val shown = (state(held = held) as WidgetState.Ready).running!!

        assertEquals(600L, shown.accumulatedSeconds)
        assertEquals("the clone's start on the phone's clock, to the second", start.minusSeconds(20), shown.start)
        assertEquals(TimerTarget.server(entryId = -3, account = ServerTimerStates.ACCOUNT), shown.target)
        assertEquals(null, (state(held = held) as WidgetState.Ready).paused)
    }

    @Test
    fun aNewStateWithTheSameClockDoesNotRedrawTheWidget() {
        // Each state the phone receives gives a slightly different offset. The clock ticks on its own.
        fun clone(offsetMs: Long) = state(held = ServerTimerStates.held(4, start, ServerTimerStates.resumed(600, entryId = -3), offset = Duration.ofMillis(offsetMs)))

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
        assertNotEquals(shown.key(), state(entry = running.copy(start = start.plusMillis(1))).key())
        assertNotEquals(shown.key(), state(entry = null).key())
        assertNotEquals(shown.key(), state(favoritesAvailable = false).key())
        assertNotEquals(shown.key(), state(workspaceId = null).key())
    }

    @Test
    fun theSyncUpdatesTheWidgetOnlyWhenWhatItShowsChanged() {
        val first = state()
        val second = state(entry = null)
        var shown: String? = first.key()
        val updates = mutableListOf<String?>()
        val sync = WidgetSync(
            state = { flowOf(first, first, second) },
            loadShown = { shown },
            saveShown = { shown = it },
            update = { updates += shown },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        runBlocking { sync.follow() }

        assertEquals("one update, for the stop, before the key moved on", listOf(first.key()), updates)
        assertEquals(second.key(), shown)
    }

    @Test
    fun aNewProcessUpdatesTheWidgetWhenThePhoneChangedSinceItWasDrawn() {
        var updates = 0
        var shown: String? = state(entry = null).key()
        val sync = WidgetSync(
            state = { flowOf(state()) },
            loadShown = { shown },
            saveShown = { shown = it },
            update = { updates++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        runBlocking { sync.follow() }

        assertEquals(1, updates)
        assertEquals(state().key(), shown)
    }

    @Test
    fun aFailedSaveKeepsFollowing() {
        var updates = 0
        val sync = WidgetSync(
            state = { flowOf(state(), state(entry = null)) },
            loadShown = { null },
            saveShown = { throw IOException("disk full") },
            update = { updates++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        runBlocking { sync.follow() }

        assertEquals(2, updates)
    }
}
