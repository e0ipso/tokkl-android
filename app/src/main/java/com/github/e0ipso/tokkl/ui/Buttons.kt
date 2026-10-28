package com.github.e0ipso.tokkl.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** A secondary action with a 48dp target and a label for TalkBack and long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokklIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier.sizeIn(minWidth = TokklTheme.spacing.space8, minHeight = TokklTheme.spacing.space8),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(TokklTheme.spacing.space6))
        }
    }
}

// Tokkl's buttons, after .button in styles.css: pills at least Material's minimum interactive
// size tall, so the drawn button is the whole touch target. Material sets their labels in
// labelLarge, which TokklTypography maps to the button-label role, 14sp system font 700.

/**
 * A filled button, the web app's `.button-primary`: on-primary on the primary fill, for a
 * screen's one call to action.
 */
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
        colors = ButtonDefaults.buttonColors(
            containerColor = TokklTheme.extraColors.primaryFill,
            contentColor = TokklTheme.extraColors.onPrimaryFill,
        ),
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
