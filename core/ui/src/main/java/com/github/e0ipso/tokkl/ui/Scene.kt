package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.ui.theme.TokklExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import kotlin.math.sqrt

/**
 * Tokkl's scene behind [content], as styles.css draws it behind the auth page: a sunset glow from
 * the top left corner and the swell from the bottom right, over diagonal stripes on the
 * background. Each glow is an ellipse 70% of the scene's width and 45% of its height around its
 * corner, fading out towards its edge. Only the brand sits on the scene. Inputs, text and data sit
 * on a surface card above it (docs/PRD.md, Visual direction, Texture).
 *
 * Where the layers overlap, every channel stays between the background and the scene colours, so
 * text and the wordmark halo keep at least the contrast styles.css gives for the scene overlap.
 */
@Composable
fun Scene(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val colours = TokklTheme.extraColors
    Box(modifier.drawBehind { drawScene(background, colours) }, content = content)
}

private fun DrawScope.drawScene(background: Color, colours: TokklExtraColors) {
    drawRect(background)
    clipRect {
        drawStripes(colours.sceneStripe)
        drawGlow(colours.sceneSwell, Offset(size.width, size.height))
        drawGlow(colours.sceneGlow, Offset.Zero)
    }
}

/**
 * `repeating-linear-gradient(-45deg, transparent 0 22px, stripe 22px 26px)`: stripes
 * [STRIPE_WIDTH] wide every [STRIPE_PERIOD]. The gradient runs up and to the left, so the stripes
 * run up and to the right.
 */
private fun DrawScope.drawStripes(colour: Color) {
    val clear = colour.copy(alpha = 0f)
    val stripeStart = (STRIPE_PERIOD - STRIPE_WIDTH) / STRIPE_PERIOD
    val step = STRIPE_PERIOD.toPx() / sqrt(2f)
    drawRect(
        Brush.linearGradient(
            0f to clear,
            stripeStart to clear,
            stripeStart to colour,
            1f to colour,
            start = Offset.Zero,
            end = Offset(-step, -step),
            tileMode = TileMode.Repeated,
        ),
    )
}

/**
 * `radial-gradient(ellipse 70% 45% at <corner>, colour, transparent)`. A radial brush draws a
 * circle, so the circle is as tall as the ellipse and stretched to its width. The colour fades
 * with its own RGB, as CSS fades to transparent, not towards transparent black.
 */
private fun DrawScope.drawGlow(colour: Color, centre: Offset) {
    val radiusX = size.width * GLOW_WIDTH
    val radiusY = size.height * GLOW_HEIGHT
    if (radiusX <= 0f || radiusY <= 0f) return
    val brush = Brush.radialGradient(listOf(colour, colour.copy(alpha = 0f)), center = centre, radius = radiusY)
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = centre) {
        drawCircle(brush, radius = radiusY, center = centre)
    }
}

/** A glow's radii, as fractions of the scene's width and height. */
private const val GLOW_WIDTH = 0.70f
private const val GLOW_HEIGHT = 0.45f

/** The stripes' period and width, 26px and 4px in styles.css. */
private val STRIPE_PERIOD = 26.dp
private val STRIPE_WIDTH = 4.dp
