package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The screens of one feature: the routes it declares, the back stack entries of the routes it
 * shows and the top-level pages it puts in the bar, the rail or the More sheet. Each feature
 * module binds its own in Koin, and the navigation host adds every one, so no feature names
 * another's screens. A route opens another feature's screen through [Navigator].
 */
interface FeatureEntries {

    /** This feature's top-level pages, which [TopLevelPages] puts in order. */
    val pages: List<TopLevelDestination> get() = emptyList()

    /** Registers the serializer of each route this feature declares, so the saved back stack holds it. */
    fun PolymorphicModuleBuilder<Route>.registerRoutes() = Unit

    /** Adds the entry of each route this feature shows, with [route] or [routes]. */
    fun EntryProviderScope<Route>.addEntries(host: NavigationHost)
}

/**
 * The serializers of every route: Timer's, the entry form's and those [features] declare. The
 * navigation host saves its back stack with them.
 */
fun routeSerializers(features: List<FeatureEntries>): SerializersModule = SerializersModule {
    polymorphic(Route::class) {
        subclass(Route.Timer::class)
        subclass(Route.Entry::class)
        features.forEach { feature -> with(feature) { registerRoutes() } }
    }
}

/**
 * What the navigation host lends every screen: the [navigator] of its back stack, and the
 * [prompts] a screen shares with the frame's notification permission prompt.
 */
class NavigationHost(val navigator: Navigator, val prompts: NotificationPrompts)

/** The entry of the object route [route], under its [contentKey]. */
fun <R : Route> EntryProviderScope<Route>.route(route: R, content: @Composable (R) -> Unit) {
    entry(route, contentKey = route.contentKey, content = content)
}

/** The entries of every route of class [R], each under its own [contentKey]. */
inline fun <reified R : Route> EntryProviderScope<Route>.routes(noinline content: @Composable (R) -> Unit) {
    entry<R>(clazzContentKey = { it.contentKey }, content = content)
}
