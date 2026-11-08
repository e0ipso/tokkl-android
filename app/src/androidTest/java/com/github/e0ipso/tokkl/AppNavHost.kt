package com.github.e0ipso.tokkl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.e0ipso.tokkl.capabilities.Capabilities
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.di.FeatureScreens
import com.github.e0ipso.tokkl.navhost.NavigationRequests
import com.github.e0ipso.tokkl.navigation.sessionViewModel
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.timer.TimerViewModel
import kotlinx.coroutines.flow.StateFlow
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
    TokklNavHost(
        requests = remember { NavigationRequests() },
        features = remember { koin.get(FeatureScreens) },
        pages = remember { koin.get() },
        signOuts = remember { koin.get<AccountLifetime>().signOuts },
        capabilities = capabilities,
        session = remember { koin.get() },
        timerFrame = { sessionViewModel<TimerViewModel>() },
    )
}
