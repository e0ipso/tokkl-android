package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.platform.viewModelCoroutineScope
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import com.github.e0ipso.tokkl.session.AccountTransition
import com.github.e0ipso.tokkl.session.ServerTrustStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val signInFeatureModule = module {
    single { SignInCooldownStore(androidContext(), io()) } bind SignInCooldowns::class

    viewModel { params ->
        SignInViewModel(
            // Runs on the IO thread, since the stores touch the disk.
            signIn = ServerSignIn(
                storeSignIn = get<AccountTransition>()::signIn,
                connector = get<SharedHttpClient>(),
                configured = get(),
                scope = get(),
                io = io(),
                signedInAccount = StoredSignInAccount(get(), get()),
            ),
            trust = get<ServerTrustStore>(),
            cooldowns = get<SignInCooldownStore>(),
            scope = viewModelCoroutineScope(),
            io = io(),
            clock = get(),
            refreshes = get(),
        ).also { model -> params.getOrNull<SignInAgain>()?.let(model::signInAgain) }
    }

    single { SignInEntries } bind FeatureRoutes::class

    // Whether somebody is signed in, and sign-in's routes, for the navigation host and the sync status.
    single {
        AccountSignInRoutes(session = get(), servers = get(), settings = get<AccountSettingsSource>().state, io = io())
    } bind
        SignInRoutes::class
}
