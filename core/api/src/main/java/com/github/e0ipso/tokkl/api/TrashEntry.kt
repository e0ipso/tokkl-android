package com.github.e0ipso.tokkl.api

import com.github.e0ipso.tokkl.model.EntryId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A deleted time entry as `GET /api/app/trash` sends it: a v9 time entry with Tokkl's meta fields.
 *
 * @property duration seconds, negative while the entry runs, as in API v9.
 * @property deletedAt when it went to the trash, `server_deleted_at`.
 */
@Serializable
data class TrashEntry(
    val id: EntryId,
    val description: String? = null,
    val start: String,
    val stop: String? = null,
    val duration: Long = 0,
    @SerialName("project_id") val projectId: Long? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("server_deleted_at") val deletedAt: String? = null,
)
