package com.github.e0ipso.tokkl.cache

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.model.EntryFields
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.EntryTiming
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.time.AccountTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A no-time entry keeps its flag and day through every change the queue makes to it, in the
 * queued change and in the cache row, with the real Room transaction. No sender runs.
 */
class NoTimeQueueTest {
    @get:Rule
    val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CacheDatabase
    private lateinit var queue: ChangeQueue
    private val dao get() = database.changes()

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        queue = ChangeQueue(
            RoomChangeStore(database),
            Dispatchers.IO,
            onQueued = {},
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            spans = ServerSpans.Unsnapped,
        )
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun createQueuesANoTimeEntryOnItsDay() {
        runBlocking {
            // The phone holds a server entry that ended the evening before, and one that day.
            dao.upsertEntry(timed(NEAR, MIDNIGHT.minusSeconds(1_800), MIDNIGHT.minusSeconds(60)))
            dao.upsertEntry(timed(FAR, MIDNIGHT.plusSeconds(3 * 3_600), MIDNIGHT.plusSeconds(4 * 3_600)))

            val id = queue.create(
                WorkspaceId(WORKSPACE),
                EntryFields("Planning"),
                EntryTiming.AllDay(DAY, MIDNIGHT, Duration.ofSeconds(5_400)),
            )

            val create = dao.changesFor(id.value).single()
            assertEquals(ChangeKind.CREATE, create.kind)
            assertNoTime(DAY, create.entry())
            assertEquals(MIDNIGHT, create.start)
            assertEquals(MIDNIGHT.plusSeconds(5_400), create.stop)
            assertEquals("the lost-post lookup skips the entries around midnight", listOf(NEAR), create.existingIds)
            assertNoTime(DAY, dao.entry(id.value)!!)
        }
    }

    @Test
    fun descriptionEditKeepsTheEntryOnItsDay() {
        runBlocking {
            // A server row has no day of its own; the form passes the one its midnight falls on.
            dao.upsertEntry(serverNoTime(SERVER_ID, day = null))

            assertTrue(
                queue.update(
                    EntryId(SERVER_ID),
                    EntryFields("Renamed"),
                    EntryTiming.AllDay(DAY, MIDNIGHT, Duration.ofSeconds(7_200)),
                ),
            )

            val update = dao.changesFor(SERVER_ID).single()
            assertEquals(ChangeKind.UPDATE, update.kind)
            assertEquals("Renamed", update.description)
            assertNoTime(DAY, update.entry())
            val row = dao.entry(SERVER_ID)!!
            assertEquals("Renamed", row.description)
            assertEquals(MIDNIGHT, row.start)
            assertNoTime(DAY, row)
        }
    }

    @Test
    fun olderEntryEditKeepsTheFlagAndDay() {
        runBlocking {
            queue.updateOlder(
                EntryId(OLDER_ID),
                WorkspaceId(WORKSPACE),
                EntryFields("Older"),
                EntryTiming.AllDay(DAY, MIDNIGHT, Duration.ofSeconds(3_600)),
            )

            val update = dao.changesFor(OLDER_ID).single()
            assertEquals(ChangeKind.UPDATE, update.kind)
            assertEquals(WORKSPACE, update.workspaceId)
            assertNoTime(DAY, update.entry())
            assertNoTime(DAY, dao.entry(OLDER_ID)!!)
        }
    }

    @Test
    fun editFoldedIntoAHeldCreateKeepsTheFlagAndTakesTheNewDay() {
        runBlocking {
            val id = queue.create(
                WorkspaceId(WORKSPACE),
                EntryFields("Refused"),
                EntryTiming.AllDay(DAY, MIDNIGHT, Duration.ofSeconds(5_400)),
            )
            val create = dao.changesFor(id.value).single()
            dao.hold(create.sequence, "No-time entries can be at most 24 hours")

            val earlier = AT.instantAt(EARLIER_DAY, 0)
            assertTrue(
                queue.update(
                    id,
                    EntryFields("Fixed"),
                    EntryTiming.AllDay(EARLIER_DAY, earlier, Duration.ofSeconds(3_600)),
                ),
            )

            val folded = dao.changesFor(id.value).single()
            assertEquals("the fix replaces the held create", create.sequence, folded.sequence)
            assertEquals(ChangeKind.CREATE, folded.kind)
            assertFalse("the fix lets the create go out again", folded.held)
            assertEquals("Fixed", folded.description)
            assertEquals(earlier, folded.start)
            assertNoTime(EARLIER_DAY, folded.entry())
            assertNoTime(EARLIER_DAY, dao.entry(id.value)!!)
        }
    }

    @Test
    fun deleteCarriesTheFlagAndDayForUndo() {
        runBlocking {
            val id = queue.create(
                WorkspaceId(WORKSPACE),
                EntryFields("Gone"),
                EntryTiming.AllDay(DAY, MIDNIGHT, Duration.ofSeconds(1_800)),
            )

            assertTrue(queue.delete(id))

            val delete = dao.changesFor(id.value).last()
            assertEquals(ChangeKind.DELETE, delete.kind)
            assertNoTime(DAY, delete.entry())
            assertNull(dao.entry(id.value))
        }
    }

    @Test
    fun editWithoutADayTurnsTheEntryTimed() {
        runBlocking {
            val id = queue.create(
                WorkspaceId(WORKSPACE),
                EntryFields("Was all day"),
                EntryTiming.AllDay(DAY, MIDNIGHT, Duration.ofSeconds(3_600)),
            )
            val start = AT.instantAt(DAY, 9 * 60)

            assertTrue(
                queue.update(id, EntryFields("Was all day"), EntryTiming.Stopped(start, start.plusSeconds(3_600))),
            )

            val update = dao.changesFor(id.value).last()
            assertEquals(ChangeKind.UPDATE, update.kind)
            assertTimed(update.entry())
            assertEquals(start, update.start)
            val row = dao.entry(id.value)!!
            assertTimed(row)
            assertEquals(start, row.start)
        }
    }

    private fun assertNoTime(day: LocalDate, entry: CachedTimeEntry) {
        assertTrue("${entry.id} is a no-time entry", entry.allDay)
        assertEquals(day, entry.day)
    }

    private fun assertTimed(entry: CachedTimeEntry) {
        assertFalse("${entry.id} is timed", entry.allDay)
        assertNull(entry.day)
    }

    private fun timed(id: Long, start: Instant, stop: Instant) =
        CachedTimeEntry(id, WORKSPACE, null, null, "Server", start, stop, false, emptyList(), emptyList())

    private fun serverNoTime(id: Long, day: LocalDate?) = CachedTimeEntry(
        id, WORKSPACE, null, null, "Server all day", MIDNIGHT,
        MIDNIGHT.plusSeconds(
            7_200,
        ),
        false, emptyList(), emptyList(),
        allDay = true, day = day,
    )

    private companion object {
        const val WORKSPACE = 20L
        const val SERVER_ID = 101L
        const val OLDER_ID = 301L
        const val NEAR = 401L
        const val FAR = 402L
        val AT = AccountTime(ZoneId.of("Europe/Madrid"), DayOfWeek.MONDAY, timeResolutionMinutes = null)
        val DAY: LocalDate = LocalDate.of(2026, 11, 3)
        val EARLIER_DAY: LocalDate = LocalDate.of(2026, 11, 2)
        val MIDNIGHT: Instant = AT.instantAt(DAY, 0)
        val NOW: Instant = Instant.parse("2026-11-04T10:00:00Z")
    }
}
