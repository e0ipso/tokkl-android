package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** `--radius-md` in styles.css: fields and other small controls. */
private val RadiusMedium = RoundedCornerShape(10.dp)

/** `--radius-lg` in styles.css: cards, dialogs and sheets. */
private val RadiusLarge = RoundedCornerShape(16.dp)

/**
 * Tokkl's corners for Material 3's shape roles (docs/PRD.md, Visual direction). Text fields and
 * menus use extraSmall, cards medium, sheets large and dialogs extraLarge.
 *
 * Shapes set corners only. A card or panel is a [com.github.e0ipso.tokkl.ui.StickerCard], which
 * draws Tokkl's 2dp frame, [FrameWidth] wide in [androidx.compose.material3.ColorScheme.outline],
 * and the hard offset shadow under it. A dialog draws the frame with its own border.
 */
internal val TokklShapes = Shapes(
    extraSmall = RadiusMedium,
    small = RadiusMedium,
    medium = RadiusLarge,
    large = RadiusLarge,
    extraLarge = RadiusLarge,
)

/** `--radius-sm` in styles.css: the corners of the calendar's blocks. */
val SmallCorner: Dp = 6.dp

/** [SmallCorner] on every corner. */
val SmallCornerShape: Shape = RoundedCornerShape(SmallCorner)

/** The frame around a card, a dialog or a sticker, `border-width: 2px` on Tokkl's cards in styles.css. */
val FrameWidth: Dp = 2.dp
