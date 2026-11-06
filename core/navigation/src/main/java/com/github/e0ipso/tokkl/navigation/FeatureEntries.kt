package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import com.github.e0ipso.tokkl.capabilities.Capabilities
import kotlinx.coroutines.flow.StateFlow

/**
 * The screens of one feature: the back stack entries of the routes it shows. Each feature module
 * binds its own in Koin, and the navigation host adds every one to its entry provider, so no
 * feature names another's screens. A route opens another feature's screen through [Navigator].
 */
interface FeatureEntries {

    /** Adds the entry of each route this feature shows, with [route] or [routes]. */
    fun EntryProviderScope<Route>.addEntries(host: NavigationHost)
}

/**
 * What the navigation host lends every screen: the [navigator] of its back stack, the [prompts]
 * the Pomodoro page shares with the frame's notification permission prompt, and the server's
 * [capabilities], which a test of the host may replace.
 */
class NavigationHost(
    val navigator: Navigator,
    val prompts: NotificationPrompts,
    val capabilities: StateFlow<Capabilities>,
)

/** The entry of the object route [route], under its [contentKey]. */
fun <R : Route> EntryProviderScope<Route>.route(route: R, content: @Composable (R) -> Unit) {
    entry(route, contentKey = route.contentKey, content = content)
}

/** The entries of every route of class [R], each under its own [contentKey]. */
inline fun <reified R : Route> EntryProviderScope<Route>.routes(noinline content: @Composable (R) -> Unit) {
    entry<R>(clazzContentKey = { it.contentKey }, content = content)
}
