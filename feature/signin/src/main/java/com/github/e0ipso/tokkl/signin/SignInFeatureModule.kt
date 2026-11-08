package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountRefresh
import com.github.e0ipso.tokkl.session.ServerTrustStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Sign-in: the cooldowns after failed sign-ins, what a sign-in stores, the view model, which takes
 * the account to sign in again to, and the screen.
 */
val signInFeatureModule = module {
    single { SignInCooldownStore(androidContext()) } bind SignInCooldowns::class

    single {
        AccountSessionWriter(
            accountSettings = get(),
            timerStates = get(),
            lifetime = get(),
            session = get(),
            signedIn = getAll(),
            sender = get(),
            timerSync = get(),
        )
    } bind SessionWriter::class

    viewModel { params ->
        val refresh = get<AccountRefresh>()
        SignInViewModel(
            // Runs on the IO thread, since the stores touch the disk.
            signIn = SignIn(
                session = get<SessionWriter>(),
                httpClient = get<SharedHttpClient>().instance,
                configured = get(),
                io = io(),
                signedInAccount = StoredSignInAccount(get(), get())::current,
            ),
            trust = get<ServerTrustStore>(),
            cooldowns = get<SignInCooldownStore>(),
            scope = viewModelCoroutineScope(),
            io = io(),
            clock = get(),
            // A sign-in loads the account settings and the cache, as coming to the foreground does.
            onSignedIn = { refresh.refresh() },
        ).also { model -> params.getOrNull<SignInAgain>()?.let(model::signInAgain) }
    }

    single { SignInEntries } bind FeatureEntries::class

    // Whether somebody is signed in, and sign-in's routes, for the navigation host and the sync status.
    single { AccountSignInRoutes(session = get(), servers = get(), settings = get(), io = io()) } bind
        SignInRoutes::class
}
