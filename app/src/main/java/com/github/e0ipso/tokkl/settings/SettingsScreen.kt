package com.github.e0ipso.tokkl.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.catalog.ConfirmDialog
import com.github.e0ipso.tokkl.reminder.ReminderPreferences
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.RadioOptionRow
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * The app's settings: the theme, the account with its sign-out ([signOut]), then About, which opens
 * the licences through [onOpenLicences]. Every theme change applies at once. It is a page of the
 * bottom bar, so it has no back button; system Back goes to Timer.
 */
@Composable
fun SettingsScreen(
    model: ThemeSettingsModel,
    signOut: SignOutModel,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
    reminder: ReminderSetting? = null,
) {
    val reminderPreferences = reminder?.state?.collectAsState()?.value ?: ReminderPreferences()
    val settings by model.state.collectAsState()
    val signOutState by signOut.state.collectAsState()
    SettingsContent(
        settings = settings,
        onModeChange = model::onModeChange,
        onDynamicColorChange = model::onDynamicColorChange,
        signOut = signOutState,
        onSignOut = signOut::signOut,
        onConfirmSignOut = signOut::confirm,
        onCancelSignOut = signOut::cancel,
        onOpenLicences = onOpenLicences,
        modifier = modifier,
        reminder = reminderPreferences,
        onReminderChange = { reminder?.change(it) },
    )
}

/** [SettingsScreen] without its models. */
@Composable
internal fun SettingsContent(
    settings: ThemeSettings,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    signOut: SignOutModel.State,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onCancelSignOut: () -> Unit,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
    reminder: ReminderPreferences = ReminderPreferences(),
    onReminderChange: (ReminderPreferences) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(stringResource(R.string.settings)) },
        contentWindowInsets = ScrollingScreenInsets,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
        ) {
            ThemeSection(settings, onModeChange, onDynamicColorChange)
            Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            ReminderSection(reminder, onReminderChange)
            Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            AccountSection(signOut, onSignOut)
            Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            AboutSection(onOpenLicences)
        }
    }
    signOut.unsentToConfirm?.let { unsent ->
        ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            paragraphs = listOf(pluralStringResource(R.plurals.settings_sign_out_unsent, unsent, unsent)),
            confirmLabel = stringResource(R.string.settings_sign_out),
            canConfirm = true,
            problem = null,
            onConfirm = onConfirmSignOut,
            onCancel = onCancelSignOut,
            danger = true,
        )
    }
}

@Composable
private fun ThemeSection(
    settings: ThemeSettings,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_theme),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    // Radio buttons: the selected one differs in shape, not only in colour.
    Column(Modifier.selectableGroup()) {
        for ((mode, label) in MODE_LABELS) {
            RadioOptionRow(
                label = stringResource(label),
                selected = settings.mode == mode,
                onSelect = { onModeChange(mode) },
                textStyle = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Spacer(Modifier.height(TokklTheme.spacing.inGroup))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .toggleable(value = settings.dynamicColor, role = Role.Switch, onValueChange = onDynamicColorChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_dynamic_colour), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.settings_dynamic_colour_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(TokklTheme.spacing.space3))
        Switch(checked = settings.dynamicColor, onCheckedChange = null)
    }
}

/**
 * One row that signs out, clickable across its width, which TalkBack reads as a button. It says
 * that it is signing out until the phone is clear, and shows a failed sign-out below it.
 */
@Composable
private fun AccountSection(signOut: SignOutModel.State, onSignOut: () -> Unit) {
    Text(
        text = stringResource(R.string.settings_account),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(enabled = !signOut.signingOut, role = Role.Button, onClick = onSignOut),
    ) {
        Text(
            text = stringResource(if (signOut.signingOut) R.string.settings_signing_out else R.string.settings_sign_out),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (signOut.failed) {
        Spacer(Modifier.height(TokklTheme.spacing.inGroup))
        ErrorBanner(stringResource(R.string.settings_sign_out_failed), Modifier.fillMaxWidth())
    }
}

/** One row that opens the licences, clickable across its width, which TalkBack reads as a button. */
@Composable
private fun AboutSection(onOpenLicences: () -> Unit) {
    Text(
        text = stringResource(R.string.settings_about),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onOpenLicences),
    ) {
        Text(text = stringResource(R.string.licences), style = MaterialTheme.typography.bodyLarge)
    }
}

private val MODE_LABELS = listOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark,
)
