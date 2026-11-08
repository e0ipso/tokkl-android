package com.github.e0ipso.tokkl.session

/** Whether an account is signed in now, such as [Session.isSignedIn]. */
fun interface SignedInCheck {
    fun isSignedIn(): Boolean
}
