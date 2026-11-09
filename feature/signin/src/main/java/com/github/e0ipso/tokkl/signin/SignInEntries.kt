package com.github.e0ipso.tokkl.signin

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.routes
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.CoroutineContext

/** Sign-in, plain or again to the account the route names. A sign-in opens Timer. */
internal object SignInEntries : FeatureRoutes {
    /** Sign-in is no page of the bar. */
    override val pages get() = emptyList<TopLevelDestination>()

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(SignInRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        routes<SignInRoute> { route ->
            val model = koinViewModel<SignInViewModel>(parameters = { parametersOf(route.again) })
            SignInScreen(
                model = model,
                onSignedIn = {
                    model.reset()
                    host.navigator.open(Route.Timer)
                },
            )
        }
    }
}

/**
 * Sign-in. With [server], signing in again to that account after the server refused its token:
 * the form starts filled in for it, and the sign-in checks it is the same user.
 */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.SignIn")
data class SignInRoute(val server: String? = null, val userId: Long? = null) : Route {
    /** The account to sign in again to, or null for a plain sign-in. */
    val again: SignInAgain? get() = server?.let(ServerUrl::parse)?.let { SignInAgain(it, userId) }

    companion object {
        fun again(account: SignInAgain) = SignInRoute(account.server.toString(), account.userId)
    }
}

/**
 * The signed-in account, when the user signs in again because the server refused its API token
 * (docs/PRD.md O6). The phone keeps that account's cache and queued changes, so only a sign-in to
 * the same [server] and, when the phone knows it, the same [userId] may replace the token.
 */
data class SignInAgain(val server: ServerUrl, val userId: Long?)

/**
 * Whether the [session] has somebody signed in, and the sign-in screen's routes. Signing in again
 * fills in the stored [servers]' server and the account [settings]' user, read on [io].
 */
internal class AccountSignInRoutes(
    private val session: Session,
    private val servers: ServerConfigStore,
    private val settings: StateFlow<AccountSettings?>,
    private val io: CoroutineContext,
) : SignInRoutes {

    override suspend fun isSignedIn(): Boolean = session.isSignedIn()

    override fun signIn(): Route = SignInRoute()

    override fun isSignIn(route: Route): Boolean = route is SignInRoute

    override suspend fun again(): Route? = withContext(io) {
        servers.load()?.let { server -> SignInRoute.again(SignInAgain(server, settings.value?.userId)) }
    }
}
