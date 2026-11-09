package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi

/**
 * The signed-in account's API from [client], as the repositories over the server use it: every
 * call fails with [ApiError.AuthFailed] while signed out, before any request.
 */
class SignedInApi(private val client: ApiClientProvider) {

    /** [call] with the signed-in account's API, or [ApiError.AuthFailed] when signed out. */
    suspend fun <R> call(call: suspend (TokklApi) -> ApiResult<R>): ApiResult<R> {
        val api = client() ?: return ApiResult.Failure(ApiError.AuthFailed)
        return call(api)
    }
}
