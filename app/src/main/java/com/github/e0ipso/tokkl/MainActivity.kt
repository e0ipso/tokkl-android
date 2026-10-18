package com.github.e0ipso.tokkl

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.ui.Brand
import com.github.e0ipso.tokkl.ui.BrandSize
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val signInViewModel: SignInViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawEdgeToEdge()
        val themeSettings = (application as TokklApplication).themeSettings
        // Reading the theme setting loads it, so the first frame already has the chosen theme's icons.
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        showSystemBarIconsFor(darkTheme = themeSettings.state.value.mode.isDark(systemIsDark))
        refreshOnSignIn()
        setContent {
            val settings by themeSettings.state.collectAsState()
            val darkTheme = settings.mode.isDark(systemIsDark = isSystemInDarkTheme())
            TokklTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                WindowFollowsTheme(darkTheme)
                TokklNavHost(
                    signInModel = signInViewModel.model,
                    themeSettings = themeSettings,
                    sessionTrust = (application as TokklApplication).sessionTrust,
                    reports = reportsViewModel.model,
                )
            }
        }
    }

    /**
     * Coming to the foreground reads the account settings again (docs/PRD.md S7) and refreshes the
     * offline cache with the same `GET /api/v9/me` (O2). Nothing refreshes on a timer.
     */
    override fun onStart() {
        super.onStart()
        (application as TokklApplication).accountSettings.refresh()
    }

    /** A sign-in loads the account settings and the cache, as coming to the foreground does. */
    private fun refreshOnSignIn() {
        lifecycleScope.launch {
            signInViewModel.model.state
                .map { it.signedIn }
                .distinctUntilChanged()
                .filter { it }
                .collect { (application as TokklApplication).accountSettings.refresh() }
        }
    }

    /**
     * Paints the window in the scheme's background and picks system bar icons that show on it.
     * The window theme follows a light or dark setting only from Android 12 on, once Android has
     * the app's night mode, and never knows the wallpaper's colours, so this sets the background
     * and the icons from the chosen theme. Effects run before the first frame draws.
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
     * `SystemBarStyle.auto`, Android 10 and later paint a translucent scrim over a three-button
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

/** The home screen until the timer screen replaces it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchScreen(
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .screenContent()
                .padding(vertical = TokklTheme.spacing.space6),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Brand(BrandSize.LARGE, Modifier.semantics { heading() })
            Spacer(Modifier.height(TokklTheme.spacing.space4))
            Text(
                text = stringResource(R.string.launch_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TokklTheme.spacing.space4))
            // Side by side where they fit, so at the largest font scale the links take two rows,
            // and a notice at the bottom of the screen still covers none of them.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2, Alignment.CenterHorizontally)) {
                TextButton(onClick = onOpenReports) {
                    Text(stringResource(R.string.reports))
                }
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.settings))
                }
                TextButton(onClick = onOpenLicences) {
                    Text(stringResource(R.string.licences))
                }
            }
        }
    }
}
