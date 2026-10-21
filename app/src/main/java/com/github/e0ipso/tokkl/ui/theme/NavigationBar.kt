package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material 3's alpha for a disabled item. */
private const val DisabledAlpha = 0.38f

/**
 * Tokkl's colours for a bottom bar item, replacing Material 3's defaults. The active indicator is
 * a block in its page's [hue] behind the icon, with the icon in the hue's text colour, as
 * `.nav-link.active` in styles.css since tokkl d012228. [navigationIndicator] draws that block,
 * so Material 3's own indicator is transparent. Material 3 draws the label below the indicator on
 * the bar, where Tokkl's web puts it inside the block, so the active label takes on-surface, the
 * ink in light. Inactive icons and labels take on-surface-variant.
 *
 * The scheme's own roles stay as they are: Material 3 would have painted the indicator in
 * secondaryContainer, which Tokkl maps to --selected.
 */
internal fun navigationBarItemColors(scheme: ColorScheme, hue: SectionHue) = NavigationBarItemColors(
    selectedIconColor = hue.onFill,
    selectedTextColor = scheme.onSurface,
    selectedIndicatorColor = Color.Transparent,
    unselectedIconColor = scheme.onSurfaceVariant,
    unselectedTextColor = scheme.onSurfaceVariant,
    disabledIconColor = scheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
    disabledTextColor = scheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
)

/** The colours of [navigationBarItemColors] for a navigation rail item, whose label also sits below its indicator. */
internal fun navigationRailItemColors(scheme: ColorScheme, hue: SectionHue) = navigationBarItemColors(scheme, hue).let { bar ->
    NavigationRailItemColors(
        selectedIconColor = bar.selectedIconColor,
        selectedTextColor = bar.selectedTextColor,
        selectedIndicatorColor = bar.selectedIndicatorColor,
        unselectedIconColor = bar.unselectedIconColor,
        unselectedTextColor = bar.unselectedTextColor,
        disabledIconColor = bar.disabledIconColor,
        disabledTextColor = bar.disabledTextColor,
    )
}

/**
 * The active indicator of a bar or rail item: [fill], its page's hue, inside a [FrameWidth]
 * [outline]. The outline is the border colour in light and dark, as the web's 2px inset outline on
 * `.nav-link.active`: the ink in light, where sky and yellow are under 3:1 on surface, and
 * `--border` in dark.
 */
internal class NavigationIndicator(val fill: Color, val outline: Color)

/** The indicator of an item whose page has [hue]. */
internal fun navigationIndicator(scheme: ColorScheme, hue: SectionHue) = NavigationIndicator(hue.fill, scheme.outline)

/**
 * The weight of an item's label. The active one is bold, 700, so that the weight and the
 * indicator's shape mark it without colour. An inactive label keeps the weight it inherits.
 */
internal fun navigationBarLabelWeight(selected: Boolean): FontWeight? =
    if (selected) FontWeight.Bold else null

/** What a screen needs to build a bar or rail item from [NavigationBarItem] or [NavigationRailItem] in Tokkl's colours. */
object TokklNavigationBarDefaults {
    /** The colours of [NavigationBarItem] for a page with [hue], in the current theme. */
    @Composable
    @ReadOnlyComposable
    fun itemColors(hue: SectionHue): NavigationBarItemColors = navigationBarItemColors(MaterialTheme.colorScheme, hue)

    /** The colours of [NavigationRailItem] for a page with [hue], in the current theme, the bar's colours. */
    @Composable
    @ReadOnlyComposable
    fun railItemColors(hue: SectionHue): NavigationRailItemColors = navigationRailItemColors(MaterialTheme.colorScheme, hue)

    /**
     * The colours of an item on the More sheet, a [NavigationDrawerItem], as `.more-sheet .nav-link`
     * in Tokkl's styles.css. The active item is a block in its page's [hue] with its icon and label
     * inside in the hue's text colour, as in the web app, inside a [FrameWidth] outline in the border
     * colour that the item draws itself; the others take on-surface-variant on the sheet.
     */
    @Composable
    fun moreItemColors(hue: SectionHue): NavigationDrawerItemColors {
        val scheme = MaterialTheme.colorScheme
        return NavigationDrawerItemDefaults.colors(
            selectedContainerColor = hue.fill,
            selectedIconColor = hue.onFill,
            selectedTextColor = hue.onFill,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        )
    }
}

/**
 * A [NavigationBarItem] in Tokkl's colours, with the active label in bold. Its label is always
 * shown, on one line, so the icon alone never names a destination.
 *
 * [barLabels] are the labels of every item in the bar, this one's included. Where the font scale
 * makes one of them wider than its item, as "Settings" is at 200% on a phone, all of them shrink
 * to the size at which the widest fits, so the labels stay one size. They never shrink below
 * their size at the default font scale.
 */
@Composable
fun RowScope.TokklNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    hue: SectionHue,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    barLabels: List<String> = listOf(label),
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { IndicatedIcon(selected, hue, BarIndicatorSize, icon) },
        modifier = modifier,
        enabled = enabled,
        label = { ItemLabel(label, selected, barLabels) },
        alwaysShowLabel = true,
        colors = TokklNavigationBarDefaults.itemColors(hue),
    )
}

/**
 * A [NavigationRailItem] in Tokkl's colours, with the active label in bold, as
 * [TokklNavigationBarItem] draws a bar item. Its label is always shown, on one line. [railLabels]
 * are the labels of every item in the rail. The rail grows as wide as its widest label needs, so a
 * label shrinks only where the window leaves the rail too little width.
 */
@Composable
fun TokklNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    hue: SectionHue,
    modifier: Modifier = Modifier,
    railLabels: List<String> = listOf(label),
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { IndicatedIcon(selected, hue, RailIndicatorSize, icon) },
        modifier = modifier,
        label = { ItemLabel(label, selected, railLabels) },
        alwaysShowLabel = true,
        colors = TokklNavigationBarDefaults.railItemColors(hue),
    )
}

/**
 * The [icon] of a bar or rail item, over its [NavigationIndicator] while [selected]: a pill of
 * [indicatorSize], Material 3's indicator size, centred on the icon as Material 3 centres its
 * own. It draws outside the icon's bounds, where Material 3 draws its indicator.
 */
@Composable
private fun IndicatedIcon(selected: Boolean, hue: SectionHue, indicatorSize: DpSize, icon: @Composable () -> Unit) {
    val indicator = navigationIndicator(MaterialTheme.colorScheme, hue)
    Box(
        Modifier.drawBehind {
            if (!selected) return@drawBehind
            val size = indicatorSize.toSize()
            val stroke = FrameWidth.toPx()
            val topLeft = Offset((this.size.width - size.width) / 2, (this.size.height - size.height) / 2)
            drawRoundRect(indicator.fill, topLeft, size, CornerRadius(size.height / 2))
            // Inset, as the web's inset box-shadow: the outline lies inside the fill's edge.
            val inset = stroke / 2
            drawRoundRect(
                color = indicator.outline,
                topLeft = topLeft + Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius((size.height - stroke) / 2),
                style = Stroke(stroke),
            )
        },
    ) { icon() }
}

/** Material 3's active indicator on a bar item, NavigationBarTokens' 64 by 32dp. */
private val BarIndicatorSize = DpSize(64.dp, 32.dp)

/** Material 3's active indicator on a rail item, NavigationRailTokens' 56 by 32dp. */
private val RailIndicatorSize = DpSize(56.dp, 32.dp)

/**
 * The [label] of a bar or rail item, bold while [selected], on one line, at the size at which all
 * of [labels] fit ([SharedLabelSize]).
 */
@Composable
private fun ItemLabel(label: String, selected: Boolean, labels: List<String>) {
    // Every label is measured in the bolder of its two weights, so selecting an item
    // changes no item's size.
    val style = LocalTextStyle.current.let { it.copy(fontWeight = maxOf(it.fontWeight ?: FontWeight.Normal, FontWeight.Bold)) }
    val measurer = rememberTextMeasurer(cacheSize = labels.size)
    val fontScale = LocalDensity.current.fontScale
    Text(
        text = label,
        fontWeight = navigationBarLabelWeight(selected),
        maxLines = 1,
        autoSize = remember(labels, style, measurer, fontScale) { SharedLabelSize(labels, style, measurer, fontScale) },
    )
}

/**
 * One size for all of a bar's [labels]: the largest, from the size of the label role's [style]
 * down to that size undone by [fontScale], at which each label fits on one line in the width an
 * item gives it. The items share one width, so every item works out the same size. [measurer]
 * lays the other labels out, since the layout scope lays out only the item's own text.
 */
private class SharedLabelSize(
    private val labels: List<String>,
    private val style: TextStyle,
    private val measurer: TextMeasurer,
    private val fontScale: Float,
) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val fontSize = style.fontSize
        fun allFit(size: TextUnit) = labels.all { label ->
            fits(measurer.measure(label, style.copy(fontSize = size), maxLines = 1, constraints = constraints, density = this))
        }
        if (allFit(fontSize)) return fontSize
        // Smaller sizes in steps of STEP, from the smallest (step 0) up. Fitting only gets easier
        // as the size goes down, so a binary search finds the largest step that fits.
        val smallest = fontSize.value / fontScale.coerceAtLeast(1f)
        fun size(step: Int) = (smallest + step * STEP).sp
        var low = 0
        var high = ((fontSize.value - smallest) / STEP).toInt() - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (allFit(size(middle))) low = middle else high = middle - 1
        }
        return size(low)
    }

    private fun fits(layout: TextLayoutResult): Boolean = !layout.didOverflowWidth && !layout.didOverflowHeight

    override fun equals(other: Any?): Boolean =
        other is SharedLabelSize && labels == other.labels && style == other.style &&
            measurer === other.measurer && fontScale == other.fontScale

    override fun hashCode(): Int = ((labels.hashCode() * 31 + style.hashCode()) * 31 + measurer.hashCode()) * 31 + fontScale.hashCode()

    private companion object {
        /** In sp, as fine as TextAutoSize.StepBased's default step. */
        const val STEP = 0.25f
    }
}
