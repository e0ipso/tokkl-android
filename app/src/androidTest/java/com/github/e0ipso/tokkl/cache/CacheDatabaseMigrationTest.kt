package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A cache written by version 1, before the queue, by version 2, before
 * `pending_changes.maybeSent`, or by version 3, before `pending_changes.existingIds`, opens under
 * the current version through Room's migrations, keeps its rows and its queue, and takes queued
 * changes. Versions 7 and 8, which held the phone's own pause chain, timer session and Pomodoro
 * session, open without those tables and keep their queue, which the sender then delivers to the
 * real Tokkl server. Room checks the migrated tables against the exported schema when it opens
 * the file, so a migration that left them wrong fails here.
 */
class CacheDatabaseMigrationTest {

    /** Signed in to the test server, so the test accounts' entry calls reach it. */
    @get:Rule val session = SessionRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun version1MigratesAndKeepsTheCachedRows() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_1.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO time_entries VALUES " +
                    "(42, 7, 3, NULL, 'Cached before the update', ${START.toEpochMilli()}, NULL, 0, '[\"focus\"]', '[5]')",
            )
            db.execSQL("INSERT INTO projects VALUES (3, 7, NULL, 'Website', '#0b83d9', 1, 0)")
            db.execSQL("INSERT INTO tags VALUES (5, 7, 'focus')")
            db.version = 1
        }

        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val cache = database.cache()
            assertEquals(
                listOf(CachedTimeEntry(42, 7, 3, null, "Cached before the update", START, null, false, listOf("focus"), listOf(5))),
                runBlocking { cache.timeEntries().first() },
            )
            assertEquals(listOf("Website"), runBlocking { cache.projects().first() }.map { it.name })

            val queue = ChangeQueue(database, Dispatchers.IO, onQueued = {})
            assertTrue("the migrated file takes a queued stop", runBlocking { queue.stop(42) })
            assertEquals(1, runBlocking { queue.unsentCount.first() })
            assertTrue(runBlocking { cache.timeEntriesWithSendState().first() }.single().unsent)
        } finally {
            database.close()
            context.deleteDatabase(FILE)
        }
    }

    @Test
    fun version2MigratesAndKeepsTheQueue() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_2.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO time_entries VALUES " +
                    "(-1, 7, NULL, NULL, 'Queued before the update', ${START.toEpochMilli()}, NULL, 0, '[]', '[]')",
            )
            db.execSQL(
                "INSERT INTO pending_changes VALUES " +
                    "(1, 'CREATE', -1, 7, NULL, NULL, 'Queued before the update', ${START.toEpochMilli()}, NULL, 0, '[]', '[]')",
            )
            db.execSQL("INSERT INTO entry_ids VALUES (-9, 99)")
            db.version = 2
        }

        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val store = RoomChangeStore(database)
            val queued = PendingChange(1, ChangeKind.CREATE, -1, 7, null, null, "Queued before the update", START, null, false, emptyList(), emptyList())
            assertEquals("the queued create survived, not marked as maybe sent", queued, store.first())
            assertTrue("the mapping survived", store.isMapped(99))

            store.maybeSent(queued, true)
            assertTrue(store.first()!!.maybeSent)
            assertEquals(1, runBlocking { database.changes().count().first() })
        } finally {
            database.close()
            context.deleteDatabase(FILE)
        }
    }

    @Test
    fun version3MigratesKeepsTheMarkedQueueAndRecordsTheServersEntriesOnANewCreate() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_3.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO time_entries VALUES " +
                    "(42, 7, NULL, NULL, 'From the server', ${START.toEpochMilli()}, ${START.plusSeconds(1800).toEpochMilli()}, 0, '[]', '[]')",
            )
            db.execSQL(
                "INSERT INTO pending_changes VALUES " +
                    "(1, 'CREATE', -1, 7, NULL, NULL, 'Queued before the update', ${START.toEpochMilli()}, NULL, 0, '[]', '[]', 1)",
            )
            db.version = 3
        }

        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val store = RoomChangeStore(database)
            val queued = PendingChange(1, ChangeKind.CREATE, -1, 7, null, null, "Queued before the update", START, null, false, emptyList(), emptyList(), maybeSent = true)
            assertEquals("the queued create survived, marked, with no ids", queued, store.first())

            val queue = ChangeQueue(database, Dispatchers.IO, onQueued = {})
            val copy = runBlocking { queue.create(7, EntryFields(description = "From the server"), START, START.plusSeconds(1800)) }
            assertEquals(
                "a create queued now records the server's entry around its start",
                listOf(42L),
                database.changes().changesFor(copy).single().existingIds,
            )
        } finally {
            database.close()
            context.deleteDatabase(FILE)
        }
    }

    @Test
    fun version4MigratesAndHeldChangesSurviveAReopen() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_3.forEach(db::execSQL)
            db.execSQL("ALTER TABLE pending_changes ADD COLUMN existingIds TEXT NOT NULL DEFAULT '[]'")
            db.execSQL(
                "INSERT INTO pending_changes VALUES " +
                    "(1, 'CREATE', -1, 7, NULL, NULL, 'Draft', ${START.toEpochMilli()}, NULL, 0, '[]', '[]', 0, '[]')",
            )
            db.version = 4
        }
        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val store = RoomChangeStore(database)
            val draft = checkNotNull(store.first())
            assertEquals(false, draft.held)
            assertEquals(null, draft.refusal)
            store.hold(draft, "Server validation failed")
        } finally {
            database.close()
        }
        val reopened = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val held = runBlocking { reopened.changes().held().first() }.single()
            assertEquals("Server validation failed", held.refusal)
            assertEquals(null, RoomChangeStore(reopened).first())
            assertEquals(1, reopened.changes().release(held.sequence))
            assertEquals("Draft", RoomChangeStore(reopened).first()!!.description)
        } finally {
            reopened.close()
            assertTrue(context.deleteDatabase(FILE))
        }
    }

    @Test
    fun version5KeepsAnAlreadyQueuedStopAndAddsUndoStorage() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_3.forEach(db::execSQL)
            db.execSQL("ALTER TABLE pending_changes ADD COLUMN existingIds TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE pending_changes ADD COLUMN held INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE pending_changes ADD COLUMN refusal TEXT")
            db.execSQL(
                "INSERT INTO pending_changes VALUES " +
                    "(1, 'STOP', 42, 7, NULL, NULL, 'Earlier tap', ${START.toEpochMilli()}, " +
                    "${START.plusSeconds(300).toEpochMilli()}, 0, '[]', '[]', 0, '[]', 0, NULL)",
            )
            db.execSQL("INSERT INTO time_entries VALUES (42, 7, NULL, NULL, 'Earlier tap', " +
                "${START.toEpochMilli()}, ${START.plusSeconds(300).toEpochMilli()}, 0, '[]', '[]')")
            db.execSQL("UPDATE room_master_table SET identity_hash = '7b9d9c485b587e3975dadd777dfeccae' WHERE id = 42")
            db.version = 5
        }

        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val old = checkNotNull(RoomChangeStore(database).first())
            assertEquals(ChangeKind.STOP, old.kind)
            assertEquals(null, old.capturedStop)
            assertEquals(false, old.shortStop)
            assertEquals(START.plusSeconds(300), old.stop)
            assertEquals(1, runBlocking { database.changes().count().first() })
            assertEquals(null, runBlocking { database.changes().latestShortStop().first() })
        } finally {
            database.close()
            assertTrue(context.deleteDatabase(FILE))
        }
    }

    @Test
    fun version6KeepsARunningTimerAndItsQueueAndStopsItAfterTheUpdate() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_6.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO pending_changes VALUES " +
                    "(1, 'CREATE', -1, 7, NULL, NULL, 'Running before the update', ${START.toEpochMilli()}, NULL, 0, " +
                    "'[]', '[]', 0, '[]', 0, NULL, NULL, 0)",
            )
            db.execSQL("INSERT INTO time_entries VALUES (-1, 7, NULL, NULL, 'Running before the update', ${START.toEpochMilli()}, NULL, 0, '[]', '[]')")
            db.version = 6
        }

        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            assertEquals("Running before the update", RoomChangeStore(database).first()!!.description)
            val running = checkNotNull(database.changes().latestRunning())
            assertEquals(-1L, running.entry.id)
            assertTrue("the queued start shows unsent", running.unsent)
            val queue = ChangeQueue(database, Dispatchers.IO, onQueued = {})
            assertTrue("the migrated file takes a Stop", runBlocking { queue.stop(-1) })
            assertEquals(listOf(ChangeKind.CREATE, ChangeKind.STOP), database.changes().changesFor(-1).map { it.kind })
        } finally {
            database.close()
            assertTrue(context.deleteDatabase(FILE))
        }
    }

    @Test
    fun version7KeepsTheQueueAndLeavesNoLocalTimerTables() {
        context.deleteDatabase(FILE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
            VERSION_7.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO pending_changes VALUES " +
                    "(1, 'CREATE', -1, 7, NULL, NULL, 'Queued before the update', ${START.toEpochMilli()}, NULL, 0, " +
                    "'[]', '[]', 0, '[]', 0, NULL, NULL, 0)",
            )
            db.execSQL(
                "INSERT INTO pause_chain VALUES " +
                    "(0, 11, 2, 3, 600, NULL, 7, 'Paused focus', NULL, NULL, '[]', 0)",
            )
            db.execSQL("INSERT INTO timer_session VALUES (0, 44)")
            db.version = 7
        }

        val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
        try {
            val queued = checkNotNull(RoomChangeStore(database).first())
            assertEquals("Queued before the update", queued.description)
            assertEquals(false, queued.maybeSent)
            assertEquals("no local timer table is left", emptySet<String>(), tables(database) intersect RETIRED_TABLES)
        } finally {
            database.close()
            assertTrue(context.deleteDatabase(FILE))
        }
    }

    /**
     * A version 8 file as the last build with the phone's own Pause and Pomodoro left it: a saved
     * pomodoro and a captured Stop still queued, an edit the server refused, an id mapping, a short
     * Stop's Undo offer, and rows in all three local timer tables. Version 9 drops those tables and
     * nothing else, and the existing sender delivers the queue to the real server.
     */
    @Test
    fun version8DropsTheLocalTimerTablesAndItsQueueStillSends() {
        val account = TestAccounts.create(timezone = "Etc/UTC")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val workspace = account.workspaceId
            // Whole hours are on every grid the server snaps to.
            val base = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(6))
            val kept = account.createEntry("Kept on the server", base.plus(HOUR.multipliedBy(4)), base.plus(HOUR.multipliedBy(5)))
            val running = account.createEntry("Running before the update", base.plus(HOUR.multipliedBy(2)))
            val stoppedAt = base.plus(HOUR.multipliedBy(3))
            val shortAt = base.plus(HOUR)
            val undo = ShortStopUndo(
                77, shortAt.plusSeconds(10),
                CachedTimeEntry(77, workspace, null, null, "Short before the update", shortAt, shortAt, false, emptyList(), emptyList()),
            )
            context.deleteDatabase(FILE)
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(FILE), null).use { db ->
                VERSION_8.forEach(db::execSQL)
                // A pomodoro the phone's own session saved: an ordinary stopped create.
                db.execSQL(entryRow(-1, workspace, "Saved pomodoro before the update", base, base.plus(HOUR)))
                db.execSQL(changeRow(1, "CREATE", -1, workspace, "Saved pomodoro before the update", base, base.plus(HOUR)))
                // A captured Stop of the timer the server runs.
                db.execSQL(entryRow(running, workspace, "Running before the update", base.plus(HOUR.multipliedBy(2)), stoppedAt))
                db.execSQL(
                    changeRow(2, "STOP", running, workspace, "Running before the update", base.plus(HOUR.multipliedBy(2)), stoppedAt,
                        capturedStop = stoppedAt),
                )
                // An edit the server refused, which waits for the user.
                db.execSQL(entryRow(kept, workspace, "Refused edit", base.plus(HOUR.multipliedBy(4)), base.plus(HOUR.multipliedBy(5))))
                db.execSQL(
                    changeRow(3, "UPDATE", kept, workspace, "Refused edit", base.plus(HOUR.multipliedBy(4)), base.plus(HOUR.multipliedBy(5)),
                        held = "1, 'Server validation failed'"),
                )
                db.execSQL("INSERT INTO entry_ids VALUES (-9, 99)")
                db.execSQL(
                    "INSERT INTO short_stop_undo VALUES (77, ${undo.capturedAt.toEpochMilli()}, 77, $workspace, NULL, NULL, " +
                        "'Short before the update', ${shortAt.toEpochMilli()}, ${shortAt.toEpochMilli()}, 0, '[]', '[]')",
                )
                db.execSQL("INSERT INTO pause_chain VALUES (0, 11, 2, 3, 600, NULL, $workspace, 'Paused focus', NULL, NULL, '[]', 0)")
                db.execSQL("INSERT INTO pause_chain VALUES (1, 11, 0, 1, 600, $running, $workspace, 'Paused focus', NULL, NULL, '[]', 0)")
                db.execSQL("INSERT INTO timer_session VALUES (0, 44)")
                db.execSQL(
                    "INSERT INTO pomodoro_session VALUES (0, $workspace, 25.0, 5.0, 15.0, 4.0, 'work', " +
                        "${base.toEpochMilli()}, ${base.toEpochMilli()}, 0.0, 'Focus', NULL, NULL, '[]', 0)",
                )
                db.version = 8
            }

            val database = Room.databaseBuilder(context, CacheDatabase::class.java, FILE).build()
            try {
                val tables = tables(database)
                assertEquals("the local timer tables are gone", emptySet<String>(), tables intersect RETIRED_TABLES)
                assertTrue("every queue table stays: $tables", tables.containsAll(QUEUE_TABLES))
                val changes = database.changes()
                assertEquals(3, runBlocking { changes.count().first() })
                assertEquals(listOf(ChangeKind.CREATE), changes.changesFor(-1).map { it.kind })
                assertEquals(stoppedAt, changes.changesFor(running).single().capturedStop)
                val held = runBlocking { changes.held().first() }.single()
                assertEquals(kept, held.entryId)
                assertEquals("Server validation failed", held.refusal)
                assertTrue("the old id mapping stays", changes.isMapped(99))
                assertEquals("the short Stop's Undo offer stays", undo, runBlocking { changes.latestShortStop().first() })

                val sender = ChangeSender(
                    RoomChangeStore(database),
                    client = { TogglApiClient(TokklTestServer.url, ApiCredentials.ApiToken(account.apiToken)) },
                    scope = scope, io = Dispatchers.IO,
                )
                val outcome = runBlocking { sender.sendAndWait() }
                assertTrue("the drain ended with only the held edit left: $outcome", outcome is ChangeSender.Outcome.Empty)

                val created = checkNotNull(changes.serverId(-1)) { "the server answered the saved pomodoro" }
                val saved = account.entry(created)
                assertEquals("Saved pomodoro before the update", saved.text("description"))
                assertEquals(base, saved.instant("start"))
                assertEquals(base.plus(HOUR), saved.instant("stop"))
                assertEquals("the captured Stop reached the server", stoppedAt, account.entry(running).instant("stop"))
                assertEquals("the held edit stayed on the phone", "Kept on the server", account.entry(kept).text("description"))
                assertEquals(listOf(held), runBlocking { changes.held().first() })
                assertEquals(1, runBlocking { changes.count().first() })
                assertTrue(changes.isMapped(99))
                assertEquals(undo, runBlocking { changes.latestShortStop().first() })
                assertTrue("Undo still queues its restore", runBlocking { ChangeQueue(database, Dispatchers.IO, onQueued = {}).undoShortStop(77) })
                assertEquals(listOf(ChangeKind.RESTORE), changes.changesFor(77).map { it.kind })
            } finally {
                database.close()
                assertTrue(context.deleteDatabase(FILE))
            }
        } finally {
            scope.cancel()
            TestAccounts.delete(account)
        }
    }

    /** The names of the tables in [database]'s file. */
    private fun tables(database: CacheDatabase): Set<String> =
        database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    // Android 12's Instant.parse rejects Tokkl's +00:00 timestamps; OffsetDateTime accepts them.
    private fun JsonObject.instant(key: String): Instant = OffsetDateTime.parse(text(key)).toInstant()

    private companion object {
        const val FILE = "cache-migration-test.db"
        val START: Instant = Instant.parse("2026-10-19T08:00:00Z")

        /** The tables of version 1, from app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/1.json. */
        val VERSION_1 = listOf(
            "CREATE TABLE IF NOT EXISTS `time_entries` (`id` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, " +
                "`projectId` INTEGER, `taskId` INTEGER, `description` TEXT NOT NULL, `start` INTEGER NOT NULL, " +
                "`stop` INTEGER, `billable` INTEGER NOT NULL, `tags` TEXT NOT NULL, `tagIds` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_time_entries_start` ON `time_entries` (`start`)",
            "CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, " +
                "`clientId` INTEGER, `name` TEXT NOT NULL, `color` TEXT, `active` INTEGER NOT NULL, " +
                "`billable` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `clients` (`id` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, " +
                "`projectId` INTEGER, `name` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `favorites` (`position` INTEGER NOT NULL, `description` TEXT NOT NULL, " +
                "`projectId` INTEGER, `taskId` INTEGER, `tags` TEXT NOT NULL, `billable` INTEGER NOT NULL, " +
                "PRIMARY KEY(`position`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8107a6946224f42e71b1e5b586a3e59c')",
        )

        /**
         * The tables of version 2, from app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/2.json:
         * those of version 1 and the queue's.
         */
        val VERSION_2 = VERSION_1.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `pending_changes` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, `entryId` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, `projectId` INTEGER, " +
                "`taskId` INTEGER, `description` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER, " +
                "`billable` INTEGER NOT NULL, `tags` TEXT NOT NULL, `tagIds` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_pending_changes_entryId` ON `pending_changes` (`entryId`)",
            "CREATE TABLE IF NOT EXISTS `entry_ids` (`localId` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, PRIMARY KEY(`localId`))",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '885651807ba8ad3b390113ed5f804c2f')",
        )

        /**
         * The tables of version 3, from app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/3.json:
         * those of version 2, with `pending_changes.maybeSent`.
         */
        val VERSION_3 = VERSION_1.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `pending_changes` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, `entryId` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, `projectId` INTEGER, " +
                "`taskId` INTEGER, `description` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER, " +
                "`billable` INTEGER NOT NULL, `tags` TEXT NOT NULL, `tagIds` TEXT NOT NULL, `maybeSent` INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX IF NOT EXISTS `index_pending_changes_entryId` ON `pending_changes` (`entryId`)",
            "CREATE TABLE IF NOT EXISTS `entry_ids` (`localId` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, PRIMARY KEY(`localId`))",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3c6b11c6c52fc8eca243b7d3d98dae21')",
        )

        /**
         * The tables of version 6, from app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/6.json:
         * those of version 3, with the queue's later columns and the short Stop's Undo.
         */
        val VERSION_6 = VERSION_1.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `pending_changes` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, `entryId` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, `projectId` INTEGER, " +
                "`taskId` INTEGER, `description` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER, " +
                "`billable` INTEGER NOT NULL, `tags` TEXT NOT NULL, `tagIds` TEXT NOT NULL, `maybeSent` INTEGER NOT NULL DEFAULT 0, " +
                "`existingIds` TEXT NOT NULL DEFAULT '[]', `held` INTEGER NOT NULL DEFAULT 0, `refusal` TEXT, " +
                "`capturedStop` INTEGER, `shortStop` INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX IF NOT EXISTS `index_pending_changes_entryId` ON `pending_changes` (`entryId`)",
            "CREATE TABLE IF NOT EXISTS `entry_ids` (`localId` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, PRIMARY KEY(`localId`))",
            "CREATE TABLE IF NOT EXISTS `short_stop_undo` (`entryId` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL, " +
                "`stopped_id` INTEGER NOT NULL, `stopped_workspaceId` INTEGER NOT NULL, `stopped_projectId` INTEGER, " +
                "`stopped_taskId` INTEGER, `stopped_description` TEXT NOT NULL, `stopped_start` INTEGER NOT NULL, " +
                "`stopped_stop` INTEGER, `stopped_billable` INTEGER NOT NULL, `stopped_tags` TEXT NOT NULL, " +
                "`stopped_tagIds` TEXT NOT NULL, PRIMARY KEY(`entryId`))",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '056926b082c8c580bac4595b21f4295a')",
        )

        /**
         * The tables of version 7, from app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/7.json:
         * those of version 6, plus the pause chain and the timer session.
         */
        val VERSION_7 = VERSION_6.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `pause_chain` (`slot` INTEGER NOT NULL, `chainId` INTEGER NOT NULL, " +
                "`revision` INTEGER NOT NULL, `epoch` INTEGER NOT NULL, `stoppedSeconds` INTEGER NOT NULL, " +
                "`runningId` INTEGER, `workspaceId` INTEGER NOT NULL, `snapshot_description` TEXT NOT NULL, " +
                "`snapshot_projectId` INTEGER, `snapshot_taskId` INTEGER, `snapshot_tags` TEXT NOT NULL, " +
                "`snapshot_billable` INTEGER NOT NULL, PRIMARY KEY(`slot`))",
            "CREATE TABLE IF NOT EXISTS `timer_session` (`slot` INTEGER NOT NULL, `id` INTEGER NOT NULL, PRIMARY KEY(`slot`))",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7747807ae7eb20f454ad9d96203e5375')",
        )

        /**
         * The tables of version 8, from app/schemas/com.github.e0ipso.tokkl.cache.CacheDatabase/8.json:
         * those of version 7, plus the Pomodoro session.
         */
        val VERSION_8 = VERSION_7.dropLast(1) + listOf(
            "CREATE TABLE IF NOT EXISTS `pomodoro_session` (`slot` INTEGER NOT NULL, `workspaceId` INTEGER NOT NULL, " +
                "`workMinutes` REAL NOT NULL, `shortRestMinutes` REAL NOT NULL, `longRestMinutes` REAL NOT NULL, " +
                "`longRestEvery` REAL NOT NULL, `phaseKind` TEXT NOT NULL, `phaseStartedAt` INTEGER NOT NULL, " +
                "`pomodoroStartedAt` INTEGER NOT NULL, `completed` REAL NOT NULL, `fields_description` TEXT NOT NULL, " +
                "`fields_projectId` INTEGER, `fields_taskId` INTEGER, `fields_tags` TEXT NOT NULL, " +
                "`fields_billable` INTEGER NOT NULL, PRIMARY KEY(`slot`))",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a594909fd1789ae86cc781bcdf9451b9')",
        )

        /** The tables of the phone's own pause chain, timer session and Pomodoro session, which version 9 drops. */
        val RETIRED_TABLES = setOf("pause_chain", "timer_session", "pomodoro_session")

        /** The queue's tables, which every version since 6 keeps. */
        val QUEUE_TABLES = setOf("time_entries", "pending_changes", "entry_ids", "short_stop_undo")

        val HOUR: Duration = Duration.ofHours(1)

        /** A `time_entries` row of versions 1 and later. */
        fun entryRow(id: Long, workspace: Long, description: String, start: Instant, stop: Instant?) =
            "INSERT INTO time_entries VALUES ($id, $workspace, NULL, NULL, '$description', ${start.toEpochMilli()}, " +
                "${stop?.toEpochMilli()}, 0, '[]', '[]')"

        /** A `pending_changes` row of versions 6 to 9, not sent, with [held] as its `held, refusal` columns. */
        fun changeRow(
            sequence: Long,
            kind: String,
            entryId: Long,
            workspace: Long,
            description: String,
            start: Instant,
            stop: Instant?,
            capturedStop: Instant? = null,
            held: String = "0, NULL",
        ) = "INSERT INTO pending_changes VALUES ($sequence, '$kind', $entryId, $workspace, NULL, NULL, '$description', " +
            "${start.toEpochMilli()}, ${stop?.toEpochMilli()}, 0, '[]', '[]', 0, '[]', $held, ${capturedStop?.toEpochMilli()}, 0)"
    }
}
