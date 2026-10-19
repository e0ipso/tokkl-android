package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.e0ipso.tokkl.ui.theme.FrameWidth

// Tokkl's buttons, after .button in styles.css: pills at least Material's minimum interactive
// size tall, so the drawn button is the whole touch target. Material sets their labels in
// labelLarge, which TokklTypography maps to the button-label role, 14sp system font 700.

/** A filled button, on-primary on primary, for a screen's one call to action. */
@Composable
fun TokklButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current),
        enabled = enabled,
        content = content,
    )
}

/**
 * A ghost button: text in onSurface inside Tokkl's 2dp frame, with no fill. For actions that
 * should not stand out, such as the choices of a trust prompt, where both take this variant.
 */
@Composable
fun TokklGhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(FrameWidth, ButtonDefaults.outlinedButtonBorder(enabled).brush),
        content = content,
    )
}

/**
 * A filled button in the danger colours, on-danger on danger, as the web app's `.button-danger`,
 * for the confirm button of a change that cannot be undone, such as deleting a tag. Its label
 * says what it does, so the colour is never the only sign.
 */
@Composable
fun TokklDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        content = content,
    )
}
