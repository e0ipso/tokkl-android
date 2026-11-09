package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.Job

/**
 * The account refreshes that a page or a step asks for. [AccountRefresh] reads the server; the
 * unit tests of what asks count the refreshes without one.
 */
interface CacheRefresh {

    /** Reads the account's data from the server again. */
    fun refresh(): Job

    /** Reads it again after a change the server has just accepted, so the cache ends up with that change. */
    fun refreshAfterChange(): Job
}

/**
 * What the foreground refresh reads, as [ForegroundRefresh] asks for it: on a trip back to the
 * foreground, and when the network comes back. core:cache sends the queue first.
 */
interface ForegroundReads {
    fun refresh()

    fun refreshOnReconnect()
}
