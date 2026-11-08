package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.capabilities.Capabilities

/** A refresh's `GET /api/v9/me/preferences`, null when it failed, and the capabilities it learned. */
class AccountRefreshed(val preferences: MePreferences?, val capabilities: Capabilities)

/** Takes each account refresh's answers, on the refresh's thread and inside its job. */
fun interface AccountRefreshListener {
    fun refreshed(refresh: AccountRefreshed)

    companion object {
        /** One listener that runs [listeners] one after another, in list order. */
        fun inOrder(listeners: List<AccountRefreshListener>) = AccountRefreshListener { refresh ->
            listeners.forEach { it.refreshed(refresh) }
        }
    }
}
