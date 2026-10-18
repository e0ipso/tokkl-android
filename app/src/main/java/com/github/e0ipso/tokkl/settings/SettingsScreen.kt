package com.github.e0ipso.tokkl.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets

/**
 * The app's settings. So far that is the theme; sign-out (TKA-0038) goes in a section of its
 * own below it. Every change applies at once.
 */
@Composable
fun SettingsScreen(model: ThemeSettingsModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by model.state.collectAsState()
    SettingsContent(
        settings = settings,
        showDynamicColor = dynamicColorAvailable(),
        onModeChange = model::onModeChange,
        onDynamicColorChange = model::onDynamicColorChange,
        onBack = onBack,
        modifier = modifier,
    )
}

/** [SettingsScreen] without its model. [showDynamicColor] is false below Android 12. */
@Composable
internal fun SettingsContent(
    settings: ThemeSettings,
    showDynamicColor: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(16.dp))
            ThemeSection(settings, showDynamicColor, onModeChange, onDynamicColorChange)
        }
    }
}

@Composable
private fun ThemeSection(
    settings: ThemeSettings,
    showDynamicColor: Boolean,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = settings.mode == mode,
                        role = Role.RadioButton,
                        onClick = { onModeChange(mode) },
                    ),
            ) {
                RadioButton(selected = settings.mode == mode, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (showDynamicColor) {
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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
            Spacer(Modifier.width(12.dp))
            Switch(checked = settings.dynamicColor, onCheckedChange = null)
        }
    }
}

private val MODE_LABELS = listOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark,
)
