package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient

/**
 * The server behind a page that manages one kind of the account's items: which workspace the page
 * manages, and what it lists there. Each feature's repository adds the changes its page sends.
 */
interface CatalogRepository<D> {

    /** The workspace the page manages: [stored] from the account settings, or the server's default. */
    suspend fun workspace(stored: Long?): ApiResult<Long>

    /** What the page lists in [workspace]. */
    suspend fun load(workspace: Long): ApiResult<D>
}

/**
 * The signed-in account's [client], as the repositories over the server use it: every call fails
 * with [ApiError.AuthFailed] while signed out, before any request.
 */
class SignedInApi(private val client: ApiClientProvider) {

    /** [call] with the signed-in account's client, or [ApiError.AuthFailed] when signed out. */
    suspend fun <R> call(call: suspend (TogglApiClient) -> ApiResult<R>): ApiResult<R> {
        val api = client() ?: return ApiResult.Failure(ApiError.AuthFailed)
        return call(api)
    }

    /** The workspace the catalog pages manage, as [managedWorkspace] finds it. */
    suspend fun workspace(stored: Long?): ApiResult<Long> = call { managedWorkspace(it, stored) }
}
