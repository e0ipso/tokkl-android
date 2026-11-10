package com.github.e0ipso.tokkl.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.platform.whileShown
import com.github.e0ipso.tokkl.preferences.PomodoroDefaults
import com.github.e0ipso.tokkl.preferences.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.preferences.ReminderPreferences
import com.github.e0ipso.tokkl.preferences.ReminderSetting
import com.github.e0ipso.tokkl.ui.theme.ThemeMode
import com.github.e0ipso.tokkl.ui.theme.ThemeSettings
import com.github.e0ipso.tokkl.ui.theme.ThemeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class SettingsState(
    val theme: ThemeSettings,
    val pomodoro: PomodoroDefaults = PomodoroDefaults.DEFAULT,
    val pomodoroSharedWithWeb: Boolean = false,
    val reminder: ReminderPreferences = ReminderPreferences(),
    val signOut: SignOutConfirmation.State = SignOutConfirmation.State(),
)

/**
 * Each setting keeps itself and applies a change at once; the page only shows them and passes the
 * user's changes on. The server keeps the Pomodoro defaults where its [capabilities] have Tokkl's
 * preferences, the same key that keeps favorites.
 */
class SettingsViewModel(
    private val theme: ThemeSettingsRepository,
    private val pomodoro: PomodoroDefaultsSetting,
    private val reminder: ReminderSetting,
    private val signOut: SignOutConfirmation,
    capabilities: StateFlow<Capabilities>,
    scope: CoroutineScope,
) : ViewModel(scope) {

    val state: StateFlow<SettingsState> = combine(
        theme.state,
        pomodoro.defaults,
        capabilities,
        reminder.state,
        signOut.state,
    ) { themeSettings, defaults, flags, reminderPreferences, signingOut ->
        SettingsState(themeSettings, defaults, flags.favorites, reminderPreferences, signingOut)
    }.stateIn(
        viewModelScope,
        whileShown,
        SettingsState(
            theme = theme.state.value,
            pomodoro = pomodoro.defaults.value,
            pomodoroSharedWithWeb = capabilities.value.favorites,
            reminder = reminder.state.value,
            signOut = signOut.state.value,
        ),
    )

    fun changeMode(mode: ThemeMode) = theme.onModeChange(mode)

    fun changeDynamicColor(on: Boolean) = theme.onDynamicColorChange(on)

    fun changePomodoro(defaults: PomodoroDefaults) {
        pomodoro.change(defaults)
    }

    fun changeReminder(preferences: ReminderPreferences) {
        reminder.change(preferences)
    }

    fun signOut() = signOut.signOut()

    fun confirmSignOut() = signOut.confirm()

    fun cancelSignOut() = signOut.cancel()
}
