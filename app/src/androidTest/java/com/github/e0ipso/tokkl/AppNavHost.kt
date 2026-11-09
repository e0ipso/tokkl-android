package com.github.e0ipso.tokkl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.navhost.NavigationRequests
import com.github.e0ipso.tokkl.navhost.TokklNavHost
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.requestSerializers
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.timer.TimerViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext

/**
 * [TokklNavHost] with what MainActivity hands it, from the app's Koin graph, and no notification
 * permission prompt. A test may replace the server's [capabilities].
 */
@Composable
internal fun AppNavHost(
    capabilities: StateFlow<Capabilities> = remember { GlobalContext.get().get<CapabilitiesSource>().state },
) {
    val koin = remember { GlobalContext.get() }
    val features = remember { koin.getAll<FeatureEntries>() }
    TokklNavHost(
        requests = remember { NavigationRequests(SavedStateHandle(), requestSerializers(features)) },
        features = features,
        pages = remember { koin.get() },
        signOuts = remember { koin.get<AccountLifetime>().signOuts },
        capabilities = capabilities,
        signIns = remember { koin.get() },
        timerFrame = { koinViewModel<TimerViewModel>(viewModelStoreOwner = LocalSignedInSession.current).frame },
    )
}
