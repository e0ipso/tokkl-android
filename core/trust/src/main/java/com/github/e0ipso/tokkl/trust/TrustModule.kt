package com.github.e0ipso.tokkl.trust

import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.ServerTrustStore
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The trust refusals of the signed-in session's requests, which the session reports and the
 * navigation host prompts about. Accepting one refreshes the account settings, the request that
 * ran into it.
 */
val trustModule = module {
    single {
        SessionTrust(trust = get<ServerTrustStore>(), refreshes = get(), scope = get(), io = io())
    } binds arrayOf(TrustListener::class, AccountDataOwner::class)
}
