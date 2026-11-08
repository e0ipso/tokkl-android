package com.github.e0ipso.tokkl.navhost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.NavigationRequest
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.RequestOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * The pages that taps outside the app asked to open, until the navigation host opens them.
 * MainActivity fills it from its intents ([takeDeepLinks]). It is MainActivity's view model, and
 * what still waits is kept in [saved], with the features' request serializers ([serializers]), so
 * it outlives a configuration change and the process. One request of each kind waits at a time: a
 * later tap of the same kind replaces the earlier one.
 */
class NavigationRequests(saved: SavedStateHandle, serializers: SerializersModule) : ViewModel() {
    private val waiting = mutableStateListOf<NavigationRequest>()
    private val configuration = SavedStateConfiguration { serializersModule = serializers }

    init {
        saved.get<SavedState>(PENDING)?.let { state ->
            // A request from an older version of the app may no longer exist, and then waits no more.
            try {
                waiting += decodeFromSavedState(serializer, state, configuration)
            } catch (_: SerializationException) {
                // Nothing waits.
            }
        }
        saved.setSavedStateProvider(PENDING) { encodeToSavedState(serializer, waiting.toList(), configuration) }
    }

    val pending: List<NavigationRequest> get() = waiting

    fun request(request: NavigationRequest) {
        waiting.removeAll { it::class == request::class }
        waiting += request
    }

    /** [request] was opened, or turned down. A request that replaced it since stays, unless it is equal. */
    fun handled(request: NavigationRequest) {
        waiting.remove(request)
    }

    fun clear() = waiting.clear()

    private companion object {
        const val PENDING = "pending-navigation-requests"
        val serializer = ListSerializer(PolymorphicSerializer(NavigationRequest::class))
    }
}

/**
 * Opens what [requests] ask for, each as soon as it can, through the feature that declared it
 * ([FeatureEntries.OpenRequest]), which reads whether somebody is signed in from [signIns]. A
 * page in [unavailable] opens Timer, and [onSignIn] tells a request that sign-in shows.
 */
@Composable
internal fun NavigationRequestEffects(
    requests: NavigationRequests,
    features: List<FeatureEntries>,
    navigator: Navigator,
    signIns: SignInRoutes,
    unavailable: Set<Route>,
    onSignIn: Boolean,
) {
    val currentUnavailable by rememberUpdatedState(unavailable)
    val opening = RequestOpening(
        navigator = navigator,
        unavailableNow = { currentUnavailable },
        onSignIn = onSignIn,
        signIns = signIns,
        handled = requests::handled,
    )
    for (request in requests.pending) {
        key(request) {
            for (feature in features) feature.OpenRequest(request, opening)
        }
    }
}
