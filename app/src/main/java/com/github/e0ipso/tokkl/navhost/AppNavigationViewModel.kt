package com.github.e0ipso.tokkl.navhost

import androidx.lifecycle.ViewModel
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationTap
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotificationSync
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * What the navigation host reads beside its back stack: every feature's screens and top-level
 * [pages], how many sign-outs have finished, the server's capabilities, which decide the pages it
 * offers, whether somebody is signed in, and whether a Pomodoro notification's tap still belongs to
 * the account and its phase. It is a view model of MainActivity, so a rotation keeps it. The
 * stores are read on [io].
 */
internal class AppNavigationViewModel(
    val features: List<FeatureEntries>,
    val pages: TopLevelPages,
    val signOuts: StateFlow<Int>,
    val capabilities: StateFlow<Capabilities>,
    private val session: Session,
    private val pomodoroNotification: PomodoroSessionNotificationSync,
    private val io: CoroutineContext,
    scope: CoroutineScope,
) : ViewModel(scope) {

    /** Whether a server and its token are stored. */
    suspend fun isSignedIn(): Boolean = withContext(io) { session.isSignedIn() }

    /** Whether [tap]'s notification still belongs to the signed-in account and the phase on the server. */
    suspend fun acceptsPomodoroTap(tap: PomodoroNotificationTap): Boolean = withContext(io) {
        pomodoroNotification.acceptsTap(tap.account, tap.phase)
    }
}
