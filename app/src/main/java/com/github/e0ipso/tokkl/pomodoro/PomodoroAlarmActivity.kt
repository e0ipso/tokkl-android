package com.github.e0ipso.tokkl.pomodoro

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Separate unexported task: no account page is exposed above the keyguard. */
class PomodoroAlarmActivity : ComponentActivity() {
    private val holder by lazy { ViewModelProvider(this)[PomodoroAlarmViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.enableEdgeToEdge(window)
        holder.model.open(intent?.phaseKey())
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    holder.model.state.collect { if (it.closed) finish() }
                }
                launch {
                    // Ring changes follow the store's durable writes. Re-read the row on each
                    // change and resume; the display clock alone ticks locally each second.
                    (application as TokklApplication).pomodoroRinging.state
                        .map { it.ring?.phase }.distinctUntilChanged()
                        .collect { holder.model.refresh() }
                }
            }
        }
        val themes = (application as TokklApplication).themeSettings
        setContent {
            val theme by themes.state.collectAsState()
            val state by holder.model.state.collectAsState()
            val dark = theme.mode.isDark(isSystemInDarkTheme())
            TokklTheme(darkTheme = dark, dynamicColor = theme.dynamicColor) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                DisposableEffect(dark, background) {
                    window.decorView.setBackgroundColor(background)
                    window.isNavigationBarContrastEnforced = false
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                    onDispose { }
                }
                PomodoroAlarmContent(state, onNext = { holder.model.next() }, onEnd = { holder.model.end() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        holder.model.open(intent.phaseKey())
    }
}

/** Retains an in-flight guarded command through rotation without replaying it. */
class PomodoroAlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TokklApplication
    internal val model = PomodoroAlarmModel(
        current = app.pomodoroSessions::current,
        reached = { withContext(Dispatchers.IO) { app.pomodoroSessions.reachedPhaseEnd() } },
        next = { app.pomodoroSessions.nextPhaseIfCurrent(it) },
        end = { app.pomodoroSessions.endIfCurrent(it) },
        scope = viewModelScope,
    )
}
