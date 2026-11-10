package com.github.e0ipso.tokkl.navigation

import android.content.Intent
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

    /** Reads the stores off the main thread. */
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

/**
 * A tap outside the app that opens one page of MainActivity, such as a widget's or a reminder's,
 * and the boolean extra its intent carries. The surface that makes the intent adds the tap with
 * [into], and the feature that opens the page reads it with [isIn], so both read one name.
 */
enum class PageTap(val extra: String) {
    TIMER_WIDGET("open_timer_widget"),

    /** The Timer widget's day report tap, which opens today's report. */
    TODAY_REPORT("open_today_widget"),

    POMODORO_WIDGET("open_pomodoro_widget"),

    /** A long-running timer reminder's tap, which opens Timer. */
    REMINDER("com.github.e0ipso.tokkl.OPEN_TIMER_REMINDER"),
    ;

    /**
     * [mainActivity], an intent that opens MainActivity, carrying this tap: in a new task, or in the
     * one on show, brought back to MainActivity.
     */
    fun into(mainActivity: Intent): Intent = mainActivity
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(extra, true)

    fun isIn(intent: LinkIntent): Boolean = intent.flag(extra)
}

/**
 * Whether somebody is signed in, and the sign-in screen's routes, which the sign-in feature binds
 * in Koin. The host starts at sign-in while nobody is signed in and goes there after a sign-out,
 * and the sync status opens it again for the account after the server refused its token.
 */
interface SignInRoutes {

    /** Whether a server and its token are stored. Reads the stores off the main thread. */
    suspend fun isSignedIn(): Boolean

    /** Sign-in, with nothing filled in. */
    fun signIn(): Route

    /** Whether [route] is a sign-in screen, which shows without the bar. */
    fun isSignIn(route: Route): Boolean

    /**
     * Sign-in filled in for the account signed in now, so the user signs in again to the same
     * account, or null without a stored server. Reads the stores off the main thread.
     */
    suspend fun again(): Route?
}
