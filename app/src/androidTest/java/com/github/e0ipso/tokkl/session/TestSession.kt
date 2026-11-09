package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.api.SuccessListener
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/**
 * The app's [Session], with the trust and last-answer listeners the app gives it, for tests that
 * sign in, sign out and check the session from the test thread: each call waits for the
 * session's own, which reads and writes the stores on the IO dispatcher.
 */
class TestSession(private val session: Session, private val koin: Koin) {
    fun save(server: ServerUrl, token: String) = runBlocking { session.save(server, token) }

    fun clear() = runBlocking { session.clear() }

    fun isSignedIn(): Boolean = runBlocking { session.isSignedIn() }

    /** The app's API client provider, which the session is. */
    val api: ApiClientProvider get() = session

    /**
     * A transport like the one under the signed-in account's API, or null when signed out, for
     * tests that send a request the app does not send itself: the stored server and token, through
     * the app's shared HTTP client with the listeners the session gives its own.
     */
    fun client(): TogglApiClient? = runBlocking {
        val server = koin.get<ServerConfigStore>().load() ?: return@runBlocking null
        val token = koin.get<ApiTokenStore>().load() ?: return@runBlocking null
        TogglApiClient(
            server,
            ApiCredentials.ApiToken(token),
            koin.get<SharedHttpClient>().instance,
            trustListener = koin.get<TrustListener>(),
            successListener = koin.get<SuccessListener>(),
            io = Dispatchers.IO,
        )
    }
}

/**
 * The app's session, named like the constructor it replaced, so the tests keep their
 * `Session(context)` call sites. [context] is unused: the app's Koin graph holds the one Session.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun Session(context: Context): TestSession = GlobalContext.get().let { TestSession(it.get(), it) }
