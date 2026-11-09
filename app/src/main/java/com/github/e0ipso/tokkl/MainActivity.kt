package com.github.e0ipso.tokkl

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.lifecycle.MainActivityForeground
import com.github.e0ipso.tokkl.navhost.DeepLinks
import com.github.e0ipso.tokkl.navhost.NavigationRequests
import com.github.e0ipso.tokkl.navhost.TokklNavHost
import com.github.e0ipso.tokkl.navhost.takeDeepLinks
import com.github.e0ipso.tokkl.navigation.FeatureNavigation
import com.github.e0ipso.tokkl.navigation.LocalSignedInSession
import com.github.e0ipso.tokkl.navigation.SignInRoutes
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import com.github.e0ipso.tokkl.navigation.requestSerializers
import com.github.e0ipso.tokkl.notification.NotificationPermissionAsk
import com.github.e0ipso.tokkl.notification.NotificationPermissionPrompt
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.platform.holdDrawingUntil
import com.github.e0ipso.tokkl.session.AccountLifetime
import com.github.e0ipso.tokkl.storage.StartupLoads
import com.github.e0ipso.tokkl.timer.TimerViewModel
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsRepository
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {

    private val themeSettings: ThemeSettingsRepository by inject()
    private val foreground: MainActivityForeground by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)
    private val features: FeatureNavigation by inject()
    private val pages: TopLevelPages by inject()
    private val lifetime: AccountLifetime by inject()
    private val capabilities: CapabilitiesSource by inject()
    private val signIns: SignInRoutes by inject()
    private val loads: StartupLoads by inject()

    /** The pages that taps outside the app asked to open, until the navigation opens them. */
    private val requests: NavigationRequests by viewModels {
        viewModelFactory {
            initializer { NavigationRequests(createSavedStateHandle(), requestSerializers(features.requests)) }
        }
    }

    /** What each feature reads of the taps from outside the app. */
    private val links by lazy { DeepLinks(features.requests) }

    /** Whether the screens are composed, which they are once every store has loaded. */
    private var composed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the splash screen up, and draws no frame, until the screens are composed.
        findViewById<View>(android.R.id.content).holdDrawingUntil { composed }
        drawEdgeToEdge()
        intent?.takeDeepLinks(links)?.forEach(requests::request)
        lifecycle.addObserver(foreground)
        // The screens compose once every store has loaded off the main thread, so they show the
        // stored theme, account settings and capabilities from their first composition. At once
        // when the loads have ended, as in a process that runs already.
        lifecycleScope.launch {
            loads.ready()
            showScreens()
            composed = true
        }
    }

    private fun showScreens() {
        val systemIsDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // The first frame already has the chosen theme's icons.
        showSystemBarIconsFor(darkTheme = themeSettings.state.value.mode.isDark(systemIsDark))
        setContent {
            val settings by themeSettings.state.collectAsStateWithLifecycle()
            val darkTheme = settings.mode.isDark(systemIsDark = isSystemInDarkTheme())
            TokklTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                WindowFollowsTheme(darkTheme)
                val ask = remember { NotificationPermissionAsk.forApp(this, io) }
                TokklNavHost(
                    requests = requests,
                    features = features,
                    pages = pages,
                    signOuts = lifetime.signOuts,
                    capabilities = capabilities.state,
                    signIns = signIns,
                    timerFrame = {
                        koinViewModel<TimerViewModel>(viewModelStoreOwner = LocalSignedInSession.current).frame
                    },
                    notificationPrompt = { starts, retries, onAnswer ->
                        NotificationPermissionPrompt(
                            starts,
                            ask,
                            onAllow = foreground::notificationsGranted,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            retries = retries,
                            onResult = { onAnswer() },
                        )
                    },
                )
            }
        }
    }

    /**
     * Takes a tap's pages from [intent] and leaves the activity's own intent as it was started.
     * Nothing reads the intent after onCreate, and a page still waiting outlives rotation in the
     * requests' saved state.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.takeDeepLinks(links).forEach(requests::request)
    }

    /**
     * Paints the window in the scheme's background and picks system bar icons that show on it.
     * The window theme follows the light or dark setting through the app's night mode, but never
     * knows the wallpaper's colours, so this sets the background and the icons from the chosen
     * theme. Effects run before the first frame draws.
     */
    @Composable
    private fun WindowFollowsTheme(darkTheme: Boolean) {
        val background = MaterialTheme.colorScheme.background.toArgb()
        DisposableEffect(darkTheme, background) {
            window.setBackgroundDrawable(background.toDrawable())
            showSystemBarIconsFor(darkTheme)
            onDispose {}
        }
    }

    /**
     * Draws behind transparent system bars, with the system's navigation bar contrast enforcement
     * off, so the screen's own background shows under both bars.
     *
     * With enforcement on, which androidx.activity's `enableEdgeToEdge` turns on for
     * `SystemBarStyle.auto`, Android paints a translucent scrim over a three-button
     * navigation bar: a near-white band on the light background, a lighter one on the dark.
     * `enableEdgeToEdge` also reapplies its first call's styles after every configuration change,
     * whatever later calls set. `WindowCompat.enableEdgeToEdge` sets the window flags once, with
     * enforcement off, and leaves the icons to [showSystemBarIconsFor].
     */
    private fun drawEdgeToEdge() {
        WindowCompat.enableEdgeToEdge(window)
    }

    /** Dark status and navigation bar icons on a light theme, light icons on a dark one. */
    private fun showSystemBarIconsFor(darkTheme: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
