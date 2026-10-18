package com.github.e0ipso.tokkl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.licences.LicencesScreen
import com.github.e0ipso.tokkl.reports.ReportsModel
import com.github.e0ipso.tokkl.reports.ReportsScreen
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.settings.SettingsScreen
import com.github.e0ipso.tokkl.settings.ThemeSettingsModel
import com.github.e0ipso.tokkl.signin.SignInModel
import com.github.e0ipso.tokkl.signin.SignInScreen
import com.github.e0ipso.tokkl.trust.SessionTrustModel
import com.github.e0ipso.tokkl.trust.TrustPrompt
import com.github.e0ipso.tokkl.trust.TrustRefusedNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's screens. */
enum class Destination { SIGN_IN, HOME, SETTINGS, LICENCES, REPORTS }

/**
 * The app's navigation host: a back stack of [Destination]s that survives
 * configuration changes and process death. It starts at sign-in until a
 * server and its token are stored, and at the home screen after that.
 * System back pops the stack and closes the app from its first screen.
 *
 * Above any screen of a signed-in session it shows the prompt for a request
 * that [sessionTrust] saw refused, and after a cancel a notice that opens the
 * prompt again. Sign-in shows its own prompts.
 */
@Composable
fun TokklNavHost(
    signInModel: SignInModel,
    themeSettings: ThemeSettingsModel,
    sessionTrust: SessionTrustModel,
    reports: ReportsModel,
) {
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
    val destination = backStack.last()
    Box(Modifier.fillMaxSize()) {
        Screen(destination, backStack, signInModel, themeSettings, reports)
        if (destination != Destination.SIGN_IN) SessionTrustPrompts(sessionTrust, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Screen(
    destination: Destination,
    backStack: SnapshotStateList<Destination>,
    signInModel: SignInModel,
    themeSettings: ThemeSettingsModel,
    reports: ReportsModel,
) {
    when (destination) {
        Destination.SIGN_IN -> SignInScreen(
            model = signInModel,
            onSignedIn = {
                signInModel.reset()
                backStack.clear()
                backStack.add(Destination.HOME)
            },
        )
        Destination.HOME -> LaunchScreen(
            onOpenReports = { backStack.add(Destination.REPORTS) },
            onOpenSettings = { backStack.add(Destination.SETTINGS) },
            onOpenLicences = { backStack.add(Destination.LICENCES) },
        )
        Destination.SETTINGS -> SettingsScreen(
            model = themeSettings,
            onBack = { backStack.removeAt(backStack.lastIndex) },
            onOpenLicences = { backStack.add(Destination.LICENCES) },
        )
        Destination.LICENCES -> LicencesScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
        Destination.REPORTS -> ReportsScreen(model = reports, onBack = { backStack.removeAt(backStack.lastIndex) })
    }
}

/** The prompt for a refused request, or after a cancel the notice that reopens it. [modifier] places the notice. */
@Composable
private fun SessionTrustPrompts(sessionTrust: SessionTrustModel, modifier: Modifier = Modifier) {
    val state by sessionTrust.state.collectAsState()
    val refusal = state.refusal ?: return
    if (state.promptOpen && refusal is ApiError.TrustQuestion) {
        TrustPrompt(refusal, onAccept = sessionTrust::accept, onCancel = sessionTrust::cancel)
    } else {
        TrustRefusedNotice(refusal, onReview = sessionTrust::reopen, modifier = modifier)
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Destination>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map(Destination::valueOf).toMutableStateList() },
)
