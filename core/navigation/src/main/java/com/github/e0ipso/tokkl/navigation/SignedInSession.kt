package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStoreOwner

/**
 * The signed-in session's view model store, which the navigation host provides. The view models
 * that several screens share live there, and a sign-out drops them with the session.
 */
val LocalSignedInSession = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("No signed-in session store")
}
