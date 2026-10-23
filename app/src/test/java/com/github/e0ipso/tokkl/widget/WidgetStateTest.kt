package com.github.e0ipso.tokkl.widget

import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerControls
import com.github.e0ipso.tokkl.cache.TimerTarget
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    private fun state(
        workspaceId: Long? = 1,
        entry: CachedTimeEntry? = running,
        favoritesAvailable: Boolean = true,
        projects: List<CachedProject> = listOf(project),
        tasks: List<CachedTask> = listOf(task),
    ) = widgetState(workspaceId, entry, favorites, favoritesAvailable, projects, tasks)

    @Test
    fun showsTheRunningEntryWithItsProjectAndTask() {
        val shown = state() as WidgetState.Ready

        assertEquals(WidgetRunning(entryId = -3, description = "Widget", project = "Tokkl for Android", task = "Design", start = start), shown.running)
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
    fun theChronometerAddsWhatThePauseChainStored() {
        val now = start.plusSeconds(90)

        assertEquals(1_000_000L - 90_000 - 600_000, chronometerBase(start, now, elapsedRealtime = 1_000_000, accumulatedSeconds = 600))
        assertEquals("a start after now counts from the stored time", 1_000_000L - 600_000, chronometerBase(now, start, elapsedRealtime = 1_000_000, accumulatedSeconds = 600))
    }

    @Test
    fun aPausedTimerShowsItsFrozenTotalAndWhatResumeStarts() {
        val target = TimerTarget(session = 11, chainId = 22, revision = 3)
        val paused = TimerControls.Paused(seconds = 600, workspaceId = 1, fields = EntryFields("Widget", projectId = 7, taskId = 9), target = target)

        val shown = controlsState(paused) as WidgetState.Ready

        assertEquals(null, shown.running)
        assertEquals(WidgetPaused(description = "Widget", project = "Tokkl for Android", task = "Design", seconds = 600, target = target), shown.paused)
        assertEquals("favorites stay", 2, shown.favorites.size)
    }

    @Test
    fun aResumedEntryShowsTheChainsTimeAndActsOnTheChain() {
        val target = TimerTarget(session = 11, chainId = 22, revision = 4, entryId = -3)
        val resumed = TimerControls.Running(running, unsent = true, accumulatedSeconds = 600, target = target)

        val shown = controlsState(resumed) as WidgetState.Ready

        assertEquals(WidgetRunning(-3, "Widget", "Tokkl for Android", "Design", start, accumulatedSeconds = 600, target = target), shown.running)
        assertEquals(null, shown.paused)
    }

    @Test
    fun theKeyChangesWithEachPauseAndResume() {
        val target = TimerTarget(session = 11, chainId = 22, revision = 3)
        val paused = controlsState(TimerControls.Paused(seconds = 600, workspaceId = 1, fields = EntryFields("Widget"), target = target))

        assertNotEquals(state().key(), paused.key())
        assertNotEquals("a later revision", paused.key(), controlsState(TimerControls.Paused(600, 1, EntryFields("Widget"), target.copy(revision = 5))).key())
        assertNotEquals("a paused edit", paused.key(), controlsState(TimerControls.Paused(600, 1, EntryFields("Edited"), target)).key())
    }

    private fun controlsState(controls: TimerControls) = widgetState(1, controls, favorites, favoritesAvailable = true, projects = listOf(project), tasks = listOf(task))

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
