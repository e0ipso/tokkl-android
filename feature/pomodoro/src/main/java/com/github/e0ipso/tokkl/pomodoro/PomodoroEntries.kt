package com.github.e0ipso.tokkl.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.pomodoro.R
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.LinkIntent
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.NavigationRequest
import com.github.e0ipso.tokkl.navigation.PageBadge
import com.github.e0ipso.tokkl.navigation.PageTap
import com.github.e0ipso.tokkl.navigation.RequestOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.timerstate.HeldTimerState
import com.github.e0ipso.tokkl.timerstate.PomodoroPhaseKey
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.coroutines.flow.Flow
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
 * The page's view model is the signed-in session's, and its starts and retries may ask for the
 * notification permission through the frame's prompt.
 */
internal object PomodoroEntries : FeatureRoutes {
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
}

/** [notification] is made on the first tap, so listing the features makes nothing. */
internal class PomodoroRequests(
    private val notification: Lazy<PomodoroSessionNotificationSync>,
    private val io: CoroutineContext,
) : FeatureRequests {
    override val linkExtras
        get() = listOf(PageTap.POMODORO_WIDGET.extra, POMODORO_NOTIFICATION_ACCOUNT, EXTRA_PHASE_START, EXTRA_REVISION)

    override fun readLinks(intent: LinkIntent): List<NavigationRequest> = listOfNotNull(
        sessionTap(intent),
        PomodoroWidgetTap.takeIf { PageTap.POMODORO_WIDGET.isIn(intent) },
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

@Serializable
data object PomodoroWidgetTap : NavigationRequest

/** A session notification's tap; [phaseStart] is in epoch milliseconds. */
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

@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Pomodoro")
data object PomodoroRoute : Route {
    override val showsTimerNotices: Boolean get() = true
}

class PomodoroBadge(held: Flow<HeldTimerState?>) :
    PageBadge(PomodoroRoute, R.string.nav_pomodoro_running, held.map { it?.pomodoro != null })
