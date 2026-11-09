package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.platform.AppHook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Refreshes that components of the refresh itself ask for: the change sender once it has sent
 * changes, the timer state when the running entry ended, and the trust prompt once the user
 * accepted. They cannot take [AccountRefresh], which runs them, so they ask here, and
 * [forward] hands each request to it in order. The app forwards for the whole process from its
 * start, so no request waits for a screen.
 */
class AccountRefreshRequests {

    private enum class Request { REFRESH, AFTER_CHANGE }

    private val requests = Channel<Request>(Channel.UNLIMITED)

    /** Asks for [AccountRefresh.refresh]. */
    fun refresh() {
        requests.trySend(Request.REFRESH)
    }

    /** Asks for [AccountRefresh.refreshAfterChange]. */
    fun refreshAfterChange() {
        requests.trySend(Request.AFTER_CHANGE)
    }

    /**
     * Hands every request to [refresh] until the calling coroutine is cancelled. [refresh] is
     * read on the first request, so nothing loads before one comes.
     */
    suspend fun forward(refresh: Lazy<AccountRefresh>) {
        for (request in requests) {
            when (request) {
                Request.REFRESH -> refresh.value.refresh()
                Request.AFTER_CHANGE -> refresh.value.refreshAfterChange()
            }
        }
    }
}

/** First of the hooks at process start: forwards the refreshes the other components ask for, from any process. */
class RefreshRequestsHook(
    private val requests: AccountRefreshRequests,
    private val refresh: Lazy<AccountRefresh>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AppHook {
    override val order = 10

    override fun processStart() {
        scope.launch(io) { requests.forward(refresh) }
    }
}
