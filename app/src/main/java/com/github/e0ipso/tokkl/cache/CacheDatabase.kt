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
 * `pending_changes.maybeSent`, version 4 `pending_changes.existingIds`, and version 5
 * `pending_changes.held` and `pending_changes.refusal`. Version 6 keeps the immutable captured
 * Stop instant and short Stop Undo records without changing older queued requests. Version 7
 * adds `pause_chain` and `timer_session` for Pause and Resume and leaves every earlier row as it
 * is.
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
        PauseChain::class,
        TimerSession::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
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
