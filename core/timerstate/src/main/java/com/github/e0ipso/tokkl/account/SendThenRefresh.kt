package com.github.e0ipso.tokkl.account

import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.ForegroundReads

/**
 * Tries the queued changes, then refreshes the account. The send does not wait for the refresh,
 * so an expired token still asks for sign-in when `/me` fails on opening the app with a saved
 * queue.
 */
class SendThenRefresh(private val sender: ChangeSender, private val refresh: AccountRefresh) : ForegroundReads {

    override fun refresh() {
        sender.send()
        refresh.refresh()
    }

    override fun refreshOnReconnect() {
        sender.send()
        refresh.refreshOnReconnect()
    }
}
