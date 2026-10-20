package com.github.e0ipso.tokkl.catalog

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult

/**
 * Sends a create of a project, client, tag or task, and finds what it made when its answer was
 * lost.
 *
 * OkHttp sends a request again when a reused connection fails after the request went out. A
 * create whose answer was lost then reaches the server twice, and Tokkl refuses the second copy
 * as a duplicate name, although the item exists. So [create] must send with `resend = false`,
 * which turns a lost answer into [ApiError.Network]. After that failure, [list] reads the items
 * again, and the one [made] accepts counts as the one the create stored. When the read finds
 * none, the server does not hold the item, as when a stale pooled connection failed before the
 * server read the request, so the create goes once more, and only once. When the read fails too, the create's
 * own failure stands.
 *
 * Blocks, so call it off the main thread.
 */
fun <T> createOnce(create: () -> ApiResult<T>, list: () -> ApiResult<List<T>>, made: (T) -> Boolean): ApiResult<T> =
    send(create, list, made, tries = 2)

private tailrec fun <T> send(create: () -> ApiResult<T>, list: () -> ApiResult<List<T>>, made: (T) -> Boolean, tries: Int): ApiResult<T> {
    val answer = create()
    if (answer !is ApiResult.Failure || answer.error !is ApiError.Network) return answer
    val items = (list() as? ApiResult.Success)?.value ?: return answer
    val found = items.firstOrNull(made)
    return when {
        found != null -> ApiResult.Success(found)
        tries > 1 -> send(create, list, made, tries - 1)
        else -> answer
    }
}
