package com.github.e0ipso.tokkl.session

/** Hears that an account has just signed in, once its session is saved. The app binds every one it has. */
fun interface SignedInListener {
    fun signedIn()
}
