package com.github.e0ipso.tokkl.pomodoro

import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.route
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import org.koin.compose.koinInject

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
                cache = koinInject(),
                onNotificationStart = { prompts.pomodoroStarts.trySend(Unit) },
                onNotificationRetry = { prompts.retries.trySend(Unit) },
                notificationPermissionRevision = prompts.answers,
            )
        }
    }
}
