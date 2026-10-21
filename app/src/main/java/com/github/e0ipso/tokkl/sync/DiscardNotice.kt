package com.github.e0ipso.tokkl.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * Says that [count] queued changes were discarded because the server no longer had their entries
 * (docs/PRD.md O6), until the user taps OK, which calls [onDismiss]. TalkBack reads it when it
 * shows. It sits where the trust notice does, at the bottom of a signed-in screen.
 */
@Composable
fun DiscardNotice(count: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(TokklTheme.spacing.space4)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(FrameWidth, MaterialTheme.colorScheme.outline),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                start = TokklTheme.spacing.space4,
                top = TokklTheme.spacing.space4,
                end = TokklTheme.spacing.space2,
                // The text button brings its own padding.
                bottom = TokklTheme.spacing.space2,
            ),
        ) {
            Text(
                text = LocalResources.current.getQuantityString(R.plurals.sync_discarded, count, count),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(end = TokklTheme.spacing.space2)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.sync_discarded_dismiss))
            }
        }
    }
}
