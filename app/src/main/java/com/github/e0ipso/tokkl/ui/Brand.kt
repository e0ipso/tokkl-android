package com.github.e0ipso.tokkl.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The sizes of [Brand], Tokkl's sm, md and lg in Brand.tsx: the icon's size, the wordmark's
 * width and the gap between them. Each has its own images, so none is drawn scaled down from a
 * larger size. res/raw/brand_images_source.txt records where they come from.
 */
enum class BrandSize(
    internal val iconSize: Dp,
    internal val wordmarkWidth: Dp,
    internal val gap: Dp,
    @DrawableRes internal val icon: Int,
    @DrawableRes internal val wordmark: Int,
) {
    /** A header strip. */
    SMALL(24.dp, 84.dp, TokklSpacing.space2, R.drawable.tokkl_icon_sm, R.drawable.tokkl_wordmark_sm),

    /** Beside a column of navigation. */
    MEDIUM(30.dp, 130.dp, TokklSpacing.space3, R.drawable.tokkl_icon_md, R.drawable.tokkl_wordmark_md),

    /** On its own above a screen's content, as on sign-in. */
    LARGE(48.dp, 260.dp, TokklSpacing.space3, R.drawable.tokkl_icon_lg, R.drawable.tokkl_wordmark_lg),
}

/**
 * Tokkl's brand, as Tokkl's Brand.tsx draws it: the app icon beside the owner's graffiti
 * wordmark (docs/PRD.md, Visual direction, Wordmark). TalkBack reads the brand once, as "Tokkl",
 * the wordmark's description; the icon is decorative. The brand is one semantics node, so a
 * [modifier] that makes it a heading names it too.
 *
 * In dark mode a 1dp halo in `--wordmark-halo` traces the wordmark, because the artwork's navy
 * outline matches the background there. Light mode draws none. In a row narrower than the
 * brand, the wordmark shrinks to fit beside the icon.
 */
@Composable
fun Brand(size: BrandSize, modifier: Modifier = Modifier) {
    val wordmark = painterResource(size.wordmark)
    val halo = TokklTheme.extraColors.wordmarkHalo
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(size.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(size.icon),
            contentDescription = null,
            modifier = Modifier.size(size.iconSize),
        )
        Image(
            painter = wordmark,
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .weight(1f, fill = false)
                .width(size.wordmarkWidth)
                .aspectRatio(WORDMARK_ASPECT_RATIO)
                .drawBehind { drawHalo(wordmark, halo) },
        )
    }
}

/**
 * Draws [image]'s silhouette in [colour], moved [HALO_WIDTH] in each of the eight directions,
 * for the image to draw over. Tokkl draws the halo as four chained 1px drop shadows, right,
 * left, down and up. Each shadows the one before, so together they cover the same eight moves.
 */
private fun DrawScope.drawHalo(image: Painter, colour: Color) {
    if (colour.alpha == 0f) return
    val step = HALO_WIDTH.toPx()
    val silhouette = ColorFilter.tint(colour)
    for (dx in HALO_STEPS) {
        for (dy in HALO_STEPS) {
            if (dx == 0 && dy == 0) continue
            translate(dx * step, dy * step) {
                with(image) { draw(size, colorFilter = silhouette) }
            }
        }
    }
}

private val HALO_WIDTH = 1.dp
private val HALO_STEPS = -1..1

/** Width over height of the wordmark images: Tokkl's crop, 1645x709 px. */
private const val WORDMARK_ASPECT_RATIO = 1645f / 709f
