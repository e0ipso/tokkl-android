package com.github.e0ipso.tokkl.shortcuts

import com.github.e0ipso.tokkl.cache.CachedFavorite
import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.EntryFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteShortcutsTest {

    private val project = CachedProject(id = 7, workspaceId = 1, clientId = null, name = "Website", color = "#06aaf5", active = true, billable = false)
    private val names = FavoriteNames(untitled = "Untitled", withProject = { name, project -> "$name, $project" })

    private val report = favorite(0, description = "Write the report", projectId = 7, tags = listOf("call"), billable = true)
    private val website = favorite(1, description = "", projectId = 7)
    private val untitled = favorite(2, description = "")

    private fun favorite(position: Int, description: String, projectId: Long? = null, tags: List<String> = emptyList(), billable: Boolean = false) =
        CachedFavorite(position = position, description = description, projectId = projectId, taskId = null, tags = tags, billable = billable)

    private fun shortcuts(available: Boolean = true, favorites: List<CachedFavorite> = listOf(report, website, untitled), limit: Int = 15) =
        favoriteShortcuts(available, favorites, listOf(project), limit, names)

    @Test
    fun theFavoritesGiveShortcutsInTheirOrderNamedAsTheTimerPageNamesThem() {
        val shown = shortcuts()

        assertEquals(listOf("Write the report", "Website", "Untitled"), shown.map { it.shortLabel })
        assertEquals(listOf("Write the report, Website", "Website", "Untitled"), shown.map { it.longLabel })
    }

    @Test
    fun theLauncherLimitCutsTheList() {
        assertEquals(listOf("Write the report", "Website"), shortcuts(limit = 2).map { it.shortLabel })
        assertEquals(emptyList<FavoriteShortcut>(), shortcuts(limit = 0))
    }

    @Test
    fun aServerWithoutFavoritesGivesNoShortcuts() {
        assertEquals(emptyList<FavoriteShortcut>(), shortcuts(available = false))
        assertEquals(emptyList<FavoriteShortcut>(), shortcuts(favorites = emptyList()))
    }

    @Test
    fun theIdFollowsTheFavoriteNotItsPlace() {
        val moved = shortcuts(favorites = listOf(website.copy(position = 0), report.copy(position = 1)))

        assertEquals(shortcuts().take(2).reversed(), moved)
    }

    @Test
    fun theIdChangesWithEveryFieldAndHoldsNoName() {
        val fields = EntryFields(description = "Write the report", projectId = 7, taskId = 3, tags = listOf("call"), billable = true)
        val id = favoriteShortcutId(fields)
        val others = listOf(
            fields.copy(description = "Write the reports"),
            fields.copy(projectId = null),
            fields.copy(taskId = null),
            fields.copy(tags = listOf("call", "work")),
            fields.copy(tags = listOf("cal", "l")),
            fields.copy(billable = false),
        )

        others.forEach { assertNotEquals(it.toString(), id, favoriteShortcutId(it)) }
        assertFalse(id.contains("report"))
    }

    @Test
    fun twoFavoritesWithTheSameFieldsGiveOneShortcut() {
        val shown = shortcuts(favorites = listOf(report, website, report.copy(position = 2)))

        assertEquals(listOf("Write the report", "Website"), shown.map { it.shortLabel })
    }

    @Test
    fun aTapStartsTheFavoriteTheIdNamesInTheDefaultWorkspace() = runBlocking {
        val started = mutableListOf<Pair<Long, EntryFields>>()
        val result = tap(favoriteShortcutId(EntryFields(description = "", projectId = 7))) { workspace, fields ->
            started += workspace to fields
            -1L
        }

        assertEquals(ShortcutStart.Started("Website"), result)
        assertEquals(listOf(3L to EntryFields(description = "", projectId = 7)), started)
    }

    @Test
    fun aTapOnAShortcutWhoseFavoriteIsGoneStartsNothing() = runBlocking {
        val id = shortcuts().first().id
        var starts = 0
        val start: suspend (Long, EntryFields) -> Long = { _, _ -> starts++; -1L }

        assertEquals("removed", ShortcutStart.Gone, tap(id, favorites = listOf(website), start = start))
        assertEquals("no favorites on the server", ShortcutStart.Gone, tap(id, available = false, start = start))
        assertEquals("signed out", ShortcutStart.Gone, tap(id, workspaceId = null, start = start))
        assertEquals(0, starts)
    }

    private suspend fun tap(
        id: String,
        available: Boolean = true,
        favorites: List<CachedFavorite> = listOf(report, website, untitled),
        workspaceId: Long? = 3,
        start: suspend (Long, EntryFields) -> Long,
    ) = startFavoriteShortcut(id, available, favorites, listOf(project), workspaceId, names, start)

    @Test
    fun theSyncSetsOnlyAListTheLauncherLacks() = runBlocking {
        var launcher = shortcuts()
        val set = mutableListOf<List<FavoriteShortcut>>()
        val sync = FavoriteShortcutSync(
            shortcuts = flowOf(shortcuts(), shortcuts(limit = 1), shortcuts(available = false)),
            published = { launcher },
            publish = { set += it; launcher = it },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

        sync.follow()

        assertEquals("a new process finds the launcher up to date", listOf(shortcuts(limit = 1), emptyList()), set)
        sync.forget()
        assertTrue(set.last().isEmpty())
    }
}
