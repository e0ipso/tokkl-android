package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.Composable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * A page that a tap outside the app asks MainActivity to open, such as a widget's or a
 * notification's. Each feature declares the requests of its taps as `@Serializable` classes and
 * registers them ([FeatureRequests.registerRequests]), reads them from MainActivity's intents
 * ([FeatureRequests.readLinks]) and opens them ([FeatureRequests.OpenRequest]), so the host names
 * none of them. One request of each class waits at a time.
 */
interface NavigationRequest

/**
 * The taps from outside the app that open one feature's pages, such as its widget's or its
 * notification's. Only a feature whose pages such taps open binds one.
 */
interface FeatureRequests {

    /** Every intent extra this feature's taps carry, which MainActivity removes once read. */
    val linkExtras: List<String>

    /** The requests that one of MainActivity's intents, [intent], carries for this feature. */
    fun readLinks(intent: LinkIntent): List<NavigationRequest>

    /** Registers the serializer of each request this feature declares, so a waiting one outlives the process. */
    fun PolymorphicModuleBuilder<NavigationRequest>.registerRequests()

    /**
     * Opens [request] when it is one this feature declared, once it can, through [opening], which
     * it tells when the request is handled. The host calls it for every waiting request.
     */
    @Composable
    fun OpenRequest(request: NavigationRequest, opening: RequestOpening)
}

/** The serializers of every request from outside the app that [features] declare. */
fun requestSerializers(features: List<FeatureRequests>): SerializersModule = SerializersModule {
    polymorphic(NavigationRequest::class) {
        features.forEach { feature -> with(feature) { registerRequests() } }
    }
}

/** What a feature reads of one of MainActivity's intents: its action and its extras. */
interface LinkIntent {
    val action: String?

    /** The boolean extra [key], false when absent. */
    fun flag(key: String): Boolean

    /** The string extra [key], or null. */
    fun text(key: String): String?

    /** The long extra [key], or null when absent. */
    fun number(key: String): Long?
}

/**
 * What the host lends the opening of a request: the [navigator], the pages the server lacks now,
 * whether sign-in shows ([onSignIn]), and the session. A request opens once it can, and then
 * tells [handled], which lets a later tap of the same kind wait in its place.
 */
class RequestOpening(
    val navigator: Navigator,
    private val unavailableNow: () -> Set<Route>,
    val onSignIn: Boolean,
    private val signIns: SignInRoutes,
    val handled: (NavigationRequest) -> Unit,
) {
    /** The pages the server lacks now, which open Timer instead. */
    val unavailable: Set<Route> get() = unavailableNow()

    /** Whether somebody is signed in. Reads the stores off the main thread. */
    suspend fun isSignedIn(): Boolean = signIns.isSignedIn()

    /**
     * Opens the top-level page [route] when somebody is signed in, after [before], and sign-in
     * otherwise.
     */
    suspend fun openSignedIn(route: Route, before: () -> Unit = {}) {
        if (isSignedIn()) {
            before()
            navigator.open(route, unavailable)
        } else {
            navigator.startOver(signIns.signIn())
        }
    }
}
