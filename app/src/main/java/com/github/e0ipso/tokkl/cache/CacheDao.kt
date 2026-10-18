package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Reads and replaces the cached rows. The flows emit the current rows and again after every
 * change. [refresh] blocks on disk I/O, so call it off the main thread.
 */
@Dao
abstract class CacheDao {

    /** The newest entry first. */
    @Query("SELECT * FROM time_entries ORDER BY start DESC, id DESC")
    abstract fun timeEntries(): Flow<List<CachedTimeEntry>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE, id")
    abstract fun projects(): Flow<List<CachedProject>>

    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE, id")
    abstract fun clients(): Flow<List<CachedClient>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE, id")
    abstract fun tags(): Flow<List<CachedTag>>

    @Query("SELECT * FROM tasks ORDER BY name COLLATE NOCASE, id")
    abstract fun tasks(): Flow<List<CachedTask>>

    /** In the user's order. */
    @Query("SELECT * FROM favorites ORDER BY position")
    abstract fun favorites(): Flow<List<CachedFavorite>>

    /**
     * Replaces every row with what [change] makes of the current rows, in one transaction, so a
     * reader sees the cache before or after the refresh and never part way.
     */
    @Transaction
    open fun refresh(change: (CacheContents) -> CacheContents) {
        val current = CacheContents(
            timeEntries = allTimeEntries(),
            projects = allProjects(),
            clients = allClients(),
            tags = allTags(),
            tasks = allTasks(),
            favorites = allFavorites(),
        )
        val next = change(current)
        deleteTimeEntries()
        insertTimeEntries(next.timeEntries)
        deleteProjects()
        insertProjects(next.projects)
        deleteClients()
        insertClients(next.clients)
        deleteTags()
        insertTags(next.tags)
        deleteTasks()
        insertTasks(next.tasks)
        deleteFavorites()
        insertFavorites(next.favorites)
    }

    @Query("SELECT * FROM time_entries")
    protected abstract fun allTimeEntries(): List<CachedTimeEntry>

    @Query("SELECT * FROM projects")
    protected abstract fun allProjects(): List<CachedProject>

    @Query("SELECT * FROM clients")
    protected abstract fun allClients(): List<CachedClient>

    @Query("SELECT * FROM tags")
    protected abstract fun allTags(): List<CachedTag>

    @Query("SELECT * FROM tasks")
    protected abstract fun allTasks(): List<CachedTask>

    @Query("SELECT * FROM favorites")
    protected abstract fun allFavorites(): List<CachedFavorite>

    @Query("DELETE FROM time_entries")
    protected abstract fun deleteTimeEntries()

    @Query("DELETE FROM projects")
    protected abstract fun deleteProjects()

    @Query("DELETE FROM clients")
    protected abstract fun deleteClients()

    @Query("DELETE FROM tags")
    protected abstract fun deleteTags()

    @Query("DELETE FROM tasks")
    protected abstract fun deleteTasks()

    @Query("DELETE FROM favorites")
    protected abstract fun deleteFavorites()

    @Insert
    protected abstract fun insertTimeEntries(rows: List<CachedTimeEntry>)

    @Insert
    protected abstract fun insertProjects(rows: List<CachedProject>)

    @Insert
    protected abstract fun insertClients(rows: List<CachedClient>)

    @Insert
    protected abstract fun insertTags(rows: List<CachedTag>)

    @Insert
    protected abstract fun insertTasks(rows: List<CachedTask>)

    @Insert
    protected abstract fun insertFavorites(rows: List<CachedFavorite>)
}
