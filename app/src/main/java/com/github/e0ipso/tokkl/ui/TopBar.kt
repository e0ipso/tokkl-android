package com.github.e0ipso.tokkl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/** The top bar's height, from its top to the bottom of its border, at the usual font scale. */
val TopBarHeight: Dp = 64.dp

/** The border under the top bar. */
private val TopBarBorder: Dp = 1.dp

/**
 * The bar at the top of every signed-in page: the page's [title] in the page-title role, which
 * TalkBack reads as a heading, on surface, [TopBarHeight] tall or taller at large font scales.
 * Along its bottom runs a strip of the texture glow, drawn as a gradient under the controls and
 * never behind them, then a 1dp border. A page opened from another, such as Licences, a form or
 * the entry screen, passes [onBack] for the 48dp back button; the pages of the bottom bar have
 * none. The title and the button sit in the screen's margins, as the content under them does.
 *
 * The bar never shows the wordmark, which appears only on sign-in and the launch splash. It looks
 * the same however far the page has scrolled, so it has no motion for reduced motion to stop.
 *
 * As a Scaffold's top bar, which stays put while the content scrolls under it, it draws its
 * surface under the status bar and keeps its title clear of the system bars, [windowInsets].
 * Inside a page's content, pass no insets.
 */
@Composable
fun TokklTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    windowInsets: WindowInsets = ScrollingScreenInsets,
) {
    val surface = MaterialTheme.colorScheme.surface
    val glow = TokklTheme.extraColors.textureGlow
    Column(
        modifier
            .fillMaxWidth()
            .background(surface)
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TopBarHeight - TokklTheme.spacing.space1 - TopBarBorder)
                .screenContent(),
        ) {
            if (onBack != null) {
                // Material's icon button is 40dp inside a 48dp touch area; the back button is 48dp itself.
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .outdent(BackIconInset)
                        .size(BackButtonSize),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Spacer(Modifier.width(TokklTheme.spacing.space1))
            }
            PageTitle(title, Modifier.weight(1f))
        }
        // As the web app's phone header: the glow from the start edge, gone by 70% of the width.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TokklTheme.spacing.space1)
                .background(Brush.horizontalGradient(0f to glow, GLOW_END to surface)),
        )
        HorizontalDivider(thickness = TopBarBorder, color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * The surface a [TokklTopBar] draws under the status bar, alone. A page whose bar scrolls away
 * inside its content uses it as its Scaffold's top bar, so the band under the status bar has the
 * colour it has on the other pages, and the content starts below the status bar.
 */
@Composable
fun TopBarStatusBand(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsTopHeight(WindowInsets.systemBars),
    )
}

/**
 * Places the content [inset] toward the start edge and takes that much less room, so the back
 * button's icon, rather than its 48dp touch area, lines up with the screen's margin.
 */
private fun Modifier.outdent(inset: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = inset.roundToPx()
    layout(placeable.width - shift, placeable.height) { placeable.placeRelative(-shift, 0) }
}

/** The back button, the smallest touch target Android's accessibility guidance allows. */
private val BackButtonSize: Dp = 48.dp

/** The room between the back button's edge and its 24dp icon. */
private val BackIconInset: Dp = 12.dp

/** Where the glow strip's gradient reaches the bar's surface, as a fraction of its width. */
private const val GLOW_END = 0.7f
