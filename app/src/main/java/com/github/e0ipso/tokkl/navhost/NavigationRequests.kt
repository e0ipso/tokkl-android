package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.TopLevelPage
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationTap
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.widget.WidgetPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** A page that a tap outside the app asks MainActivity to open. */
sealed interface NavigationRequest {
    /** A home screen widget's tap. */
    data class Widget(val page: WidgetPage) : NavigationRequest

    /** A Pomodoro session notification's tap. */
    data class Pomodoro(val tap: PomodoroNotificationTap) : NavigationRequest

    /** A reminder notification's tap, which opens Timer. */
    data object ReminderTimer : NavigationRequest
}

/**
 * The pages that taps outside the app asked to open, until the navigation host opens them.
 * MainActivity fills it from its intents and its saved state, and saves what still waits. One
 * request of each kind waits at a time: a later tap of the same kind replaces the earlier one.
 */
class NavigationRequests {
    private val waiting = mutableStateListOf<NavigationRequest>()

    val pending: List<NavigationRequest> get() = waiting

    fun request(request: NavigationRequest) {
        waiting.removeAll { it::class == request::class }
        waiting += request
    }

    /** [request] was opened, or turned down. A request that replaced it since stays, unless it is equal. */
    fun handled(request: NavigationRequest) {
        waiting.remove(request)
    }

    fun clear() = waiting.clear()
}

/**
 * Opens what [requests] ask for, each as soon as it can. A widget's page opens when somebody is
 * signed in, and sign-in opens otherwise. A Pomodoro notification's tap opens Pomodoro when the
 * notification still belongs to the signed-in account and its phase. A reminder's tap opens
 * Timer, but waits while sign-in shows ([onSignIn]). A page in [unavailable] opens Timer.
 */
@Composable
internal fun NavigationRequestEffects(
    requests: NavigationRequests,
    navigator: Navigator,
    unavailable: Set<TopLevelPage>,
    onSignIn: Boolean,
) {
    val session: Session = koinInject()
    val io: CoroutineDispatcher = koinInject(IoDispatcher)
    val pomodoroNotification: AccountPomodoroNotification = koinInject()
    val currentUnavailable by rememberUpdatedState(unavailable)
    for (request in requests.pending) {
        key(request) {
            when (request) {
                is NavigationRequest.Widget -> {
                    val reports = if (request.page == WidgetPage.TODAY) sessionViewModel<ReportsViewModel>() else null
                    LaunchedEffect(request) {
                        val signedIn = withContext(io) { session.isSignedIn() }
                        if (!signedIn) {
                            navigator.signIn()
                        } else {
                            when (request.page) {
                                WidgetPage.TIMER -> navigator.open(TopLevelPage.TIMER, currentUnavailable)

                                WidgetPage.TODAY -> {
                                    reports?.openToday()
                                    navigator.open(TopLevelPage.REPORTS, currentUnavailable)
                                }

                                WidgetPage.POMODORO -> navigator.open(TopLevelPage.POMODORO, currentUnavailable)
                            }
                        }
                        requests.handled(request)
                    }
                }

                is NavigationRequest.Pomodoro -> LaunchedEffect(request) {
                    val accepted = withContext(io) {
                        pomodoroNotification.current.acceptsTap(request.tap.account, request.tap.phase)
                    }
                    if (accepted) navigator.open(TopLevelPage.POMODORO, currentUnavailable)
                    requests.handled(request)
                }

                NavigationRequest.ReminderTimer -> LaunchedEffect(request, onSignIn) {
                    if (onSignIn) return@LaunchedEffect
                    navigator.open(TopLevelPage.TIMER)
                    requests.handled(request)
                }
            }
        }
    }
}
