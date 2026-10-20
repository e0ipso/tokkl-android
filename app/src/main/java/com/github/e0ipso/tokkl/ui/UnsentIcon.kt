package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import com.github.e0ipso.tokkl.R

/**
 * The unsent icon, an arrow up to a line, as tall as a line of [style] and in the text colour.
 * It goes beside words that say the same, such as the history's NOT SENT, so it has nothing for
 * TalkBack and colour is never the only sign.
 */
@Composable
fun UnsentIcon(style: TextStyle, modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_unsent),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(with(LocalDensity.current) { style.lineHeight.toDp() }),
    )
}
