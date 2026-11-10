package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.core.ui.R
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the shared components, in the light and the dark theme. The texts are
// sample data; the app takes its texts from string resources.

@PreviewLightDark
@Composable
private fun TopBarPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4)) {
            TokklTopBar("Timer")
            TokklTopBar("Licences", onBack = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun ErrorBannerPreview() {
    PreviewSurface { ErrorBanner("The server could not be reached. Check the connection and try again.") }
}

@PreviewLightDark
@Composable
private fun ButtonsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
                TokklButton(onClick = {}) { Text("Save") }
                TokklGhostButton(onClick = {}) { Text("Cancel") }
                TokklDangerButton(onClick = {}) { Text("Delete") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
                TokklIconButton(icon = R.drawable.ic_edit, description = "Edit", onClick = {})
                TokklIconButton(icon = R.drawable.ic_nav_trash, description = "Delete", onClick = {}, danger = true)
                TokklIconButton(icon = R.drawable.ic_play, description = "Start", onClick = {}, enabled = false)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun StepperCardPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
            StepperCard(label = "Work", value = "25", range = 1..180, unit = StepperUnit.MINUTES, onChange = {})
            StepperCard(label = "Work", value = "200", range = 1..180, unit = StepperUnit.MINUTES, onChange = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun OptionRowsPreview() {
    PreviewSurface {
        Column {
            RadioOptionRow(label = "Follow the system", selected = true, onSelect = {})
            RadioOptionRow(label = "Dark", selected = false, onSelect = {})
            CheckboxOptionRow(label = "Use the wallpaper's colours", checked = true, onCheckedChange = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun SceneAndBrandPreview() {
    TokklTheme {
        Scene(Modifier.padding(TokklTheme.spacing.space4)) { Brand(BrandSize.LARGE) }
    }
}

/** [content] on the theme's background, in the screen margins. */
@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    TokklTheme {
        Surface {
            Column(Modifier.padding(TokklTheme.spacing.space4)) { content() }
        }
    }
}
