package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationTap
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.signin.SignInRoute
import com.github.e0ipso.tokkl.widget.WidgetPage
import org.koin.compose.viewmodel.koinViewModel

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
 * What the navigation reads of the stored session: whether somebody [isSignedIn], and whether a
 * Pomodoro notification's tap still [acceptsPomodoroTap]. The app's navigation module builds it
 * from the session and the Pomodoro session notification, and both read off the main thread.
 */
internal class NavigationSession(
    val isSignedIn: suspend () -> Boolean,
    val acceptsPomodoroTap: suspend (PomodoroNotificationTap) -> Boolean,
)

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
    session: NavigationSession,
    unavailable: Set<Route>,
    onSignIn: Boolean,
) {
    val currentUnavailable by rememberUpdatedState(unavailable)
    for (request in requests.pending) {
        key(request) {
            when (request) {
                is NavigationRequest.Widget -> {
                    val reports = if (request.page == WidgetPage.TODAY) {
                        koinViewModel<ReportsViewModel>(viewModelStoreOwner = LocalSignedInSession.current)
                    } else {
                        null
                    }
                    LaunchedEffect(request) {
                        if (!session.isSignedIn()) {
                            navigator.startOver(SignInRoute())
                        } else {
                            when (request.page) {
                                WidgetPage.TIMER -> navigator.open(Route.Timer, currentUnavailable)

                                WidgetPage.TODAY -> {
                                    reports?.openToday()
                                    navigator.open(ReportsRoute, currentUnavailable)
                                }

                                WidgetPage.POMODORO -> navigator.open(PomodoroRoute, currentUnavailable)
                            }
                        }
                        requests.handled(request)
                    }
                }

                is NavigationRequest.Pomodoro -> LaunchedEffect(request) {
                    if (session.acceptsPomodoroTap(request.tap)) {
                        navigator.open(PomodoroRoute, currentUnavailable)
                    }
                    requests.handled(request)
                }

                NavigationRequest.ReminderTimer -> LaunchedEffect(request, onSignIn) {
                    if (onSignIn) return@LaunchedEffect
                    navigator.open(Route.Timer)
                    requests.handled(request)
                }
            }
        }
    }
}
