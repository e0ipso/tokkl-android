package com.github.e0ipso.tokkl.navhost

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.github.e0ipso.tokkl.ui.theme.SectionHue
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar item colours Tokkl sets over Material 3's, and their WCAG 2 contrast on the bar:
 * 4.5:1 for the labels and 3:1 for the icons, the indicator's outline and the running dot.
 */
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

    @Test
    fun lightItemsMeetWcagAa() = assertContrast("light", TokklLightColorScheme)

    @Test
    fun darkItemsMeetWcagAa() = assertContrast("dark", TokklDarkColorScheme)

    /**
     * For the hue of every tab: the icon and the running dot sit on the hue's indicator, whose
     * outline draws its edge on the bar, which is surfaceContainer, and the label sits below it,
     * on the bar. Inactive items sit on the bar.
     */
    private fun assertContrast(scheme: String, s: ColorScheme) {
        val bar = s.surfaceContainer
        val active = SectionHues.all.withIndex().flatMap { (index, hue) ->
            val item = navigationBarItemColors(s, hue)
            val indicator = navigationIndicator(s, hue)
            listOf(
                Triple("active nav icon on indicator $index", item.selectedIconColor to indicator.fill, NON_TEXT),
                Triple("running dot on indicator $index", hue.onFill to indicator.fill, NON_TEXT),
                Triple("indicator $index outline on bar", indicator.outline to bar, NON_TEXT),
                Triple("active nav label on bar, hue $index", item.selectedTextColor to bar, TEXT),
            )
        }
        val item = navigationBarItemColors(s, SectionHues.timer)
        val pairs = active + listOf(
            Triple("inactive nav icon on bar", item.unselectedIconColor to bar, NON_TEXT),
            Triple("inactive nav label on bar", item.unselectedTextColor to bar, TEXT),
        )
        val failures = pairs.mapNotNull { (name, colours, minimum) ->
            val ratio = contrast(colours.first, colours.second)
            "%s: %.2f:1, needs %.1f:1".format(name, ratio, minimum).takeIf { ratio < minimum }
        }
        assertTrue("$scheme bar items below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
