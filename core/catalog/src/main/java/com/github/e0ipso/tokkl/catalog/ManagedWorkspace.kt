package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.flatMap
import com.github.e0ipso.tokkl.model.WorkspaceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The workspace whose projects, clients and tags the pages manage: the account's default
 * workspace, [stored] with the account settings, or from `GET /api/v9/me` when the stored
 * settings predate it.
 */
suspend fun managedWorkspace(api: TokklApi, stored: WorkspaceId?): ApiResult<WorkspaceId> {
    if (stored != null) return ApiResult.Success(stored)
    return api.account.me(MeWorkspace.serializer()).flatMap { me ->
        me.defaultWorkspaceId?.let { ApiResult.Success(WorkspaceId(it)) }
            ?: ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("/me has no default_workspace_id")))
    }
}

@Serializable
private class MeWorkspace(@SerialName("default_workspace_id") val defaultWorkspaceId: Long? = null)
