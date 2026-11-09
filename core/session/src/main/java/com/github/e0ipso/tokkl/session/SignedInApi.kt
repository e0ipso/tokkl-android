package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TokklApi

/**
 * [call] with the signed-in account's API, as the repositories over the server use it, or
 * [ApiError.AuthFailed] while signed out, before any request.
 */
suspend inline fun <R> ApiClientProvider.signedIn(call: (TokklApi) -> ApiResult<R>): ApiResult<R> {
    val api = this() ?: return ApiResult.Failure(ApiError.AuthFailed)
    return call(api)
}
