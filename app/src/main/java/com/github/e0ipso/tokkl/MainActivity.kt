package com.github.e0ipso.tokkl

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.github.e0ipso.tokkl.calendar.CalendarViewModel
import com.github.e0ipso.tokkl.clients.ClientsViewModel
import com.github.e0ipso.tokkl.history.HistoryViewModel
import com.github.e0ipso.tokkl.projects.ProjectsViewModel
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.signin.SignInViewModel
import com.github.e0ipso.tokkl.tags.TagsViewModel
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val signInViewModel: SignInViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val calendarViewModel: CalendarViewModel by viewModels()
    private val projectsViewModel: ProjectsViewModel by viewModels()
    private val clientsViewModel: ClientsViewModel by viewModels()
    private val tagsViewModel: TagsViewModel by viewModels()

    /** Whether a time entry runs, from the phone's cache, which the bottom bar marks on Timer. */
    private val timerRunning: Flow<Boolean> by lazy {
        (application as TokklApplication).cache.running.map { it != null }.distinctUntilChanged()
    }

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
                    history = historyViewModel.model,
                    calendar = calendarViewModel.model,
                    projects = projectsViewModel.model,
                    clients = clientsViewModel.model,
                    tags = tagsViewModel.model,
                    timerView = (application as TokklApplication).timerView,
                    timerRunning = timerRunning,
                )
            }
        }
    }

    /**
     * Coming to the foreground reads the account settings again (docs/PRD.md S7) and refreshes the
     * offline cache with the same `GET /api/v9/me` (O2). After a trip to the background, the
     * calendar loads its weeks from the server again. Nothing refreshes on a timer.
     */
    override fun onStart() {
        super.onStart()
        (application as TokklApplication).accountSettings.refresh()
        calendarViewModel.model.cameToForeground()
    }

    /**
     * Going to the background, which a configuration change is not. The calendar's weeks live in
     * its view model whichever page shows, so the activity tells it rather than the calendar.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) calendarViewModel.model.wentToBackground()
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
