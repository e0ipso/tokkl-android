package com.github.e0ipso.tokkl.notification

import com.github.e0ipso.tokkl.cache.CachedProject
import com.github.e0ipso.tokkl.cache.CachedTask
import com.github.e0ipso.tokkl.cache.CachedTimeEntry
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunningNotificationTest {

    private val start: Instant = Instant.parse("2026-10-20T08:15:30.250Z")
    private val project = CachedProject(id = 7, workspaceId = 1, clientId = null, name = "Tokkl for Android", color = "#06aaf5", active = true, billable = false)
    private val task = CachedTask(id = 9, workspaceId = 1, projectId = 7, name = "Design", active = true)
    private val running = CachedTimeEntry(
        id = -3,
        workspaceId = 1,
        projectId = 7,
        taskId = 9,
        description = "Notification",
        start = start,
        stop = null,
        billable = false,
        tags = listOf("android"),
        tagIds = emptyList(),
    )

    private fun content(entry: CachedTimeEntry? = running, projects: List<CachedProject> = listOf(project), tasks: List<CachedTask> = listOf(task)) =
        runningNotificationContent(entry, projects, tasks)

    @Test
    fun showsTheDescriptionProjectTaskAndStartOfTheRunningEntry() {
        val shown = content()!!

        assertEquals(-3L, shown.entryId)
        assertEquals("Notification", shown.description)
        assertEquals("Tokkl for Android", shown.project)
        assertEquals("Design", shown.task)
        assertEquals("the chronometer counts from the entry's start", start, shown.start)
    }

    @Test
    fun noRunningEntryShowsNothing() {
        assertNull(content(entry = null))
        assertNull("a stopped entry", content(entry = running.copy(stop = start.plusSeconds(60))))
    }

    @Test
    fun aProjectOrTaskTheCacheLacksShowsNone() {
        val shown = content(projects = emptyList(), tasks = emptyList())!!

        assertNull(shown.project)
        assertNull(shown.task)
        assertEquals("Notification", shown.description)
    }

    @Test
    fun theKeyChangesWithEveryFieldAStartOrEditSets() {
        val key = content()!!.key
        val edits = listOf(
            running.copy(start = start.plusSeconds(1)),
            running.copy(description = "Edited"),
            running.copy(projectId = null, taskId = null),
            running.copy(taskId = null),
            running.copy(tags = listOf("android", "work")),
            running.copy(tags = listOf("androids")),
            running.copy(tags = emptyList()),
            running.copy(billable = true),
            running.copy(workspaceId = 2),
        )

        edits.forEach { edited -> assertNotEquals("$edited", key, content(entry = edited)!!.key) }
        assertNotEquals("a renamed project", key, content(projects = listOf(project.copy(name = "Renamed")))!!.key)
    }

    @Test
    fun theKeyStaysWhenTheServerGivesTheEntryItsId() {
        assertEquals(content()!!.key, content(entry = running.copy(id = 4_200))!!.key)
    }

    @Test
    fun theKeyStaysWhenTheServerStoresTheEntryAsItDoes() {
        val key = content(entry = running.copy(description = " Notification ", tags = listOf("Android", "work")))!!.key
        val stored = running.copy(
            start = start.truncatedTo(ChronoUnit.SECONDS),
            description = "Notification",
            tags = listOf("Work", "android"),
        )

        assertEquals(key, content(entry = stored)!!.key)
    }

    @Test
    fun valuesThatRunTogetherGiveDifferentKeys() {
        val a = content(entry = running.copy(description = "ab", tags = listOf("c")))!!.key
        val b = content(entry = running.copy(description = "a", tags = listOf("bc")))!!.key

        assertNotEquals(a, b)
    }

    @Test
    fun theKeyHoldsNoTextOfTheEntry() {
        val key = content()!!.key

        assertEquals(64, key.length)
        assertEquals("a hex SHA-256", true, key.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun aStartPostsAndAStopRemoves() {
        val shown = content()!!

        assertEquals(RunningNotificationChange.Post(shown), runningNotificationChange(followed = null, content = shown))
        assertEquals(RunningNotificationChange.Remove, runningNotificationChange(followed = shown.followed(), content = null))
    }

    @Test
    fun theEntryTheNotificationFollowedChangesNothing() {
        val shown = content()!!

        assertNull("the app opening, a swiped-away notification stays away", runningNotificationChange(shown.followed(), shown))
        assertNull("nothing runs, and nothing ran", runningNotificationChange(null, null))
    }

    @Test
    fun anotherEntryWithTheSameKeyPostsAgain() {
        val shown = content()!!
        // A second start with the same fields within the same second.
        val next = content(entry = running.copy(id = -4, start = start.plusMillis(400)))!!

        assertEquals(shown.key, next.key)
        assertEquals(RunningNotificationChange.Post(next), runningNotificationChange(shown.followed(), next))
    }

    @Test
    fun aKeyKeptWithoutAnIdMatchesAnyEntryWithIt() {
        val shown = content()!!

        assertNull(runningNotificationChange(FollowedEntry(shown.key, entryId = null), content(entry = running.copy(id = -4))))
    }

    @Test
    fun anEditPostsAgain() {
        val shown = content()!!
        val edited = content(entry = running.copy(description = "Edited"))!!

        assertEquals(RunningNotificationChange.Post(edited), runningNotificationChange(shown.followed(), edited))
    }

    @Test
    fun theSyncPostsOnStartAndEditAndRemovesOnStop() {
        val fake = FakeNotification(serverIds = mapOf(-3L to 4_200L))
        val first = content()!!
        val edited = content(entry = running.copy(description = "Edited"))!!
        // The server answered the create: the same entry under its own id.
        val idChanged = content(entry = running.copy(id = 4_200, description = "Edited"))!!
        val sync = fake.sync(flowOf(null, first, first, edited, idChanged, null, null))

        runBlocking { sync.follow() }

        assertEquals(listOf("post Notification -3", "post Edited -3", "remove"), fake.calls)
        assertNull(fake.shown)
    }

    @Test
    fun twoStartsWithinOneSecondPostTheSecondForItsStop() {
        val fake = FakeNotification()
        val first = content()!!
        val second = content(entry = running.copy(id = -4, start = start.plusMillis(400)))!!

        runBlocking { fake.sync(flowOf(first, second)).follow() }

        assertEquals(listOf("post Notification -3", "post Notification -4"), fake.calls)
        assertEquals(second.followed(), fake.shown)
    }

    @Test
    fun theServersIdForTheFollowedEntryPostsNothingAndIsKept() {
        val first = content()!!
        val answered = content(entry = running.copy(id = 4_200, start = start.truncatedTo(ChronoUnit.SECONDS)))!!
        val fake = FakeNotification(shown = first.followed(), serverIds = mapOf(-3L to 4_200L))

        runBlocking { fake.sync(flowOf(answered)).follow() }

        assertEquals("a dismissed notification stays dismissed", emptyList<String>(), fake.calls)
        assertEquals(FollowedEntry(first.key, 4_200), fake.shown)
    }

    @Test
    fun anotherServerEntryWithTheSameKeyPosts() {
        val first = content()!!
        // A refresh finds a timer started elsewhere with the same fields in the same second.
        val elsewhere = content(entry = running.copy(id = 9_000))!!
        val fake = FakeNotification(shown = first.followed(), serverIds = mapOf(-3L to 4_200L))

        runBlocking { fake.sync(flowOf(elsewhere)).follow() }

        assertEquals(listOf("post Notification 9000"), fake.calls)
        assertEquals(elsewhere.followed(), fake.shown)
    }

    @Test
    fun aKeyKeptWithoutAnIdPostsNothingAndKeepsTheId() {
        val first = content()!!
        val fake = FakeNotification(shown = FollowedEntry(first.key, entryId = null))

        runBlocking { fake.sync(flowOf(first)).follow() }

        assertEquals(emptyList<String>(), fake.calls)
        assertEquals(first.followed(), fake.shown)
    }

    @Test
    fun aNewProcessPostsNothingForTheEntryTheNotificationFollowed() {
        val first = content()!!
        val fake = FakeNotification(shown = first.followed())

        runBlocking { fake.sync(flowOf(first)).follow() }

        assertEquals("a dismissed notification stays dismissed", emptyList<String>(), fake.calls)
        assertEquals(first.followed(), fake.shown)
    }

    @Test
    fun aNewProcessRemovesTheNotificationOfAnEntryStoppedMeanwhile() {
        val fake = FakeNotification(shown = content()!!.followed())

        runBlocking { fake.sync(flowOf(null)).follow() }

        assertEquals(listOf("remove"), fake.calls)
        assertNull(fake.shown)
    }

    @Test
    fun forgettingRemovesTheNotificationAndTheKey() {
        val fake = FakeNotification(shown = content()!!.followed())
        val sync = fake.sync(flowOf())

        runBlocking { sync.forget() }

        assertEquals(listOf("remove"), fake.calls)
        assertNull(fake.shown)
    }

    @Test
    fun aFailedWriteKeepsFollowingAndMakesTheChangeAgainLater() {
        val fake = FakeNotification(failWrites = true)
        val first = content()!!

        runBlocking { fake.sync(flowOf(first, null, first)).follow() }

        assertEquals(listOf("post Notification -3", "post Notification -3"), fake.calls)
    }

    @Test
    fun grantingPermissionRepostsTheLatestEntryEvenIfItsStartWasAlreadyFollowed() {
        val first = content()!!
        val fake = FakeNotification(shown = first.followed())
        val sync = fake.sync(flowOf())
        runBlocking {
            sync.apply(first)
            assertEquals(emptyList<String>(), fake.calls)
            sync.repost()
            sync.apply(null)
            sync.repost()
        }
        assertEquals(listOf("post Notification -3", "remove"), fake.calls)
    }

    @Test
    fun aGrantAfterSignOutCannotRepostThePreviousAccountsEntry() {
        val first = content()!!
        val fake = FakeNotification(shown = first.followed())
        val sync = fake.sync(flowOf())
        runBlocking {
            sync.apply(first)
            sync.forget()
            sync.repost()
        }
        assertEquals(listOf("remove"), fake.calls)
    }

    private fun RunningNotificationContent.followed() = FollowedEntry(key, entryId)

    /** [serverIds] maps the local ids the server has answered to its own, as the queue's table does. */
    private class FakeNotification(
        var shown: FollowedEntry? = null,
        private val failWrites: Boolean = false,
        private val serverIds: Map<Long, Long> = emptyMap(),
    ) {
        val calls = mutableListOf<String>()

        fun sync(running: Flow<RunningNotificationContent?>) =
            RunningNotificationSync(
                running = running,
                loadShown = { shown },
                saveShown = { followed -> if (failWrites) throw IOException("disk full") else shown = followed },
                currentId = { id -> serverIds[id] ?: id },
                post = { calls += "post ${it.description} ${it.entryId}" },
                remove = { calls += "remove" },
                scope = CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
            )
    }
}
