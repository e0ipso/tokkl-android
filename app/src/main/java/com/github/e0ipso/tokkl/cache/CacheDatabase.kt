package com.github.e0ipso.tokkl.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The offline cache in the private database file [FILE_NAME]. It stays on the phone, because the
 * backup and data extraction rules leave out every domain. Room exports each version's schema to
 * app/schemas, which git tracks, so a later version can migrate from it.
 */
@Database(
    entities = [
        CachedTimeEntry::class,
        CachedProject::class,
        CachedClient::class,
        CachedTag::class,
        CachedTask::class,
        CachedFavorite::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CacheConverters::class)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun cache(): CacheDao

    companion object {
        const val FILE_NAME = "account_cache.db"

        /** Opens the file on the first query, so call this from any thread. */
        fun open(context: Context): CacheDatabase =
            Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, FILE_NAME).build()
    }
}
