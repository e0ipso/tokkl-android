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
import com.github.e0ipso.tokkl.capabilities.TimerApiOffer
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.storage.StartupLoad
import org.koin.android.ext.koin.androidContext
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

    // Every process sends requests only to the stored server, and to a plain http:// server only
    // once the user has confirmed it, through the one HTTP client and its connection pool.
    single { ConfiguredServer(get()) }
    single { SharedHttpClient(server = get(), trust = get<ServerTrust>()) }
    // Sign-out cancels every call in flight.
    single { CallsSignOut(get()) } bind AccountDataOwner::class

    // Reports trust refusals to the session's trust prompt, and answers to the last answer's time.
    single {
        Session(
            servers = get<ServerConfigStore>(),
            tokens = get<ApiTokenStore>(),
            http = get(),
            io = io(),
            trustListener = get<TrustListener>(),
            successListener = get<SuccessListener>(),
        )
    } binds arrayOf(ApiClientProvider::class, SignInStatus::class, AccountDataOwner::class)

    single { AccountSettingsStore(androidContext(), io()) } bind AccountDataOwner::class
    single { CapabilitiesStore(androidContext(), io()) }
    // The account settings. They load at process start from a file of a few short values.
    single { AccountSettingsSource(get<AccountSettingsStore>(), get()) } bind StartupLoad::class

    // What the server offers. It loads at process start from a file of a few flags.
    single { CapabilitiesSource(get<CapabilitiesStore>(), get()) } binds
        arrayOf(StartupLoad::class, TimerApiOffer::class)

    single { AccountRefreshRequests() }
    single { RefreshRequestsHook(get(), inject(), get(), io()) } bind AppHook::class

    single {
        AccountRefresh(
            client = get<Session>(),
            settings = get(),
            scope = get(),
            io = io(),
            steps = get(),
        )
    } binds arrayOf(AccountDataOwner::class, CacheRefresh::class)

    // The changes the pages send at once, which outlive the page with the cache refresh after them.
    single { ServerChanges(scope = get(), io = io(), refreshes = get()) }

    // Where each account begins and ends, which the timer state and the navigation follow.
    single { CurrentAccount(app = get()) }

    // Sign-out (docs/PRD.md S6): every [AccountDataOwner], phase by phase in [SignOutPhase] order.
    single { SignOut(owners = getAll(), account = get(), io = io()) }
}
