package com.github.e0ipso.tokkl.signin

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.navigation.routes
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Sign-in, plain or again to the account the route names. A sign-in opens Timer. */
internal object SignInEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        routes<Route.SignIn> { route ->
            val model = koinViewModel<SignInViewModel>(parameters = { parametersOf(route.again) })
            SignInScreen(
                model = model,
                onSignedIn = {
                    model.reset()
                    host.navigator.open(TopLevelPage.TIMER)
                },
            )
        }
    }
}
