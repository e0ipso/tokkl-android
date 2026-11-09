package com.github.e0ipso.tokkl.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.pomodoro.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.LinkIntent
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.NavigationRequest
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.navigation.RequestOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The Pomodoro page, and the taps that open it: the Pomodoro widget's, and a session
 * notification's while that notification still belongs to the signed-in account and its phase,
 * which [notification] says on [io]. The notification is made on the first tap, so listing the
 * features makes nothing. The page's view model is the signed-in session's, and its
 * starts and retries may ask for the notification permission through the frame's prompt.
 */
internal class PomodoroEntries(
    private val notification: Lazy<PomodoroSessionNotificationSync>,
    private val io: CoroutineContext,
) : FeatureEntries {
    override val pages get() = listOf(pomodoroPage)

    override fun PolymorphicModuleBuilder<Route>.registerRoutes() {
        subclass(PomodoroRoute::class)
    }

    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val prompts = host.prompts
        route(PomodoroRoute) {
            PomodoroScreen(
                model = koinViewModel<PomodoroViewModel>(viewModelStoreOwner = LocalSignedInSession.current),
                onNotificationStart = { prompts.starts.trySend(Unit) },
                onNotificationRetry = { prompts.retries.trySend(Unit) },
                notificationPermissionRevision = prompts.answers,
            )
        }
    }

    override val linkExtras
        get() = listOf(POMODORO_WIDGET_TAP, POMODORO_NOTIFICATION_ACCOUNT, EXTRA_PHASE_START, EXTRA_REVISION)

    override fun readLinks(intent: LinkIntent): List<NavigationRequest> = listOfNotNull(
        sessionTap(intent),
        PomodoroWidgetTap.takeIf { intent.flag(POMODORO_WIDGET_TAP) },
    )

    /** A session notification's tap, when it names both its account and its phase. */
    private fun sessionTap(intent: LinkIntent): PomodoroSessionTap? {
        if (intent.action != SHOW_POMODORO) return null
        val account = intent.text(POMODORO_NOTIFICATION_ACCOUNT) ?: return null
        val phaseStart = intent.number(EXTRA_PHASE_START) ?: return null
        return PomodoroSessionTap(account, phaseStart)
    }

    override fun PolymorphicModuleBuilder<NavigationRequest>.registerRequests() {
        subclass(PomodoroSessionTap::class)
        subclass(PomodoroWidgetTap::class)
    }

    /**
     * The widget's tap opens Pomodoro when somebody is signed in, and sign-in otherwise. A
     * session notification's tap opens Pomodoro when the notification still belongs to the
     * signed-in account and its phase. Without the timer API, either opens Timer.
     */
    @Composable
    override fun OpenRequest(request: NavigationRequest, opening: RequestOpening) {
        when (request) {
            PomodoroWidgetTap -> LaunchedEffect(request) {
                opening.openSignedIn(PomodoroRoute)
                opening.handled(request)
            }

            is PomodoroSessionTap -> LaunchedEffect(request) {
                val tap = request.tap
                if (withContext(io) { notification.value.acceptsTap(tap.account, tap.phase) }) {
                    opening.navigator.open(PomodoroRoute, opening.unavailable)
                }
                opening.handled(request)
            }
        }
    }
}

/**
 * The extra of the Pomodoro widget's tap, which the widget puts in its intent
 * (`WidgetNavigation.OPEN_POMODORO` in the widget feature). The app's DeepLinksTest checks that
 * the two agree.
 */
internal const val POMODORO_WIDGET_TAP = "open_pomodoro_widget"

/** The Pomodoro widget's tap. */
@Serializable
data object PomodoroWidgetTap : NavigationRequest

/**
 * A Pomodoro session notification's tap, for [account]'s phase that started at [phaseStart], in
 * epoch milliseconds.
 */
@Serializable
data class PomodoroSessionTap(val account: String, val phaseStart: Long) : NavigationRequest {
    val tap: PomodoroNotificationTap
        get() = PomodoroNotificationTap(account, PomodoroPhaseKey(Instant.ofEpochMilli(phaseStart)))
}

/** The Pomodoro page, in the bar after Timer, while the server has the timer API, since it owns the session. */
val pomodoroPage = TopLevelDestination(
    route = PomodoroRoute,
    label = UiR.string.pomodoro_title,
    icon = R.drawable.ic_nav_pomodoro,
    hue = SectionHues.timer,
    position = 10,
    isAvailable = { it.timerState },
)

/** The Pomodoro page. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Pomodoro")
data object PomodoroRoute : Route {
    override val showsTimerNotices: Boolean get() = true
}

/** The Pomodoro page's running dot, while a session runs. */
class PomodoroBadge(timerStates: AccountTimerStates) :
    PageBadge(PomodoroRoute, R.string.nav_pomodoro_running, timerStates.held.map { it?.state?.pomodoro != null })
