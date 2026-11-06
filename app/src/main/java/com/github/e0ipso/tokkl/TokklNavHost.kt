package com.github.e0ipso.tokkl

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
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.di.IoDispatcher
import com.github.e0ipso.tokkl.navigation.AppNavigationBar
import com.github.e0ipso.tokkl.navigation.AppNavigationRail
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.NavigationRequestEffects
import com.github.e0ipso.tokkl.navigation.NavigationRequests
import com.github.e0ipso.tokkl.navigation.NavigationStores
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.NotificationPrompts
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.Show
import com.github.e0ipso.tokkl.navigation.SignedInHost
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.navigation.orTimerIfUnavailable
import com.github.e0ipso.tokkl.navigation.routeEntry
import com.github.e0ipso.tokkl.navigation.unavailablePages
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.session.SignOut
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * The app's navigation host: a Navigation 3 back stack of [Route]s that survives configuration
 * changes and process death. It starts at sign-in until a server and its token are stored, and at
 * the Timer page after that. A restored signed-in back stack goes to sign-in when nobody is signed
 * in any more, and a sign-out starts the navigation over at sign-in, with new view models.
 *
 * Every signed-in screen has the bottom bar ([AppNavigationBar]), or on a medium or expanded
 * window the navigation rail ([AppNavigationRail]) with the same items. Opening a page from it puts
 * that page right above Timer, so Back from any page in the bar or the More sheet goes to Timer,
 * and Back from Timer closes the app (docs/PRD.md B3). A screen opened from a page, such as Licences from
 * Settings, stacks above that page, and the bar keeps marking the page. Trash shows only while the
 * server's [capabilities] have it, and Timer templates while the server keeps that key: when they
 * lose it, the page goes back to Timer. Pomodoro shows in the bar and the rail only while the
 * server has the timer API, since the server owns the session. Without it the page opens nowhere,
 * and a back stack restored from before the server lost it shows Timer. [requests] opens the
 * pages that widget, reminder and Pomodoro notification taps ask for.
 *
 * Each route's screen takes its own view models, which go when the route leaves the stack. The
 * ones that several screens share live in the signed-in session's store ([NavigationStores]).
 */
@Composable
fun TokklNavHost(
    requests: NavigationRequests = remember { NavigationRequests() },
    capabilities: StateFlow<Capabilities> = koinInject<CapabilitiesSource>().state,
    notificationPermission: NotificationPermissionAsk? = null,
    onNotificationsAllowed: () -> Unit = {},
) {
    val signOuts by koinInject<SignOut>().finished.collectAsState()
    val stores = viewModel { NavigationStores() }
    val discarded = remember(stores, signOuts) { stores.after(signOuts) }
    LaunchedEffect(signOuts) {
        // A tap from before the sign-out opens nothing for the next account.
        if (discarded) requests.clear()
    }
    // A sign-out starts the navigation over, at sign-in, with a back stack of its own.
    key(signOuts) {
        CompositionLocalProvider(LocalSignedInSession provides stores.session) {
            Navigation(stores, requests, capabilities, notificationPermission, onNotificationsAllowed)
        }
    }
}

@Composable
private fun Navigation(
    stores: NavigationStores,
    requests: NavigationRequests,
    capabilities: StateFlow<Capabilities>,
    notificationPermission: NotificationPermissionAsk?,
    onNotificationsAllowed: () -> Unit,
) {
    val session: Session = koinInject()
    val io: CoroutineDispatcher = koinInject(IoDispatcher)
    val backStack = rememberSerializable(serializer = NavBackStackSerializer(Route.serializer())) { NavBackStack<Route>() }
    val savedStates = rememberSaveableStateHolder()
    val navigator = remember(backStack, stores) { Navigator(backStack, stores.entryOpenings) }
    val prompts = remember { NotificationPrompts() }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(savedStates),
            rememberViewModelStoreNavEntryDecorator(stores.entries),
        ),
        entryProvider = { route -> routeEntry(route, navigator, prompts, capabilities) },
    )
    // A signed-in page restored from saved state can outlive its session: a sign-out that finished
    // in the background before the process died, or a token that no longer decrypts. The page
    // shows while the stores are read, once per composition of the host, so sign-in pays nothing.
    val restoredSignedIn = remember { backStack.lastOrNull().let { it != null && it !is Route.SignIn } }
    if (restoredSignedIn) {
        LaunchedEffect(Unit) {
            val signedIn = withContext(io) { session.isSignedIn() }
            if (!signedIn) navigator.signIn()
        }
    }
    if (backStack.isEmpty()) {
        // Shows the window background, which matches the theme, while the
        // stores are read off the main thread.
        LaunchedEffect(Unit) {
            val signedIn = withContext(io) { session.isSignedIn() }
            backStack.add(if (signedIn) Route.Timer else Route.SignIn())
        }
        return
    }
    val flags by capabilities.collectAsState()
    val unavailable = unavailablePages(flags, hasFavorites = true)
    // A page the server lacks never shows, not even for the frame before the stack is corrected.
    val top = backStack.last().orTimerIfUnavailable(unavailable)
    NavigationRequestEffects(requests, navigator, unavailable, onSignIn = top is Route.SignIn)
    BackHandler(enabled = backStack.size > 1) { navigator.back() }
    if (top is Route.SignIn) {
        LaunchedEffect(Unit) { savedStates.removeState(Route.Timer.contentKey) }
        entries.Show(top)
        return
    }
    SignedInHost(
        stack = backStack,
        top = top,
        entries = entries,
        navigator = navigator,
        unavailable = unavailable,
        prompts = prompts,
        notificationPermission = notificationPermission,
        onNotificationsAllowed = onNotificationsAllowed,
    )
}
