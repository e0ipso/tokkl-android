package com.github.e0ipso.tokkl.session

/**
 * Whether an account is signed in, read from the stored session. [Session] answers from the disk
 * and the Keystore; the unit tests of what reads it answer in memory.
 */
fun interface SignInStatus {
    suspend fun isSignedIn(): Boolean
}
