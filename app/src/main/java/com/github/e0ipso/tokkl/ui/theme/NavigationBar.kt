package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

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
}

/**
 * A [NavigationBarItem] in Tokkl's colours, with the active label in bold. Its label is always
 * shown, so the icon alone never names a destination.
 */
@Composable
fun RowScope.TokklNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label, fontWeight = navigationBarLabelWeight(selected)) },
        alwaysShowLabel = true,
        colors = TokklNavigationBarDefaults.itemColors(),
    )
}
