package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The workspace whose projects, clients and tags the pages manage: the account's default
 * workspace, [stored] with the account settings, or from `GET /api/v9/me` when the stored
 * settings predate it. Blocks on the network in that case, so call it off the main thread.
 */
fun managedWorkspace(api: TogglApiClient, stored: Long?): ApiResult<Long> {
    if (stored != null) return ApiResult.Success(stored)
    return when (val me = api.get(api.server.api("me"), MeWorkspace.serializer())) {
        is ApiResult.Success -> me.value.defaultWorkspaceId?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("/me has no default_workspace_id")))
        is ApiResult.Failure -> me
    }
}

@Serializable
private class MeWorkspace(@SerialName("default_workspace_id") val defaultWorkspaceId: Long? = null)
