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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
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
 *
 * With [startScrolledAway], [top] starts scrolled away the first time the column shows, so
 * [body] has the whole height at once. A column in [body] that first shows while this one's
 * [top] is scrolled away starts with its own [top] scrolled away too, so the order holds.
 */
@Composable
fun ScrollAwayColumn(
    top: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scrollAway: Boolean = compactHeightWindow(),
    startScrolledAway: Boolean = false,
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
    val outerTopGone = LocalOuterTopGone.current
    // Read once, as the column first shows, without following the outer column's scroll after.
    val startGone = remember { startScrolledAway || Snapshot.withoutReadObservation { outerTopGone() } }
    BoxWithConstraints(modifier) {
        val height = maxHeight
        // Past the end, which the first layout brings back to the end once it knows the top's height.
        val scroll = rememberScrollState(initial = if (startGone) Int.MAX_VALUE else 0)
        val topGoesFirst = remember(scroll) { TopGoesFirst(scroll) }
        val topGone = remember(scroll) { { scroll.value >= scroll.maxValue } }
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(topGoesFirst)
                .verticalScroll(scroll),
        ) {
            top()
            CompositionLocalProvider(LocalOuterTopGone provides topGone) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height),
                    content = body,
                )
            }
        }
    }
}

/** Whether the top of the scroll-away column around this one has scrolled away. */
private val LocalOuterTopGone = staticCompositionLocalOf<() -> Boolean> { { false } }

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
 * Whether the window itself is under 480dp tall, whatever the font scale, by Material's window
 * size classes: a phone held sideways, and not one held upright at the largest font scale.
 */
@Composable
fun shortWindow(): Boolean {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return TokklSpacing.isCompactHeight(height)
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
