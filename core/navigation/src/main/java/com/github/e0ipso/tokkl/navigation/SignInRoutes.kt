package com.github.e0ipso.tokkl.navigation

/**
 * Whether somebody is signed in, and the sign-in screen's routes, which the sign-in feature binds
 * in Koin. The host starts at sign-in while nobody is signed in and goes there after a sign-out,
 * and the sync status opens it again for the account after the server refused its token.
 */
interface SignInRoutes {

    /** Whether a server and its token are stored. Reads the stores off the main thread. */
    suspend fun isSignedIn(): Boolean

    /** Sign-in, with nothing filled in. */
    fun signIn(): Route

    /** Whether [route] is a sign-in screen, which shows without the bar. */
    fun isSignIn(route: Route): Boolean

    /**
     * Sign-in filled in for the account signed in now, so the user signs in again to the same
     * account, or null without a stored server. Reads the stores off the main thread.
     */
    suspend fun again(): Route?
}
