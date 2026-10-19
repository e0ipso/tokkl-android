package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

private val LocalTokklExtraColors = staticCompositionLocalOf { TokklLightExtraColors }

/**
 * Material 3 with Tokkl's palette, light or dark.
 *
 * [dynamicColor] takes the Material roles from the wallpaper instead. It stays off unless the
 * user turns on the opt-in setting (docs/PRD.md, Visual direction, Colour). The running and
 * billable colours stay Tokkl's either way.
 */
@Composable
fun TokklTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TokklDarkColorScheme
        else -> TokklLightColorScheme
    }
    val extraColors = if (darkTheme) TokklDarkExtraColors else TokklLightExtraColors
    CompositionLocalProvider(LocalTokklExtraColors provides extraColors) {
        MaterialTheme(colorScheme = colorScheme, shapes = TokklShapes, typography = TokklTypography, content = content)
    }
}

/** What [TokklTheme] provides beyond [MaterialTheme]. */
object TokklTheme {
    val extraColors: TokklExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTokklExtraColors.current

    /** Tokkl's spacing scale, rhythm, screen margin and content width. */
    val spacing: TokklSpacing get() = TokklSpacing

    // Tokkl's named type roles (docs/PRD.md, Visual direction, Spacing and type scale). Compose's
    // TextStyle cannot change case, so set the uppercase roles' text in uppercase where it is used.

    /** Page titles, 28sp Anybody 900, uppercase. Material's headlineLarge. */
    val pageTitleTextStyle: TextStyle get() = PageTitleTextStyle

    /** The running clock, 36sp Anybody 800 with tabular digits. */
    val clockTextStyle: TextStyle get() = ClockTextStyle

    /** Week and report totals, 28sp Anybody 800 with tabular digits. */
    val totalTextStyle: TextStyle get() = TotalTextStyle

    /** A day's total, 22sp Anybody 800 with tabular digits. */
    val dayTotalTextStyle: TextStyle get() = DayTotalTextStyle

    /** A day's header in the history, 17sp Anybody 900, uppercase. */
    val dayHeaderTextStyle: TextStyle get() = DayHeaderTextStyle

    /** A card's title, 18sp Anybody 800. Material's headlineSmall and titleLarge. */
    val cardTitleTextStyle: TextStyle get() = CardTitleTextStyle

    /** A section heading inside a screen, 16sp Anybody 800. Material's titleMedium. */
    val sectionHeadingTextStyle: TextStyle get() = SectionHeadingTextStyle

    /** Small-caps labels, 12sp system font 700, uppercase, in onSurfaceVariant. */
    val smallCapsLabelTextStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = SmallCapsLabelTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    /** Button labels, 14sp system font 700. Material's labelLarge. */
    val buttonLabelTextStyle: TextStyle get() = ButtonLabelTextStyle

    /** Certificate fingerprints and other codes, in the system's monospace font. */
    val monospaceTextStyle: TextStyle get() = MonospaceTextStyle
}
