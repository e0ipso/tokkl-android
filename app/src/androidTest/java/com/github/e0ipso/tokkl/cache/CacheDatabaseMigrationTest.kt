package com.github.e0ipso.tokkl.cache

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cache written by version 1, before the queue, by version 2, before
 * `pending_changes.maybeSent`, or by version 3, before `pending_changes.existingIds`, opens under
 * the current version through Room's migrations, keeps its rows and its queue, and takes queued
 * changes. Room checks the migrated tables against the exported schema when it opens the file, so
 * a migration that left them wrong fails here.
 */
class CacheDatabaseMigrationTest {

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
    }
}
