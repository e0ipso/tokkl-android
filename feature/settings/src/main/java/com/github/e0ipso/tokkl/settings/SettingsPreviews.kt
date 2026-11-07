package com.github.e0ipso.tokkl.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.ui.theme.ThemeMode
import com.github.e0ipso.tokkl.ui.theme.ThemeSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the Settings page, as it opens and while it asks about unsent changes.

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    SettingsPagePreview(SignOutConfirmation.State())
}

@PreviewLightDark
@Composable
private fun SettingsSignOutPreview() {
    SettingsPagePreview(SignOutConfirmation.State(unsentToConfirm = 3))
}

@Composable
private fun SettingsPagePreview(signOut: SignOutConfirmation.State) {
    TokklTheme {
        SettingsContent(
            settings = ThemeSettings(mode = ThemeMode.SYSTEM, dynamicColor = false),
            onModeChange = {},
            onDynamicColorChange = {},
            signOut = signOut,
            onSignOut = {},
            onConfirmSignOut = {},
            onCancelSignOut = {},
            onOpenLicences = {},
        )
    }
}
