package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalTokklExtraColors = staticCompositionLocalOf { TokklLightExtraColors }

/**
 * Material 3 with Tokkl's palette, light or dark.
 *
 * [dynamicColor] takes the Material roles from the wallpaper instead. It stays off unless the
 * user turns on the opt-in setting (docs/PRD.md, Visual direction, Colour). The primary fill
 * then follows the wallpaper's primary, and the other colours of [TokklTheme.extraColors], such
 * as running and billable, stay Tokkl's.
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
    val tokklColors = if (darkTheme) TokklDarkExtraColors else TokklLightExtraColors
    val extraColors = if (dynamicColor) {
        tokklColors.withPrimaryFill(colorScheme.primary, colorScheme.onPrimary)
    } else {
        tokklColors
    }
    CompositionLocalProvider(LocalTokklExtraColors provides extraColors) {
        MaterialTheme(colorScheme = colorScheme, shapes = TokklShapes, typography = TokklTypography, content = content)
    }
}

object TokklTheme {
    val extraColors: TokklExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTokklExtraColors.current

    val sectionHue: SectionHue
        @Composable
        @ReadOnlyComposable
        get() = LocalSectionHue.current

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

    /** The number on a stepper card, 44sp Anybody 900 with tabular digits. */
    val stepperValueTextStyle: TextStyle get() = StepperValueTextStyle

    /** A day's header in the history, 17sp Anybody 900, uppercase. */
    val dayHeaderTextStyle: TextStyle get() = DayHeaderTextStyle

    /** A section heading inside a screen, 16sp Anybody 800. Material's titleMedium. */
    val sectionHeadingTextStyle: TextStyle get() = SectionHeadingTextStyle

    /** Small-caps labels, 12sp system font 700, uppercase, in onSurfaceVariant. */
    val smallCapsLabelTextStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = SmallCapsLabelTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    /** Certificate fingerprints and other codes, in the system's monospace font. */
    val monospaceTextStyle: TextStyle get() = MonospaceTextStyle
}

/** `--radius-md` in styles.css: fields and other small controls. */
private val RadiusMedium = RoundedCornerShape(10.dp)

/** `--radius-lg` in styles.css: cards, dialogs and sheets. */
private val RadiusLarge = RoundedCornerShape(16.dp)

/**
 * Tokkl's corners for Material 3's shape roles (docs/PRD.md, Visual direction). Text fields and
 * menus use extraSmall, cards medium, sheets large and dialogs extraLarge.
 *
 * Shapes set corners only. A card or panel is a [com.github.e0ipso.tokkl.ui.StickerCard], which
 * draws Tokkl's 2dp frame, [FrameWidth] wide in [androidx.compose.material3.ColorScheme.outline],
 * and the hard offset shadow under it. A dialog draws the frame with its own border.
 */
val TokklShapes = Shapes(
    extraSmall = RadiusMedium,
    small = RadiusMedium,
    medium = RadiusLarge,
    large = RadiusLarge,
    extraLarge = RadiusLarge,
)

/** `--radius-sm` in styles.css: the corners of the calendar's blocks. */
val SmallCorner: Dp = 6.dp

val SmallCornerShape: Shape = RoundedCornerShape(SmallCorner)

/** The frame around a card, a dialog or a sticker, `border-width: 2px` on Tokkl's cards in styles.css. */
val FrameWidth: Dp = 2.dp
