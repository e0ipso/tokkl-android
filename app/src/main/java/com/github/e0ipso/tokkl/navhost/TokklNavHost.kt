package com.github.e0ipso.tokkl.navhost

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.navigation.FeatureNavigation
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.NotificationPrompts
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.navigation.routeSerializers
import com.github.e0ipso.tokkl.timernotice.TimerFrame
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.PolymorphicSerializer

/**
 * The app's navigation host: a Navigation 3 back stack of [Route]s that survives configuration
 * changes and process death. It starts at sign-in until a server and its token are stored, and at
 * the Timer page after that. A restored signed-in back stack goes to sign-in when nobody is signed
 * in any more, and a sign-out starts the navigation over at sign-in, with new view models.
 *
 * Opening a page from the bar or the rail puts it right above Timer, so Back from any top-level
 * page goes to Timer, and Back from Timer closes the app (docs/PRD.md B3). A screen opened from a
 * page, such as Licences from Settings, stacks above that page, and the bar keeps marking the page.
 * A page shows only while the server's [capabilities] offer what it needs
 * ([TopLevelPages.unavailable]): when the server loses it, the page goes back to Timer, and a back
 * stack restored from before shows Timer.
 *
 * Each route's screen takes its own view models, which go when the route leaves the stack. The
 * ones that several screens share live in the signed-in session's store ([NavigationStores]).
 */
@Composable
internal fun TokklNavHost(
    requests: NavigationRequests,
    features: FeatureNavigation,
    pages: TopLevelPages,
    signOuts: StateFlow<Int>,
    capabilities: StateFlow<Capabilities>,
    signIns: SignInRoutes,
    timerFrame: @Composable () -> TimerFrame,
    notificationPrompt: NotificationPrompt? = null,
    stores: NavigationStores = viewModel { NavigationStores() },
) {
    // Collected while stopped too, so a sign-out that finished in the background shows sign-in
    // from the first frame back.
    val signOutCount by signOuts.collectAsState()
    val discarded = remember(stores, signOutCount) { stores.after(signOutCount) }
    LaunchedEffect(signOutCount) {
        // A tap from before the sign-out opens nothing for the next account.
        if (discarded) requests.clear()
    }
    key(signOutCount) {
        CompositionLocalProvider(LocalSignedInSession provides stores.session) {
            Navigation(
                stores = stores,
                requests = requests,
                capabilities = capabilities,
                features = features,
                pages = pages,
                signIns = signIns,
                timerFrame = timerFrame,
                notificationPrompt = notificationPrompt,
            )
        }
    }
}

@Composable
private fun Navigation(
    stores: NavigationStores,
    requests: NavigationRequests,
    capabilities: StateFlow<Capabilities>,
    features: FeatureNavigation,
    pages: TopLevelPages,
    signIns: SignInRoutes,
    timerFrame: @Composable () -> TimerFrame,
    notificationPrompt: NotificationPrompt?,
) {
    val routes = remember(features) {
        SavedStateConfiguration { serializersModule = routeSerializers(features.routes) }
    }
    val backStack = rememberSerializable(
        serializer = NavBackStackSerializer(PolymorphicSerializer(Route::class)),
        configuration = routes,
    ) { NavBackStack<Route>() }
    val savedStates = rememberSaveableStateHolder()
    val navigator = remember(backStack, stores) { Navigator(backStack, stores.entryOpenings) }
    val prompts = remember { NotificationPrompts() }
    val host = remember(navigator, prompts) { NavigationHost(navigator, prompts) }
    val entryProvider = remember(features, host) {
        entryProvider<Route> { features.routes.forEach { feature -> with(feature) { addEntries(host) } } }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(savedStates),
            rememberViewModelStoreNavEntryDecorator(stores.entries),
        ),
        entryProvider = entryProvider,
    )
    // A signed-in page restored from saved state can outlive its session: a sign-out that finished
    // in the background before the process died, or a token that no longer decrypts. The page
    // shows while the stores are read, once per composition of the host, so sign-in pays nothing.
    val restoredSignedIn = remember { backStack.lastOrNull().let { it != null && !signIns.isSignIn(it) } }
    if (restoredSignedIn) {
        LaunchedEffect(Unit) {
            if (!signIns.isSignedIn()) navigator.startOver(signIns.signIn())
        }
    }
    if (backStack.isEmpty()) {
        // Shows the window background, which matches the theme, while the
        // stores are read off the main thread.
        LaunchedEffect(Unit) {
            backStack.add(if (signIns.isSignedIn()) Route.Timer else signIns.signIn())
        }
        return
    }
    // Collected while stopped too: a page the server lost never shows, not even on the first frame back.
    val flags by capabilities.collectAsState()
    val unavailable = pages.unavailable(flags)
    // A page the server lacks never shows, not even for the frame before the stack is corrected.
    val top = backStack.last().orTimerIfUnavailable(unavailable)
    val onSignIn = signIns.isSignIn(top)
    NavigationRequestEffects(requests, features.requests, navigator, signIns, unavailable, onSignIn = onSignIn)
    BackHandler(enabled = backStack.size > 1) { navigator.back() }
    if (onSignIn) {
        LaunchedEffect(Unit) { savedStates.removeState(Route.Timer.contentKey) }
        entries.Show(top)
        return
    }
    SignedInHost(
        stack = backStack,
        top = top,
        entries = entries,
        host = host,
        features = features.frame,
        pages = pages,
        unavailable = unavailable,
        notificationPrompt = notificationPrompt,
        timerFrame = timerFrame,
    )
}
