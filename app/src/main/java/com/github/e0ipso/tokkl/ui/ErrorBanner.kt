package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
        Row(
            modifier = Modifier.padding(TokklTheme.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        ) {
            Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
