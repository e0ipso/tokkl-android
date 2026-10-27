package com.github.e0ipso.tokkl.cache

import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How a refresh replaces, prunes and keeps the cached rows. */
class CacheContentsTest {

    /**
     * 10:30 UTC on 18 October is 23:30 the same day in Auckland (UTC+13 in October). The window
     * starts at midnight on 5 October there, which is 11:00 UTC on 4 October.
     */
    @Test
    fun historyStartsThirteenDaysBeforeTodayAtMidnightInTheAccountZone() {
        val window = HistoryWindow.endingToday(time("Pacific/Auckland", NOW))

        assertEquals(Instant.parse("2026-10-04T11:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), window.end)
    }

    @Test
    fun theSameMomentGivesAnotherWindowInAnotherZone() {
        val window = HistoryWindow.endingToday(time("UTC", NOW))

        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-10-19T00:00:00Z"), window.end)
    }

    @Test
    fun entriesBeforeTheWindowArePrunedAndTheFirstInstantIsKept() {
        val window = HistoryWindow.endingToday(time("Pacific/Auckland", NOW))
        val before = entry(1, start = "2026-10-04T10:59:59Z")
        val first = entry(2, start = "2026-10-04T11:00:00Z")
        val today = entry(3, start = "2026-10-18T09:00:00Z")

        val refreshed = CacheContents().refreshedWith(
            ServerSnapshot(history = window, timeEntries = listOf(before, first, today)),
        )

        assertEquals(listOf(first, today), refreshed.timeEntries)
    }

    @Test
    fun cachedEntriesArePrunedEvenWhenTheEntriesRequestFailed() {
        val window = HistoryWindow.endingToday(time("Pacific/Auckland", NOW))
        val old = entry(1, start = "2026-09-30T08:00:00Z")
        val recent = entry(2, start = "2026-10-10T08:00:00Z")

        val refreshed = CacheContents(timeEntries = listOf(old, recent))
            .refreshedWith(ServerSnapshot(history = window, timeEntries = null))

        assertEquals(listOf(recent), refreshed.timeEntries)
    }

    @Test
    fun withoutAKnownZoneNothingIsPruned() {
        val old = entry(1, start = "2026-01-01T08:00:00Z")

        val refreshed = CacheContents(timeEntries = listOf(old)).refreshedWith(ServerSnapshot(history = null))

        assertEquals(listOf(old), refreshed.timeEntries)
    }

    @Test
    fun aRefreshReplacesStaleRowsWithTheServersValues() {
        val cached = CacheContents(
            timeEntries = listOf(entry(1, description = "old text"), entry(2, description = "deleted on the server")),
            projects = listOf(project(10, name = "Old name")),
            clients = listOf(CachedClient(20, WORKSPACE, "Old client", archived = false), CachedClient(21, WORKSPACE, "Gone", false)),
            tags = listOf(CachedTag(30, WORKSPACE, "old-tag")),
            tasks = listOf(CachedTask(40, WORKSPACE, 10, "Old task", active = true)),
            favorites = listOf(favorite(0, "Old favorite"), favorite(1, "Second")),
        )
        val server = ServerSnapshot(
            history = WINDOW,
            timeEntries = listOf(entry(1, description = "new text")),
            projects = listOf(project(10, name = "New name")),
            clients = listOf(CachedClient(20, WORKSPACE, "New client", archived = true)),
            tags = listOf(CachedTag(30, WORKSPACE, "new-tag")),
            tasks = listOf(CachedTask(40, WORKSPACE, 10, "New task", active = true)),
            favorites = listOf(favorite(0, "New favorite")),
        )

        val refreshed = cached.refreshedWith(server)

        assertEquals(
            CacheContents(
                timeEntries = server.timeEntries!!,
                projects = server.projects!!,
                clients = server.clients!!,
                tags = server.tags!!,
                tasks = server.tasks!!,
                favorites = server.favorites!!,
            ),
            refreshed,
        )
    }

    @Test
    fun listsTheRefreshDidNotReadStayAsTheyWere() {
        val cached = CacheContents(
            projects = listOf(project(10)),
            clients = listOf(CachedClient(20, WORKSPACE, "Client", archived = false)),
            tags = listOf(CachedTag(30, WORKSPACE, "tag")),
            tasks = listOf(CachedTask(40, WORKSPACE, 10, "Task", active = true)),
            favorites = listOf(favorite(0, "Favorite")),
        )

        assertEquals(cached, cached.refreshedWith(ServerSnapshot(history = WINDOW)))
    }

    @Test
    fun archivedProjectsStayWhileAnEntryOrFavoriteUsesThem() {
        val usedByEntry = project(10, active = false, name = "Archived, on an entry")
        val usedByFavorite = project(11, active = false, name = "Archived, in a favorite")
        val unused = project(12, active = false, name = "Archived, unused")
        val active = project(13)

        val refreshed = CacheContents().refreshedWith(
            ServerSnapshot(
                history = WINDOW,
                timeEntries = listOf(entry(1, projectId = 10)),
                projects = listOf(usedByEntry, usedByFavorite, unused, active),
                favorites = listOf(favorite(0, "Favorite", projectId = 11)),
            ),
        )

        assertEquals(listOf(usedByEntry, usedByFavorite, active), refreshed.projects)
    }

    @Test
    fun anArchivedProjectGoesOnceItsLastEntryIsPruned() {
        val archived = project(10, active = false)
        val cached = CacheContents(timeEntries = listOf(entry(1, start = "2026-09-01T08:00:00Z", projectId = 10)), projects = listOf(archived))

        val refreshed = cached.refreshedWith(ServerSnapshot(history = WINDOW, timeEntries = emptyList(), projects = listOf(archived)))

        assertEquals(emptyList<CachedProject>(), refreshed.projects)
    }

    @Test
    fun doneTasksStayWhileAnEntryUsesThem() {
        val used = CachedTask(40, WORKSPACE, 10, "Done, on an entry", active = false)
        val unused = CachedTask(41, WORKSPACE, 10, "Done, unused", active = false)

        val refreshed = CacheContents().refreshedWith(
            ServerSnapshot(history = WINDOW, timeEntries = listOf(entry(1, taskId = 40)), tasks = listOf(used, unused)),
        )

        assertEquals(listOf(used), refreshed.tasks)
    }

    @Test
    fun rowsTheServerNoLongerSendsStayWhileACachedEntryUsesThem() {
        val usedTag = CachedTag(30, WORKSPACE, "used")
        val unusedTag = CachedTag(31, WORKSPACE, "unused")
        val usedProject = project(10, name = "Used")
        val unusedProject = project(11, name = "Unused")
        val cached = CacheContents(projects = listOf(usedProject, unusedProject), tags = listOf(usedTag, unusedTag))
        val fresh = CachedTag(32, WORKSPACE, "fresh")

        val refreshed = cached.refreshedWith(
            ServerSnapshot(
                history = WINDOW,
                timeEntries = listOf(entry(1, projectId = 10, tagIds = listOf(30L))),
                projects = emptyList(),
                tags = listOf(fresh),
            ),
        )

        assertEquals(listOf(usedProject), refreshed.projects)
        assertEquals(listOf(fresh, usedTag), refreshed.tags)
    }

    @Test
    fun queuedChangesStayAppliedOverTheRefreshedEntriesUntilSent() {
        val server = listOf(entry(1, description = "Server"), entry(2, description = "Deleted on the phone"))
        val pending = listOf(
            PendingChange.of(ChangeKind.UPDATE, entry(1, description = "Phone, first")).copy(sequence = 1),
            PendingChange.of(ChangeKind.CREATE, entry(-3, description = "Started offline").copy(stop = null)).copy(sequence = 3),
            PendingChange.of(ChangeKind.UPDATE, entry(1, description = "Phone, last")).copy(sequence = 4),
            PendingChange.of(ChangeKind.DELETE, entry(2)).copy(sequence = 5),
        )

        val refreshed = CacheContents().refreshedWith(ServerSnapshot(history = WINDOW, timeEntries = server), pending)

        assertEquals(
            listOf(1L to "Phone, last", -3L to "Started offline"),
            refreshed.timeEntries.map { it.id to it.description },
        )
        assertNull("the offline timer runs", refreshed.timeEntries.single { it.id == -3L }.stop)
    }

    @Test
    fun aQueuedChangeToAnEntryBeforeTheWindowStaysAndKeepsItsArchivedProject() {
        val old = entry(1, start = "2026-09-20T08:00:00Z", description = "Edited offline", projectId = 9)
        val pending = listOf(PendingChange.of(ChangeKind.UPDATE, old).copy(sequence = 1))

        val refreshed = CacheContents().refreshedWith(
            ServerSnapshot(history = WINDOW, timeEntries = emptyList(), projects = listOf(project(9, active = false))),
            pending,
        )

        assertEquals(listOf(old), refreshed.timeEntries)
        assertEquals(listOf(9L), refreshed.projects.map { it.id })
    }

    @Test
    fun aRefreshReadBeforeStartWasSentKeepsTheRemappedEntryUntilTheNextRefresh() {
        // Start is already visible offline when the refresh reads an empty server history.
        val local = entry(-1, description = "Started offline").copy(stop = null)
        val before = EntryRefreshStart(listOf(local), setOf(local.id))
        val stale = ServerSnapshot(history = WINDOW, timeEntries = emptyList())
        // The create answer removes the pending change and replaces the negative ID.
        val sent = local.copy(id = 101)
        val afterSend = CacheContents(timeEntries = listOf(sent))

        val refreshed = afterSend.refreshedWith(stale, before = before)

        assertEquals(listOf(sent), refreshed.timeEntries)
        assertEquals(emptyList<CachedTimeEntry>(), refreshed.refreshedWith(
            stale, before = EntryRefreshStart(refreshed.timeEntries, emptySet()),
        ).timeEntries)
    }

    @Test
    fun aRefreshReadBeforeStopWasSentCannotRestartTheTimer() {
        val running = entry(1).copy(stop = null)
        val stopped = running.copy(stop = running.start.plusSeconds(90))
        val stale = ServerSnapshot(history = WINDOW, timeEntries = listOf(running))
        // Cover Stop both during the read and queued before it. Sending can leave the same row.
        for (before in listOf(
            EntryRefreshStart(listOf(running), emptySet()),
            EntryRefreshStart(listOf(stopped), setOf(stopped.id)),
        )) {
            val afterSend = CacheContents(timeEntries = listOf(stopped))
            val refreshed = afterSend.refreshedWith(stale, before = before)

            assertEquals(listOf(stopped), refreshed.timeEntries)
            val remoteEdit = stopped.copy(description = "Edited elsewhere")
            assertEquals(listOf(remoteEdit), refreshed.refreshedWith(
                ServerSnapshot(history = WINDOW, timeEntries = listOf(remoteEdit)),
                before = EntryRefreshStart(refreshed.timeEntries, emptySet()),
            ).timeEntries)
        }
    }

    @Test
    fun aRefreshReadBeforeRemovalWasSentCannotRestoreTheEntry() {
        val removed = entry(1)
        val stale = ServerSnapshot(history = WINDOW, timeEntries = listOf(removed))
        // Deletes and short Stops remove the cached row immediately, before their send.
        for (before in listOf(
            EntryRefreshStart(listOf(removed), emptySet()),
            EntryRefreshStart(emptyList(), setOf(removed.id)),
        )) {
            val afterSend = CacheContents()
            val refreshed = afterSend.refreshedWith(stale, before = before)

            assertEquals(emptyList<CachedTimeEntry>(), refreshed.timeEntries)
            assertEquals(listOf(removed), refreshed.refreshedWith(
                stale, before = EntryRefreshStart(refreshed.timeEntries, emptySet()),
            ).timeEntries)
        }
    }

    private fun time(zone: String, now: Instant) =
        AccountSettings(zone, DayOfWeek.MONDAY, timeResolutionMinutes = null).time(Clock.fixed(now, ZoneOffset.UTC))!!

    private fun entry(
        id: Long,
        start: String = "2026-10-15T08:00:00Z",
        description: String = "",
        projectId: Long? = null,
        taskId: Long? = null,
        tagIds: List<Long> = emptyList(),
    ) = CachedTimeEntry(
        id = id,
        workspaceId = WORKSPACE,
        projectId = projectId,
        taskId = taskId,
        description = description,
        start = Instant.parse(start),
        stop = Instant.parse(start).plusSeconds(600),
        billable = false,
        tags = emptyList(),
        tagIds = tagIds,
    )

    private fun project(id: Long, name: String = "Project $id", active: Boolean = true) =
        CachedProject(id, WORKSPACE, clientId = null, name = name, color = "#0b83d9", active = active, billable = false)

    private fun favorite(position: Int, description: String, projectId: Long? = null) =
        CachedFavorite(position, description, projectId, taskId = null, tags = emptyList(), billable = false)

    private companion object {
        const val WORKSPACE = 1L
        val NOW: Instant = Instant.parse("2026-10-18T10:30:00Z")
        val WINDOW = HistoryWindow(Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-19T00:00:00Z"))
    }
}
