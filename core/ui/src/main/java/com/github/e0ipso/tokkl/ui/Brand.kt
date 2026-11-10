package com.github.e0ipso.tokkl.ui

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
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.core.ui.R
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * Tokkl's brand, the owner's graffiti wordmark alone, as Tokkl's Brand.tsx draws it (docs/PRD.md,
 * Visual direction, Wordmark). Its "o" is a clock, a letter of the wordmark. The logo appears as
 * the launcher icon and before page titles, and never sits beside the wordmark.
 * TalkBack reads the brand once, as "Tokkl",
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
