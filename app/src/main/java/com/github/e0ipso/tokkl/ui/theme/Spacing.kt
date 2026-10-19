package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokkl's spacing scale, `--space-1` to `--space-8` in styles.css (docs/PRD.md, Visual
 * direction, Spacing and type scale), and the rhythm, margin and width built on it. Every gap,
 * padding and margin on a screen is one of these.
 */
object TokklSpacing {
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 20.dp
    val space6: Dp = 24.dp
    val space7: Dp = 32.dp
    val space8: Dp = 48.dp

    /** Between two groups of content, such as a dialog's message and its details. */
    val betweenGroups: Dp = space6

    /** Between the items of one group, such as a label and its value. */
    val inGroup: Dp = space2

    /** Between a screen's top bar and its first content. */
    val underTopBar: Dp = space4

    /** A screen's margin on both sides on compact widths, phones held upright. */
    val compactMargin: Dp = space4

    /** A screen's margin on both sides on medium and wider widths. */
    val mediumMargin: Dp = space6

    /** The widest a screen's single column gets. Wider windows centre it. */
    val contentMaxWidth: Dp = 600.dp

    /** The screen margin for a screen [width] wide. */
    fun screenMargin(width: Dp): Dp = if (isCompact(width)) compactMargin else mediumMargin

    /** Whether [width] is a compact width, a phone held upright, by Material's window size classes. */
    fun isCompact(width: Dp): Boolean = width < MEDIUM_WIDTH

    /** Whether [height] is a compact height, such as a phone held sideways, by Material's window size classes. */
    fun isCompactHeight(height: Dp): Boolean = height < MEDIUM_HEIGHT

    /** Where Material's window size classes start medium widths. */
    private val MEDIUM_WIDTH = 600.dp

    /** Where Material's window size classes start medium heights. */
    private val MEDIUM_HEIGHT = 480.dp
}

/**
 * Lays out a screen's single column: [TokklSpacing.screenMargin] on both sides, at most
 * [TokklSpacing.contentMaxWidth] wide and centred. The margin follows the width the screen
 * gets. The column takes the whole content width, so a child that fills its width lines up
 * with the margin. Multi-pane layouts are TKA-0056's.
 */
fun Modifier.screenContent(): Modifier = layout { measurable, constraints ->
    check(constraints.hasBoundedWidth) { "screenContent needs a bounded width" }
    val available = constraints.maxWidth
    val margin = TokklSpacing.screenMargin(available.toDp()).roundToPx()
    val width = (available - 2 * margin).coerceIn(0, TokklSpacing.contentMaxWidth.roundToPx())
    val placeable = measurable.measure(
        Constraints(
            minWidth = width,
            maxWidth = width,
            minHeight = constraints.minHeight,
            maxHeight = constraints.maxHeight,
        ),
    )
    layout(available, placeable.height) {
        placeable.placeRelative((available - width) / 2, 0)
    }
}
