package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi
import com.github.e0ipso.tokkl.api.flatMap
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.SignedInApi

/**
 * The workspace whose projects, clients and tags the pages manage: the account's default
 * workspace, [stored] with the account settings, or from `GET /api/v9/me` when the stored
 * settings predate it.
 */
suspend fun managedWorkspace(api: TokklApi, stored: WorkspaceId?): ApiResult<WorkspaceId> {
    if (stored != null) return ApiResult.Success(stored)
    return api.account.defaultWorkspace().flatMap { me ->
        me.defaultWorkspaceId?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("/me has no default_workspace_id")))
    }
}

/** The workspace the catalog pages manage, as [managedWorkspace] finds it with the signed-in account's API. */
suspend fun SignedInApi.workspace(stored: WorkspaceId?): ApiResult<WorkspaceId> = call { managedWorkspace(it, stored) }
