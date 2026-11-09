package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
