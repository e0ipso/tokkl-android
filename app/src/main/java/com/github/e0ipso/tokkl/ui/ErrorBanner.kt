package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * An error [message] in a banner: a warning icon beside danger text on the danger-soft surface,
 * in the card shape with Tokkl's 2dp frame in danger. The icon and the frame mark it as an error
 * along with the colour (docs/PRD.md, Visual direction, Contrast and signals). The message is a
 * polite live region, so TalkBack announces a new one. The icon is decorative.
 */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(FrameWidth, MaterialTheme.colorScheme.error),
    ) {
        val icon = @Composable { Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null) }
        val text = @Composable { textModifier: Modifier ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto),
                modifier = textModifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val padding = Modifier.fillMaxWidth().padding(TokklTheme.spacing.space4)
        // At a large font scale the icon goes above the message, so the message gets the
        // banner's whole width and needs fewer lines.
        if (LocalDensity.current.fontScale >= STACKED_FONT_SCALE) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
                icon()
                text(Modifier.fillMaxWidth())
            }
        } else {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3)) {
                icon()
                text(Modifier.weight(1f))
            }
        }
    }
}

/** The font scale from which the banner's icon goes above its message. */
private const val STACKED_FONT_SCALE = 1.5f
