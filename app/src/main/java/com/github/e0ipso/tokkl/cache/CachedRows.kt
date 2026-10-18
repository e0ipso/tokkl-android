package com.github.e0ipso.tokkl.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// The rows of the offline cache (docs/PRD.md O2): what the app last loaded from the server. The
// server is the source of truth, and a refresh replaces these rows with its values. Ids are the
// server's. The account settings are not here: AccountSettingsStore keeps them, and
// AccountSettingsSource is the one place to read them.

/** A time entry that started within the cached history. [stop] is null while it runs. */
@Entity(tableName = "time_entries", indices = [Index("start")])
data class CachedTimeEntry(
    @PrimaryKey val id: Long,
    val workspaceId: Long,
    val projectId: Long?,
    val taskId: Long?,
    val description: String,
    val start: Instant,
    val stop: Instant?,
    val billable: Boolean,
    val tags: List<String>,
    val tagIds: List<Long>,
)

/** A project. An archived one ([active] false) stays only while a cached entry or favorite uses it. */
@Entity(tableName = "projects")
data class CachedProject(
    @PrimaryKey val id: Long,
    val workspaceId: Long,
    val clientId: Long?,
    val name: String,
    val color: String?,
    val active: Boolean,
    val billable: Boolean,
)

@Entity(tableName = "clients")
data class CachedClient(
    @PrimaryKey val id: Long,
    val workspaceId: Long,
    val name: String,
    val archived: Boolean,
)

@Entity(tableName = "tags")
data class CachedTag(
    @PrimaryKey val id: Long,
    val workspaceId: Long,
    val name: String,
)

/** A task. A done one ([active] false) stays only while a cached entry or favorite uses it. */
@Entity(tableName = "tasks")
data class CachedTask(
    @PrimaryKey val id: Long,
    val workspaceId: Long,
    val projectId: Long?,
    val name: String,
    val active: Boolean,
)

/** One of Tokkl's favorites, at [position] in the user's list. Favorites have no server id. */
@Entity(tableName = "favorites")
data class CachedFavorite(
    @PrimaryKey val position: Int,
    val description: String,
    val projectId: Long?,
    val taskId: Long?,
    val tags: List<String>,
    val billable: Boolean,
)
