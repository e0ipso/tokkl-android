package com.github.e0ipso.tokkl.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The sizes of [Brand], Tokkl's sm, md and lg in Brand.tsx, as the wordmark's width. Each has its
 * own images, so none is drawn scaled down from a larger size. The image names are aliases:
 * brand_images.xml in res/values and in each density's values folder points them at one file per
 * density. res/raw/brand_images_source.txt records where the files come from.
 */
enum class BrandSize(
    internal val wordmarkWidth: Dp,
    @DrawableRes internal val wordmark: Int,
) {
    /** Tokkl's sm. No screen shows it: the wordmark never sits in a page header or a top bar. */
    SMALL(84.dp, R.drawable.tokkl_wordmark_sm),

    /** Tokkl's md. No screen shows it either. */
    MEDIUM(130.dp, R.drawable.tokkl_wordmark_md),

    /** On its own above a screen's content, as on sign-in. */
    LARGE(260.dp, R.drawable.tokkl_wordmark_lg),
}

/**
 * Tokkl's brand, the owner's graffiti wordmark alone, as Tokkl's Brand.tsx draws it (docs/PRD.md,
 * Visual direction, Wordmark). Its "o" is a clock, a letter of the wordmark. The logo is only the
 * launcher icon and never sits beside the wordmark. TalkBack reads the brand once, as "Tokkl",
 * the wordmark's description. The brand is one semantics node, so a [modifier] that makes it a
 * heading names it too.
 *
 * In dark mode a 1dp halo in `--wordmark-halo` traces the wordmark, because the artwork's navy
 * outline matches the background there. Light mode draws none. In a space narrower than the
 * wordmark, the wordmark shrinks to fit and keeps its shape.
 */
@Composable
fun Brand(size: BrandSize, modifier: Modifier = Modifier) {
    val wordmark = painterResource(size.wordmark)
    val halo = TokklTheme.extraColors.wordmarkHalo
    Box(modifier.semantics(mergeDescendants = true) {}) {
        Image(
            painter = wordmark,
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
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

/** Width over height of the wordmark images: the crop, 2059x769 px. */
private const val WORDMARK_ASPECT_RATIO = 2059f / 769f
