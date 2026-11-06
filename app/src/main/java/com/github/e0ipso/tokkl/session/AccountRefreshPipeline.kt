package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates

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

/**
 * The steps of each account refresh, in order. The capabilities refresh first, from the same
 * `/me`, and feed the account's timer state what their probe read. The cache refreshes next with
 * those capabilities. Then [listener] runs with the preferences and the capabilities: the
 * features that keep a preference, the change sender, since the server has just answered, and the
 * timer sync, since any refresh can learn or lose the timer API, in that order
 * ([AccountRefreshListener.inOrder]).
 */
class AccountRefreshPipeline(
    private val capabilities: CapabilitiesSource,
    private val timerStates: AccountTimerStates,
    private val cache: AccountCache,
    private val listener: AccountRefreshListener,
) : AccountRefreshSteps {

    override fun afterAnswer(api: TogglApiClient, me: Me, preferences: MePreferences?) {
        val flags = capabilities.refresh(api, me, timerStates.source)
        cache.refresh(api, me, preferences, flags, onAllDayMissing = capabilities::allDayMissing)
        listener.refreshed(AccountRefreshed(preferences, flags))
    }
}
