package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.api.ApiClientProvider
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TogglApiClient
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * The app's [Session], with the trust and last-answer listeners the app gives it, for tests that
 * sign in, sign out and check the session from the test thread: each call waits for the
 * session's own, which reads and writes the stores on the IO dispatcher.
 */
class TestSession(private val session: Session) {
    fun save(server: ServerUrl, token: String) = runBlocking { session.save(server, token) }

    fun clear() = runBlocking { session.clear() }

    fun isSignedIn(): Boolean = runBlocking { session.isSignedIn() }

    /** The app's API client provider, which the session is. */
    val api: ApiClientProvider get() = session

    /**
     * The transport under the signed-in account's API, or null when signed out, for tests that send a
     * request the app does not send itself.
     */
    fun client(): TogglApiClient? = runBlocking { session.api() }?.client
}

/**
 * The app's session, named like the constructor it replaced, so the tests keep their
 * `Session(context)` call sites. [context] is unused: the app's Koin graph holds the one Session.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun Session(context: Context): TestSession = TestSession(GlobalContext.get().get())
