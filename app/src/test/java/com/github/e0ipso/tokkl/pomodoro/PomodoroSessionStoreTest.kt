package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.api.ServerTimeEntry
import com.github.e0ipso.tokkl.cache.ChangeKind
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.cache.couldHaveMade
import com.github.e0ipso.tokkl.time.AccountTime
import com.github.e0ipso.tokkl.time.PomodoroSessionConfig
import com.github.e0ipso.tokkl.time.SessionPhase
import com.github.e0ipso.tokkl.time.SnappedSpan
import com.github.e0ipso.tokkl.time.plannedEnd
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pomodoro session on a transactional stand-in for the cache: four snapped entries meet, a
 * collapsed span is not queued, and a failure before or inside the transaction leaves neither
 * the session change nor the create. The create is an ordinary queue row, so a lost post is
 * [PendingChange.maybeSent].
 */
class PomodoroSessionStoreTest {

    private val now = AtomicReference(Instant.parse("2026-10-20T09:00:00Z"))
    private val clock = object : Clock() {
        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val database = MemoryDatabase()
    private var zoneKnown = true
    private var time: AccountTime? = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, 15.0)
    private var killTime = false
    private var queued = 0
    private val store = PomodoroSessionStore(
        database,
        Dispatchers.Unconfined,
        clock,
        accountTime = {
            if (killTime) throw IllegalStateException("killed before the transaction")
            if (!zoneKnown) null else time
        },
        onQueued = { queued += 1 },
    )

    @Test
    fun fourPomodorosMeetOnTheGridAndASkippedWorkCountsTowardTheLongRest() = runBlocking {
        at("2026-10-20T09:07:00Z")
        store.start(LENGTHS, FIELDS, WORKSPACE)

        val skipped = next("2026-10-20T09:10:00Z") as PomodoroStep.Running
        assertEquals("shortRest", skipped.session.phaseKind)
        assertEquals(1.0, skipped.session.completed, 0.0)
        assertEquals(parse("2026-10-20T09:10:00Z"), skipped.session.phaseStartedAt)
        assertTrue(skipped.session.phaseStartedAt.isBefore(parse("2026-10-20T09:32:00Z")))
        assertEquals(0, database.creates.size)
        assertEquals(0, queued)

        val first = next("2026-10-20T09:37:00Z") as PomodoroStep.Saved
        assertEquals(span("2026-10-20T09:00:00Z", "2026-10-20T09:30:00Z"), first.span)

        val overtime = next("2026-10-20T10:10:00Z") as PomodoroStep.Running
        assertEquals("shortRest", overtime.session.phaseKind)
        assertEquals(parse("2026-10-20T10:10:00Z"), overtime.session.phaseStartedAt)
        assertTrue(overtime.session.phaseStartedAt.isAfter(parse("2026-10-20T10:02:00Z")))

        val second = next("2026-10-20T10:15:00Z") as PomodoroStep.Saved
        assertEquals(span("2026-10-20T09:30:00Z", "2026-10-20T10:15:00Z"), second.span)

        next("2026-10-20T10:40:00Z")
        val third = next("2026-10-20T10:45:00Z") as PomodoroStep.Saved
        assertEquals(span("2026-10-20T10:15:00Z", "2026-10-20T10:45:00Z"), third.span)

        val longRest = next("2026-10-20T11:10:00Z") as PomodoroStep.Running
        assertEquals("longRest", longRest.session.phaseKind)
        assertEquals(4.0, longRest.session.completed, 0.0)

        val fourth = next("2026-10-20T11:25:00Z") as PomodoroStep.Saved
        assertEquals(span("2026-10-20T10:45:00Z", "2026-10-20T11:30:00Z"), fourth.span)
        assertEquals("work", fourth.session!!.phaseKind)
        assertEquals(0.0, fourth.session.completed, 0.0)
        assertEquals(parse("2026-10-20T11:25:00Z"), fourth.session.pomodoroStartedAt)

        val spans = database.creates.map { it.start to it.stop }
        assertEquals(4, spans.size)
        for (index in 0 until spans.lastIndex) {
            assertEquals("pomodoro ${index + 1} meets the next", spans[index].second, spans[index + 1].first)
        }
        assertEquals(4, queued)
        assertTrue(database.creates.all { it.description == "Focus" && it.kind == ChangeKind.CREATE && !it.maybeSent && it.stop != null })
    }

    @Test
    fun aSkipAndAnOvertimeEndThePhaseWhereTheUserActed() = runBlocking {
        at("2026-10-20T09:00:00Z")
        val started = store.start(LENGTHS, FIELDS, WORKSPACE)
        val plannedWork = plannedEnd(SessionPhase(started.phaseKind, started.phaseStartedAt, started.completed), LENGTHS)

        val skipped = next("2026-10-20T09:10:00Z") as PomodoroStep.Running
        assertEquals("shortRest", skipped.session.phaseKind)
        assertEquals(parse("2026-10-20T09:10:00Z"), skipped.session.phaseStartedAt)
        assertTrue(skipped.session.phaseStartedAt.isBefore(plannedWork))

        val closed = next("2026-10-20T09:20:00Z") as PomodoroStep.Saved
        assertEquals(span("2026-10-20T09:00:00Z", "2026-10-20T09:15:00Z"), closed.span)
        val continued = closed.session!!
        assertEquals("work", continued.phaseKind)
        assertEquals(parse("2026-10-20T09:20:00Z"), continued.pomodoroStartedAt)
        val plannedSecond = plannedEnd(SessionPhase(continued.phaseKind, continued.phaseStartedAt, continued.completed), LENGTHS)
        val overtime = next("2026-10-20T10:00:00Z") as PomodoroStep.Running
        assertEquals("shortRest", overtime.session.phaseKind)
        assertEquals(parse("2026-10-20T10:00:00Z"), overtime.session.phaseStartedAt)
        assertTrue(overtime.session.phaseStartedAt.isAfter(plannedSecond))
        assertEquals(1, database.creates.size)
    }

    @Test
    fun aPomodoroWhoseEndsSnapToTheSamePointIsNotQueued() = runBlocking {
        at("2026-10-20T09:08:00Z")
        store.start(LENGTHS, FIELDS, WORKSPACE)
        val ended = end("2026-10-20T09:21:00Z")
        assertTrue(ended is PomodoroStep.TooShort)
        assertEquals(null, (ended as PomodoroStep.TooShort).session)
        assertEquals(null, store.current())
        assertTrue(database.creates.isEmpty())
        assertEquals(0, queued)
    }

    @Test
    fun aFailureBeforeOrInsideTheTransactionLeavesNeitherWrite() {
        val started = runBlocking {
            at("2026-10-20T09:00:00Z")
            store.start(LENGTHS, FIELDS, WORKSPACE)
        }
        assertThrows(IllegalStateException::class.java) { runBlocking { store.start(LENGTHS, FIELDS, WORKSPACE) } }
        assertEquals(started, runBlocking { store.current() })
        now.set(parse("2026-10-20T09:30:00Z"))

        killTime = true
        assertThrows(IllegalStateException::class.java) { runBlocking { store.end() } }
        assertEquals(started, runBlocking { store.current() })
        assertTrue(database.creates.isEmpty())
        assertEquals(0, queued)

        killTime = false
        database.killInside = true
        assertThrows(IllegalStateException::class.java) { runBlocking { store.end() } }
        assertEquals(started, runBlocking { store.current() })
        assertTrue(database.creates.isEmpty())
        assertEquals(0, queued)

        database.killInside = false
        val saved = runBlocking { store.end() } as PomodoroStep.Saved
        assertEquals(null, saved.session)
        assertEquals(span("2026-10-20T09:00:00Z", "2026-10-20T09:30:00Z"), saved.span)
        assertEquals(null, runBlocking { store.current() })
        assertEquals(1, database.creates.size)
        assertEquals(1, queued)
    }

    @Test
    fun aCommittedCreateIsAnOrdinaryMaybeSentCreate() = runBlocking {
        at("2026-10-20T09:00:00Z")
        store.start(LENGTHS, FIELDS, WORKSPACE)
        val saved = end("2026-10-20T09:30:00Z") as PomodoroStep.Saved
        val change = database.creates.single()
        assertEquals(ChangeKind.CREATE, change.kind)
        assertFalse(change.maybeSent)
        assertEquals(null, change.capturedStop)
        assertFalse(change.shortStop)
        assertEquals(saved.span.start, change.start)
        assertEquals(saved.span.stop, change.stop)
        val server = ServerTimeEntry(
            id = 101,
            workspaceId = WORKSPACE,
            projectId = FIELDS.projectId,
            taskId = FIELDS.taskId,
            description = FIELDS.description,
            start = change.start.toString(),
            stop = change.stop!!.toString(),
            billable = FIELDS.billable,
            tags = FIELDS.tags,
        )
        assertTrue(change.couldHaveMade(server, setOf(change.start)))
        assertTrue(change.copy(maybeSent = true).couldHaveMade(server, setOf(change.start, saved.span.start)))
    }

    @Test
    fun anEditAfterAPomodoroDoesNotRewriteTheQueuedEntry() = runBlocking {
        at("2026-10-20T09:00:00Z")
        store.start(LENGTHS, FIELDS, WORKSPACE)
        next("2026-10-20T09:25:00Z")
        store.updateFields(FIELDS.copy(description = "Edited"))
        val saved = next("2026-10-20T09:30:00Z") as PomodoroStep.Saved
        assertEquals("Edited", saved.span.let { database.creates.single().description })
        assertEquals(span("2026-10-20T09:00:00Z", "2026-10-20T09:30:00Z"), saved.span)

        store.updateFields(FIELDS.copy(description = "Later"))
        assertEquals("Edited", database.creates.single().description)
        assertEquals("Later", store.current()!!.fields.description)

        val ended = end("2026-10-20T10:00:00Z") as PomodoroStep.Saved
        assertEquals(listOf("Edited", "Later"), database.creates.map { it.description })
        assertEquals("Later", ended.span.let { database.creates.last().description })
        assertEquals(span("2026-10-20T09:30:00Z", "2026-10-20T10:00:00Z"), ended.span)
    }

    @Test
    fun withoutAResolutionOrAZoneTheTimesStayAsTheyAre() = runBlocking {
        time = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, null)
        at("2026-10-20T09:00:20.500Z")
        store.start(LENGTHS, FIELDS, WORKSPACE)
        val saved = end("2026-10-20T09:00:40.250Z") as PomodoroStep.Saved
        assertEquals(parse("2026-10-20T09:00:20.500Z"), saved.span.start)
        assertEquals(parse("2026-10-20T09:00:40.250Z"), saved.span.stop)

        zoneKnown = false
        time = AccountTime(ZoneOffset.UTC, DayOfWeek.MONDAY, 15.0)
        at("2026-10-20T09:08:00Z")
        store.start(LENGTHS, FIELDS, WORKSPACE)
        val kept = end("2026-10-20T09:21:00Z") as PomodoroStep.Saved
        assertEquals(parse("2026-10-20T09:08:00Z"), kept.span.start)
        assertEquals(parse("2026-10-20T09:21:00Z"), kept.span.stop)
        assertEquals(2, database.creates.size)
    }

    @Test
    fun schema8KeepsEverySchema7TableAndAddsThePomodoroSession() {
        val older = schema(7)
        val newer = schema(8)
        assertEquals(7, older.version)
        assertEquals(8, newer.version)
        assertEquals("7747807ae7eb20f454ad9d96203e5375", older.identity)
        assertNotEquals(older.identity, newer.identity)
        assertEquals(older.tables.keys, newer.tables.keys - "pomodoro_session")
        for (name in older.tables.keys) {
            assertEquals(name, older.tables.getValue(name), newer.tables.getValue(name))
        }
        val create = newer.tables.getValue("pomodoro_session")
        assertTrue(create.contains("`pomodoroStartedAt` INTEGER NOT NULL"))
        assertTrue(create.contains("`fields_description` TEXT NOT NULL"))
        assertTrue(create.contains("`completed` REAL NOT NULL"))
        assertFalse(newer.tables.keys.any { it.contains("retry") })
    }

    private suspend fun next(text: String): PomodoroStep {
        at(text)
        return store.nextPhase()
    }

    private suspend fun end(text: String): PomodoroStep {
        at(text)
        return store.end()
    }

    private fun at(text: String) {
        now.set(parse(text))
    }

    /** The session row and the creates, one transaction at a time, rolled back when the block throws. */
    private class MemoryDatabase : PomodoroSessionDatabase {
        var session: PomodoroSession? = null
        val creates = mutableListOf<PendingChange>()
        var killInside = false
        private var depth = 0

        override fun current(): PomodoroSession? = session

        override fun save(session: PomodoroSession) {
            check(depth > 0) { "session write outside the transaction" }
            this.session = session
        }

        override fun delete() {
            check(depth > 0) { "session write outside the transaction" }
            session = null
        }

        override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long {
            check(depth > 0) { "queue write outside the transaction" }
            val sequence = (creates.maxOfOrNull { it.sequence } ?: 0) + 1
            val change = PendingChange(
                sequence = sequence,
                kind = ChangeKind.CREATE,
                entryId = -sequence,
                workspaceId = workspaceId,
                projectId = fields.projectId,
                taskId = fields.taskId,
                description = fields.description,
                start = start,
                stop = stop,
                billable = fields.billable,
                tags = fields.tags,
                tagIds = emptyList(),
            )
            creates += change
            if (killInside) throw IllegalStateException("killed inside the transaction")
            return change.entryId
        }

        override fun <T> transaction(block: () -> T): T {
            depth++
            val sessionBefore = session?.copy()
            val createsBefore = creates.toList()
            try {
                return block()
            } catch (e: RuntimeException) {
                session = sessionBefore
                creates.clear()
                creates.addAll(createsBefore)
                throw e
            } finally {
                depth--
            }
        }
    }

    private companion object {
        const val WORKSPACE = 7L
        val LENGTHS = PomodoroSessionConfig(25.0, 5.0, 15.0, 4.0)
        val FIELDS = EntryFields(description = "Focus", projectId = 3, taskId = 9, tags = listOf("deep"), billable = true)

        fun parse(text: String): Instant = Instant.parse(text)

        fun span(start: String, stop: String) = SnappedSpan(parse(start), parse(stop))

        fun schema(version: Int): SchemaFile {
            val module = generateSequence(File("").absoluteFile) { it.parentFile }
                .first { File(it, "src/main/AndroidManifest.xml").isFile }
            val file = File(module, "schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/$version.json")
            val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
            val tables = database.getValue("entities").jsonArray.associate { entity ->
                val row = entity.jsonObject
                row.getValue("tableName").jsonPrimitive.content to row.getValue("createSql").jsonPrimitive.content
            }
            return SchemaFile(
                database.getValue("version").jsonPrimitive.int,
                database.getValue("identityHash").jsonPrimitive.content,
                tables,
            )
        }
    }
}

private data class SchemaFile(val version: Int, val identity: String, val tables: Map<String, String>)
