package com.github.e0ipso.tokkl

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SignInModel
import com.github.e0ipso.tokkl.signin.SignInScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's screens. */
enum class Destination { SIGN_IN, HOME, LICENCES }

/**
 * The app's navigation host: a back stack of [Destination]s that survives
 * configuration changes and process death. It starts at sign-in until a
 * server and its token are stored, and at the home screen after that.
 * System back pops the stack and closes the app from its first screen.
 */
@Composable
fun TokklNavHost(signInModel: SignInModel) {
    val appContext = LocalContext.current.applicationContext
    val backStack = rememberSaveable(saver = BackStackSaver) { SnapshotStateList<Destination>() }
    if (backStack.isEmpty()) {
        // Shows the window background, which matches the theme, while the
        // stores are read off the main thread.
        LaunchedEffect(Unit) {
            val signedIn = withContext(Dispatchers.IO) { Session(appContext).isSignedIn() }
            backStack.add(if (signedIn) Destination.HOME else Destination.SIGN_IN)
        }
        return
    }
    BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
    when (backStack.last()) {
        Destination.SIGN_IN -> SignInScreen(
            model = signInModel,
            onSignedIn = {
                signInModel.reset()
                backStack.clear()
                backStack.add(Destination.HOME)
            },
        )
        Destination.HOME -> LaunchScreen(onOpenLicences = { backStack.add(Destination.LICENCES) })
        Destination.LICENCES -> LicencesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Destination>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map(Destination::valueOf).toMutableStateList() },
)
