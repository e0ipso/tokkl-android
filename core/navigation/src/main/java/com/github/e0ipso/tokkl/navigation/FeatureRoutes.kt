package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation3.runtime.EntryProviderScope
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The screens of one feature: the routes it declares, the back stack entries of those routes and
 * the top-level pages it puts in the bar, the rail or the More sheet. Each feature with a screen
 * binds its own in Koin, and the navigation host adds every one, so no feature names another's
 * screens and the host names none. A route opens another feature's screen through [Navigator].
 * Every member is the feature's to give: a feature without a top-level page says so with an empty
 * [pages]. The app's unit tests check that every route a feature declares has a serializer and an
 * entry.
 */
interface FeatureRoutes {

    /** This feature's top-level pages, which [TopLevelPages] puts in order. */
    val pages: List<TopLevelDestination>

    /** Registers the serializer of each route this feature declares, so the saved back stack holds it. */
    fun PolymorphicModuleBuilder<Route>.registerRoutes()

    /** Adds the entry of each route this feature shows, with [route] or [routes]. */
    fun EntryProviderScope<Route>.addEntries(host: NavigationHost)
}

/**
 * The serializers of every route: Timer's, the entry form's and those [features] declare. The
 * navigation host saves its back stack with them.
 */
fun routeSerializers(features: List<FeatureRoutes>): SerializersModule = SerializersModule {
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

fun <R : Route> EntryProviderScope<Route>.route(route: R, content: @Composable (R) -> Unit) {
    entry(route, contentKey = route.contentKey, content = content)
}

inline fun <reified R : Route> EntryProviderScope<Route>.routes(noinline content: @Composable (R) -> Unit) {
    entry<R>(clazzContentKey = { it.contentKey }, content = content)
}

/**
 * What a screen shares with the signed-in frame's notification permission prompt: the [starts]
 * and [retries] it makes, which may ask for the permission, and a count of the prompt's
 * [answers], which tells the screen to read the permission again. The Pomodoro page uses it; the
 * Timer page's starts reach the prompt through its view model.
 */
class NotificationPrompts {
    val starts = Channel<Unit>(Channel.UNLIMITED)
    val retries = Channel<Unit>(Channel.UNLIMITED)
    var answers by mutableIntStateOf(0)
}

/**
 * What one feature shows in the signed-in frame around every page. Only a feature that shows
 * something there binds one, such as the sync status band and the discarded changes' notice.
 */
interface FrameContributions {

    /**
     * The band this feature shows right above the bottom bar, or at the bottom of the screen beside
     * the rail, or null while it shows none.
     */
    @Composable
    fun frameBand(host: NavigationHost): (@Composable () -> Unit)?

    /**
     * What this feature shows over every signed-in page, with the modifier that places it at the
     * bottom, below the timer's snackbars and the notification permission's prompt, or null while
     * it shows nothing.
     */
    @Composable
    fun frameNotice(host: NavigationHost): (@Composable (Modifier) -> Unit)?
}

/**
 * What the features hand the navigation host, each part from the features that give it: the
 * [routes] with their screens and pages, the [requests] of the taps from outside the app, and the
 * [frame]'s bands and notices. MainActivity gathers them from Koin.
 */
class FeatureNavigation(
    val routes: List<FeatureRoutes>,
    val requests: List<FeatureRequests>,
    val frame: List<FrameContributions>,
)

/**
 * The signed-in session's view model store, which the navigation host provides. The view models
 * that several screens share live there, and a sign-out drops them with the session.
 */
val LocalSignedInSession = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("No signed-in session store")
}
