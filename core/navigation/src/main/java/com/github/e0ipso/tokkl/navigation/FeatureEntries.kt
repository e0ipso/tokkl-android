package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The screens of one feature: the routes it declares, the back stack entries of the routes it
 * shows, the top-level pages it puts in the bar, the rail or the More sheet, the taps from outside
 * the app that open them, and what it shows in the signed-in frame around every page. Each
 * feature module binds its own in Koin, and the navigation host adds every one, so no feature
 * names another's screens and the host names none. A route opens another feature's screen
 * through [Navigator].
 */
interface FeatureEntries {

    /** This feature's top-level pages, which [TopLevelPages] puts in order. */
    val pages: List<TopLevelDestination> get() = emptyList()

    /** Registers the serializer of each route this feature declares, so the saved back stack holds it. */
    fun PolymorphicModuleBuilder<Route>.registerRoutes() = Unit

    /** Adds the entry of each route this feature shows, with [route] or [routes]. */
    fun EntryProviderScope<Route>.addEntries(host: NavigationHost)

    /** Every intent extra this feature's taps carry, which MainActivity removes once read. */
    val linkExtras: List<String> get() = emptyList()

    /** The requests that one of MainActivity's intents, [intent], carries for this feature. */
    fun readLinks(intent: LinkIntent): List<NavigationRequest> = emptyList()

    /** Registers the serializer of each request this feature declares, so a waiting one outlives the process. */
    fun PolymorphicModuleBuilder<NavigationRequest>.registerRequests() = Unit

    /**
     * Opens [request] when it is one this feature declared, once it can, through [opening], which
     * it tells when the request is handled. The host calls it for every waiting request.
     */
    @Composable
    fun OpenRequest(request: NavigationRequest, opening: RequestOpening) = Unit

    /**
     * The band this feature shows right above the bottom bar, or at the bottom of the screen beside
     * the rail, or null while it shows none.
     */
    @Composable
    fun frameBand(host: NavigationHost): (@Composable () -> Unit)? = null

    /**
     * What this feature shows over every signed-in page, with the modifier that places it at the
     * bottom, below the timer's snackbars and the notification permission's prompt, or null while
     * it shows nothing.
     */
    @Composable
    fun frameNotice(host: NavigationHost): (@Composable (Modifier) -> Unit)? = null
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

/** The serializers of every request from outside the app that [features] declare. */
fun requestSerializers(features: List<FeatureEntries>): SerializersModule = SerializersModule {
    polymorphic(NavigationRequest::class) {
        features.forEach { feature -> with(feature) { registerRequests() } }
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
