package com.github.e0ipso.tokkl.session

/**
 * Asks for the signed-in account to be read from the server again, such as
 * [AccountRefresh.refreshAfterChange]. It returns at once; the read runs in the background.
 */
fun interface RefreshRequest {
    fun request()
}
