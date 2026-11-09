package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.TokklApi

/**
 * The server reads behind an account refresh, for the account signed in when the refresh opened
 * them: `GET /api/v9/me` with its related data and `GET /api/v9/me/preferences`. [AccountRefresh]
 * orders them; [ServerAccountReads] sends them, and the refresh's unit tests answer for it.
 */
interface AccountReads {
    suspend fun meWithRelatedData(): ApiResult<Me>

    suspend fun preferences(): ApiResult<MePreferences>
}

/**
 * [AccountReads] over the signed-in account's [api], which the steps after each refresh read the
 * rest of the account with, from the same client.
 */
class ServerAccountReads(val api: TokklApi) : AccountReads {
    override suspend fun meWithRelatedData(): ApiResult<Me> = api.account.meWithRelatedData()

    override suspend fun preferences(): ApiResult<MePreferences> = api.account.preferences()
}
