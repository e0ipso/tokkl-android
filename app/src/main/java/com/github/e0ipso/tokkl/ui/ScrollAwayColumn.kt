package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.github.e0ipso.tokkl.ui.theme.TokklSpacing

/**
 * [top] over [body], which takes the rest of the height. On a window of compact height, such as
 * a phone held sideways or upright at the largest font scale ([compactHeightWindow]), [top]
 * would leave [body] little room, so there it scrolls away as soon as [body] scrolls on, before
 * [body] moves, and [body] then has the whole height. It comes back
 * once [body] has scrolled back to its start, as Material's exitUntilCollapsed top app bar does,
 * or when dragged itself. The column scrolls for TalkBack, which reaches everything in [top].
 * Put one in [body] for a second [top] that goes after this one and comes back before it.
 */
@Composable
fun ScrollAwayColumn(
    top: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scrollAway: Boolean = compactHeightWindow(),
    body: @Composable BoxScope.() -> Unit,
) {
    if (!scrollAway) {
        Column(modifier) {
            top()
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = body,
            )
        }
        return
    }
    BoxWithConstraints(modifier) {
        val height = maxHeight
        val scroll = rememberScrollState()
        val topGoesFirst = remember(scroll) { TopGoesFirst(scroll) }
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(topGoesFirst)
                .verticalScroll(scroll),
        ) {
            top()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height),
                content = body,
            )
        }
    }
}

/**
 * Whether the window has a compact height for the text it holds, such as a phone held sideways,
 * by Material's window size classes. The height counts in steps of the font scale, so at the
 * largest font scale, where text takes twice the room, a phone held upright counts too.
 */
@Composable
fun compactHeightWindow(): Boolean {
    val density = LocalDensity.current
    val height = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    return TokklSpacing.isCompactHeight(height / density.fontScale)
}

/**
 * Scrolls [scroll], the column around a scrolling child, on before the child each time the child
 * scrolls on, so the top goes first. Scrolling back is left to the column's own scroll, which
 * takes what the child leaves once it is back at its start.
 */
private class TopGoesFirst(private val scroll: ScrollState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0) Offset(0f, -scroll.dispatchRawDelta(-available.y)) else Offset.Zero
}
