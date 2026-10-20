package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bottom bar item colours Tokkl sets over Material 3's. ThemeContrastTest checks their contrast. */
class NavigationBarColoursTest {

    @Test
    fun lightItemsFollowTheDecision() = assertDecision(TokklLightColorScheme)

    @Test
    fun darkItemsFollowTheDecision() = assertDecision(TokklDarkColorScheme)

    @Test
    fun onlyTheActiveLabelIsBold() {
        assertEquals(FontWeight(700), navigationBarLabelWeight(selected = true))
        assertNull(navigationBarLabelWeight(selected = false))
    }

    private fun assertDecision(s: ColorScheme) {
        val item = navigationBarItemColors(s)
        assertEquals(s.primary, item.selectedIndicatorColor)
        assertEquals(s.onPrimary, item.selectedIconColor)
        assertEquals(s.primary, item.selectedTextColor)
        assertEquals(s.onSurfaceVariant, item.unselectedIconColor)
        assertEquals(s.onSurfaceVariant, item.unselectedTextColor)
        // Material 3's default indicator, which the colour scheme maps to --selected.
        assertNotEquals(s.secondaryContainer, item.selectedIndicatorColor)
    }
}
