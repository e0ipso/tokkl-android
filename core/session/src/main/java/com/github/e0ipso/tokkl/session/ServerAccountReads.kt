package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.Me
import com.github.e0ipso.tokkl.api.MePreferences
import com.github.e0ipso.tokkl.api.TokklApi

/**
 * The server reads behind an account refresh, for the account signed in when the refresh opened
 * them: `GET /api/v9/me` with its related data and `GET /api/v9/me/preferences`. [AccountRefresh]
 * orders them. The steps after each refresh read the rest of the account with the same [api].
 */
class ServerAccountReads(val api: TokklApi) {
    suspend fun meWithRelatedData(): ApiResult<Me> = api.account.meWithRelatedData()

    suspend fun preferences(): ApiResult<MePreferences> = api.account.preferences()
}
