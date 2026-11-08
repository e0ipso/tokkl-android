package com.github.e0ipso.tokkl.pomodoro

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.feature.pomodoro.R
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import com.github.e0ipso.tokkl.core.ui.R as UiR

/**
 * The Pomodoro page. Its view model is the signed-in session's, and its starts and retries may ask
 * for the notification permission through the frame's prompt.
 */
internal object PomodoroEntries : FeatureEntries {
    override fun EntryProviderScope<Route>.addEntries(host: NavigationHost) {
        val prompts = host.prompts
        route(Route.Pomodoro) {
            PomodoroScreen(
                model = sessionViewModel<PomodoroViewModel>(),
                onNotificationStart = { prompts.pomodoroStarts.trySend(Unit) },
                onNotificationRetry = { prompts.retries.trySend(Unit) },
                notificationPermissionRevision = prompts.answers,
            )
        }
    }
}

/** The Pomodoro page, in the bar after Timer. */
val pomodoroPage = TopLevelDestination(
    route = Route.Pomodoro,
    label = UiR.string.pomodoro_title,
    icon = R.drawable.ic_nav_pomodoro,
    hue = SectionHues.timer,
    position = 10,
)
