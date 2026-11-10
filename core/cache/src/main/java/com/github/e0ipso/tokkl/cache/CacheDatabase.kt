package com.github.e0ipso.tokkl.cache

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec

/**
 * The offline cache and the queue of unsent changes in the private database file [FILE_NAME]. It
 * stays on the phone, because the backup and data extraction rules leave out every domain. Room
 * exports each version's schema to core/cache/schemas, which git tracks, so a later version can
 * migrate from it. Every version migrates from the one before and never drops the file, because
 * that would lose the queued changes.
 *
 * Version 9 drops the phone's own pause chain and session tables, as the server now keeps them,
 * and carries nothing from them. Version 10 adds `allDay` and `day`; existing rows read as timed.
 */
@Database(
    entities = [
        CachedTimeEntry::class,
        CachedProject::class,
        CachedClient::class,
        CachedTag::class,
        CachedTask::class,
        CachedFavorite::class,
        PendingChange::class,
        EntryIdMapping::class,
        ShortStopUndo::class,
    ],
    version = 10,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = CacheDatabase.DropLocalTimerTables::class),
        AutoMigration(from = 9, to = 10),
    ],
)
@TypeConverters(CacheConverters::class)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun cache(): CacheDao

    abstract fun changes(): ChangeDao

    /** Version 8 to 9: the phone's own pause chain, timer session and Pomodoro session go. */
    @DeleteTable.Entries(
        DeleteTable(tableName = "pomodoro_session"),
        DeleteTable(tableName = "pause_chain"),
        DeleteTable(tableName = "timer_session"),
    )
    class DropLocalTimerTables : AutoMigrationSpec

    companion object {
        const val FILE_NAME = "account_cache.db"

        /** Opens the file on the first query, so call this from any thread. */
        fun open(context: Context): CacheDatabase =
            Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, FILE_NAME).build()
    }
}
