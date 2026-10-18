package com.github.e0ipso.tokkl

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val signInViewModel: SignInViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeSettings = (application as TokklApplication).themeSettings
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
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            onDispose {}
        }
    }

    private companion object {
        // enableEdgeToEdge's own navigation bar scrims, which only Android 9 and earlier draw.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

/** The home screen until the timer screen replaces it. */
@Composable
fun LaunchScreen(onOpenSettings: () -> Unit, onOpenLicences: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.launch_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings))
            }
            TextButton(onClick = onOpenLicences) {
                Text(stringResource(R.string.licences))
            }
        }
    }
}
