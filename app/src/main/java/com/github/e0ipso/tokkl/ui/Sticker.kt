package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.ui.theme.FrameWidth
import com.github.e0ipso.tokkl.ui.theme.SectionHue

/** How far a sticker's shadow sits right of and below it, the 3px of `--shadow` in styles.css. */
val StickerShadowOffset: Dp = 3.dp

/**
 * A card or panel drawn as a sticker, as Tokkl's styles.css draws `.card` and `.surface` since
 * tokkl d012228 (TT-0299): [color] in [shape], the large corners unless given, inside Tokkl's 2dp
 * frame, with a hard shadow [StickerShadowOffset] to the right and below and no blur. Frame and
 * shadow take the outline role, the ink in light and `--border`, #8C86C0, in dark, so the
 * outline's contrast rows in ThemeContrastTest give both.
 *
 * The shadow draws outside the card's bounds, so leave it room: a card at the end of a scrolling
 * column needs [StickerShadowOffset] of padding under it, or the scroll's clip cuts the shadow.
 */
@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    val edge = MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier.stickerShadow(shape, edge),
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = BorderStroke(FrameWidth, edge),
        content = content,
    )
}

/** The hard offset shadow of a sticker in [shape], in [color], drawn behind what follows. */
private fun Modifier.stickerShadow(shape: Shape, color: Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val offset = StickerShadowOffset.toPx()
    translate(offset, offset) { drawOutline(outline, color) }
}

/**
 * A day sticker's fill, as `.day-header` and `.calendar-day-head` in styles.css: [hue]'s fill in
 * [shape], inside a [FrameWidth] [outline], which is `--sticker-outline`,
 * [com.github.e0ipso.tokkl.ui.theme.TokklExtraColors.stickerOutline]: the ink in light, where sky
 * and yellow fall under 3:1 on surface, and none in dark, where every hue reaches it. Set the text
 * on it in [SectionHue.onFill].
 */
fun Modifier.daySticker(hue: SectionHue, shape: Shape, outline: Color): Modifier {
    val filled = background(hue.fill, shape)
    return if (outline.alpha > 0f) filled.border(FrameWidth, outline, shape) else filled
}
