package com.github.e0ipso.tokkl.account

import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.AccountRefreshSteps
import com.github.e0ipso.tokkl.session.AccountRefreshed
import com.github.e0ipso.tokkl.session.MePreferences
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates

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

    override suspend fun afterAnswer(api: TogglApiClient, me: Me, preferences: MePreferences?) {
        val flags = capabilities.refresh(api, me, timerStates.source)
        cache.refresh(api, me, preferences, flags, onAllDayMissing = capabilities::allDayMissing)
        listener.refreshed(AccountRefreshed(preferences, flags))
    }
}
