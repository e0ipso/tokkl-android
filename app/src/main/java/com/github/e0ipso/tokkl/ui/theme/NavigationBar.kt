package com.github.e0ipso.tokkl.ui.theme

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Material 3's alpha for a disabled item. */
private const val DisabledAlpha = 0.38f

/**
 * Tokkl's colours for a bottom bar item, replacing Material 3's defaults. The active indicator is
 * a primary block behind the icon, with an on-primary icon. Material 3 draws the label below the
 * indicator on the bar, where on-primary text would be unreadable, so the active label takes
 * primary instead. Tokkl's web puts the label inside the block (`.nav-link.active` in
 * styles.css). Inactive icons and labels take on-surface-variant.
 *
 * The scheme's own roles stay as they are: Material 3 would have painted the indicator in
 * secondaryContainer, which Tokkl maps to --state-selected.
 */
internal fun navigationBarItemColors(scheme: ColorScheme) = NavigationBarItemColors(
    selectedIconColor = scheme.onPrimary,
    selectedTextColor = scheme.primary,
    selectedIndicatorColor = scheme.primary,
    unselectedIconColor = scheme.onSurfaceVariant,
    unselectedTextColor = scheme.onSurfaceVariant,
    disabledIconColor = scheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
    disabledTextColor = scheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
)

/**
 * The weight of an item's label. The active one is bold, 700, so that the weight and the
 * indicator's shape mark it without colour. An inactive label keeps the weight it inherits.
 */
internal fun navigationBarLabelWeight(selected: Boolean): FontWeight? =
    if (selected) FontWeight.Bold else null

/** What a screen needs to build a bottom bar item from [NavigationBarItem] in Tokkl's colours. */
object TokklNavigationBarDefaults {
    /** The colours of [NavigationBarItem] in the current theme. */
    @Composable
    @ReadOnlyComposable
    fun itemColors(): NavigationBarItemColors = navigationBarItemColors(MaterialTheme.colorScheme)

    /**
     * The colours of an item on the More sheet, a [NavigationDrawerItem], as `.more-sheet .nav-link`
     * in Tokkl's styles.css. The active item is a primary block with its icon and label inside in
     * on-primary, as in the web app; the others take on-surface-variant on the sheet.
     */
    @Composable
    fun moreItemColors(): NavigationDrawerItemColors {
        val scheme = MaterialTheme.colorScheme
        return NavigationDrawerItemDefaults.colors(
            selectedContainerColor = scheme.primary,
            selectedIconColor = scheme.onPrimary,
            selectedTextColor = scheme.onPrimary,
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    barLabels: List<String> = listOf(label),
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        label = {
            // Every label is measured in the bolder of its two weights, so selecting an item
            // changes no item's size.
            val style = LocalTextStyle.current.let { it.copy(fontWeight = maxOf(it.fontWeight ?: FontWeight.Normal, FontWeight.Bold)) }
            val measurer = rememberTextMeasurer(cacheSize = barLabels.size)
            val fontScale = LocalDensity.current.fontScale
            Text(
                text = label,
                fontWeight = navigationBarLabelWeight(selected),
                maxLines = 1,
                autoSize = remember(barLabels, style, measurer, fontScale) { SharedLabelSize(barLabels, style, measurer, fontScale) },
            )
        },
        alwaysShowLabel = true,
        colors = TokklNavigationBarDefaults.itemColors(),
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
