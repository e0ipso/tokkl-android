package com.github.e0ipso.tokkl.projects

import androidx.compose.material3.ColorScheme
import com.github.e0ipso.tokkl.ui.projectMark
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklDarkExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklExtraColors
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightExtraColors
import com.github.e0ipso.tokkl.ui.theme.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A project's dot is its colour mixed with projectMix, as the reports screen draws it. For every
 * colour of [ProjectPalette], the palette the project form offers, the dot reaches the WCAG 3:1
 * for non-text parts on the report card's surface, in light and dark (docs/PRD.md, Visual
 * direction, Contrast and signals). core:ui's ThemeContrastTest checks every other pair of the theme.
 */
class ProjectDotContrastTest {

    @Test
    fun lightProjectDotsMeetWcagAa() = assertDotsPass("light", TokklLightColorScheme, TokklLightExtraColors)

    @Test
    fun darkProjectDotsMeetWcagAa() = assertDotsPass("dark", TokklDarkColorScheme, TokklDarkExtraColors)

    private fun assertDotsPass(scheme: String, s: ColorScheme, x: TokklExtraColors) {
        val failures = ProjectPalette.entries.mapNotNull { colour ->
            val ratio = contrast(projectMark(colour.hex, x.projectMix, s.onSurfaceVariant), s.surface)
            "project dot %s on surface: %.2f:1, needs 3.0:1".format(colour.hex, ratio).takeIf { ratio < 3.0 }
        }
        assertTrue("$scheme scheme project dots below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
