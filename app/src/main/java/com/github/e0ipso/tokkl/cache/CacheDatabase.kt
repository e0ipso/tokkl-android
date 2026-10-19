package com.github.e0ipso.tokkl.cache

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The offline cache and the queue of unsent changes in the private database file [FILE_NAME]. It
 * stays on the phone, because the backup and data extraction rules leave out every domain. Room
 * exports each version's schema to app/schemas, which git tracks, so a later version can migrate
 * from it. Every version migrates from the one before and never drops the file, because that
 * would lose the queued changes.
 *
 * Version 2 adds the queue: `pending_changes` and `entry_ids`. Version 3 adds
 * `pending_changes.maybeSent`.
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
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(CacheConverters::class)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun cache(): CacheDao

    abstract fun changes(): ChangeDao

    companion object {
        const val FILE_NAME = "account_cache.db"

        /** Opens the file on the first query, so call this from any thread. */
        fun open(context: Context): CacheDatabase =
            Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, FILE_NAME).build()
    }
}
