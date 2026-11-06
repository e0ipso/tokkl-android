package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel

/** The signed-in session's view model store, which the navigation host provides. */
val LocalSignedInSession = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("No signed-in session store")
}

/** The signed-in session's [T], made through Koin on first use. */
@Composable
inline fun <reified T : ViewModel> sessionViewModel(): T =
    koinViewModel(viewModelStoreOwner = LocalSignedInSession.current)
