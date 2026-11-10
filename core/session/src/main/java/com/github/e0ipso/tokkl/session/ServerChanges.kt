package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.coroutines.CoroutineContext

/**
 * The changes a page sends to the server at once, outside the queue, such as a new client or a
 * restored entry. Each one runs in the application's [scope], so once sent it reaches the server,
 * and the cache refresh that follows an accepted change still starts, also when the page that sent
 * it goes meanwhile. The page awaits the answer and shows it while it is still there.
 */
class ServerChanges(
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val refreshes: CacheRefresh,
) {

    /**
     * Sends [request] on [io] in the application's scope. Once the server accepts it, and when
     * [refresh] is true, the cache refreshes so it ends up with the change. The answer comes back
     * through the returned [Deferred]; a caller that stops waiting cancels neither.
     */
    fun <R> send(refresh: Boolean = true, request: suspend () -> ApiResult<R>): Deferred<ApiResult<R>> =
        scope.async(io) {
            request().also { answer ->
                if (refresh && answer is ApiResult.Success) refreshes.refreshAfterChange()
            }
        }
}
