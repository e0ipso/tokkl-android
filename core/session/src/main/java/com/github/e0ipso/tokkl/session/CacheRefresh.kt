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
