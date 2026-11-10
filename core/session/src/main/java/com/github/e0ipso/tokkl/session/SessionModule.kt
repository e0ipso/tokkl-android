package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.StoredServer
import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.storage.StartupLoad
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The stored server, token and trust, the one HTTP client, the signed-in [Session], the account's
 * settings and capabilities with their refresh, and sign-out. What each refresh does after its
 * `GET /api/v9/me` answered, the [AccountRefreshSteps], comes from the app, which knows every
 * module that refreshes.
 */
val sessionModule = module {
    single { ServerConfigStore(androidContext(), io()) } bind StoredServer::class
    single { ApiTokenStore(androidContext(), io()) }
    single { ServerTrustStore(androidContext(), io()) } binds arrayOf(ServerTrust::class, AccountDataOwner::class)

    singleOf(::ConfiguredServer)
    singleOf(::SharedHttpClient)
    singleOf(::CallsSignOut) bind AccountDataOwner::class

    single {
        Session(
            servers = get<ServerConfigStore>(),
            tokens = get<ApiTokenStore>(),
            http = get(),
            io = io(),
            trustListener = get<TrustListener>(),
            successListener = get<SuccessListener>(),
        )
    } binds arrayOf(ApiClientProvider::class, AccountDataOwner::class)

    single { AccountSettingsStore(androidContext(), io()) } bind AccountDataOwner::class
    single { CapabilitiesStore(androidContext(), io()) }
    single { AccountSettingsSource(get<AccountSettingsStore>(), get(), io()) } bind StartupLoad::class

    single { CapabilitiesSource(get<CapabilitiesStore>(), get(), io()) } bind StartupLoad::class

    singleOf(::AccountRefreshRequests)
    single { RefreshRequestsHook(get(), inject(), get(), io()) } bind AppHook::class

    single {
        val session = get<Session>()
        AccountRefresh(
            reads = { session()?.let(::ServerAccountReads) },
            settings = get(),
            scope = get(),
            io = io(),
            steps = get(),
        )
    } binds arrayOf(AccountDataOwner::class, CacheRefresh::class)

    single { ServerChanges(scope = get(), io = io(), refreshes = get()) }

    singleOf(::CurrentAccount)

    single { SignOut(owners = getAll(), account = get(), io = io()) }
}
