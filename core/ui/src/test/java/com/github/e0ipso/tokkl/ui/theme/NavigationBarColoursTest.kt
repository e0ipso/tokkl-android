package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The bottom bar item colours Tokkl sets over Material 3's. ThemeContrastTest checks their contrast. */
class NavigationBarColoursTest {

    @Test
    fun lightItemsFollowTheDecision() = SectionHues.all.forEach { assertDecision(TokklLightColorScheme, it) }

    @Test
    fun darkItemsFollowTheDecision() = SectionHues.all.forEach { assertDecision(TokklDarkColorScheme, it) }

    private fun assertDecision(s: ColorScheme, hue: SectionHue) {
        val item = navigationBarItemColors(s, hue)
        val indicator = navigationIndicator(s, hue)
        // The indicator is the hue inside an outline in the border colour, in light and dark.
        assertEquals(hue.fill, indicator.fill)
        assertEquals(s.outline, indicator.outline)
        // Tokkl draws the indicator itself, so Material 3's stays transparent.
        assertEquals(Color.Transparent, item.selectedIndicatorColor)
        assertEquals(hue.onFill, item.selectedIconColor)
        assertEquals(s.onSurface, item.selectedTextColor)
        assertEquals(s.onSurfaceVariant, item.unselectedIconColor)
        assertEquals(s.onSurfaceVariant, item.unselectedTextColor)
        // Material 3's default indicator, which the colour scheme maps to --selected.
        assertNotEquals(s.secondaryContainer, item.selectedIndicatorColor)
    }
}
