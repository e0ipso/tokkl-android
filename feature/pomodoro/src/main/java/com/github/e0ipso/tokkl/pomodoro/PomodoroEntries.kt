package com.github.e0ipso.tokkl.pomodoro

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.pomodoro.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The Pomodoro page. Its view model is the signed-in session's, and its starts and retries may ask
 * for the notification permission through the frame's prompt.
 */
internal object PomodoroEntries : FeatureEntries {
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

/** The Pomodoro page, in the bar after Timer. */
val pomodoroPage = TopLevelDestination(
    route = PomodoroRoute,
    label = UiR.string.pomodoro_title,
    icon = R.drawable.ic_nav_pomodoro,
    hue = SectionHues.timer,
    position = 10,
)

/** The Pomodoro page. */
@Serializable
@SerialName("com.github.e0ipso.tokkl.navigation.Route.Pomodoro")
data object PomodoroRoute : Route
