package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.github.e0ipso.tokkl.R

/** The width every Anybody role uses: `font-stretch: 85%` on the display-face rule in styles.css. */
internal const val ANYBODY_WIDTH = 85f

/**
 * Anybody, Tokkl's display face (docs/PRD.md, Visual direction, Type), from the variable font in
 * res/font. res/raw/anybody_font_source.txt records where the file comes from.
 *
 * The variable font's default instance is Thin at 100% width, so each weight a role uses has its
 * own Font entry that sets both axes. Without the 900 entry, a role asking for 900 would get 800.
 */
internal val Anybody = FontFamily(anybody(FontWeight.ExtraBold), anybody(FontWeight.Black))

private fun anybody(weight: FontWeight) = Font(
    R.font.anybody_wdth_wght,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(ANYBODY_WIDTH)),
)

private fun anybodyStyle(size: Int, weight: FontWeight) =
    TextStyle(fontFamily = Anybody, fontWeight = weight, fontSize = size.sp)

// Tokkl's type scale (docs/PRD.md, Visual direction, Spacing and type scale). The Anybody roles
// keep the font's own line height, so no font scale clips them.

/** Page titles, 28sp Anybody 900 with 0.02em tracking. Uppercase the text where it is set. */
internal val PageTitleTextStyle = anybodyStyle(28, FontWeight.Black).copy(letterSpacing = 0.02.em)

/** The running clock, 36sp Anybody 800 with tabular digits, so it does not shift as it counts. */
internal val ClockTextStyle = anybodyStyle(36, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum")

/** Week and report totals, 28sp Anybody 800 with tabular digits. */
internal val TotalTextStyle = anybodyStyle(28, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum")

/** A day's total, 22sp Anybody 800 with tabular digits. */
internal val DayTotalTextStyle = anybodyStyle(22, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum")

/** The header of a day in the history, 17sp Anybody 900. Uppercase the text where it is set. */
internal val DayHeaderTextStyle = anybodyStyle(17, FontWeight.Black)

/** A card's title, 18sp Anybody 800. */
internal val CardTitleTextStyle = anybodyStyle(18, FontWeight.ExtraBold)

/** A section heading inside a screen, 16sp Anybody 800. */
internal val SectionHeadingTextStyle = anybodyStyle(16, FontWeight.ExtraBold)

private val Default = Typography()

/** Small-caps labels, 12sp system font 700 with 0.06em tracking. Uppercase the text where it is set. */
internal val SmallCapsLabelTextStyle = Default.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.06.em)

/** Button labels, 14sp system font 700. */
internal val ButtonLabelTextStyle = Default.labelLarge.copy(fontWeight = FontWeight.Bold)

/** Certificate fingerprints and other codes, at the 14sp body size. */
internal val MonospaceTextStyle = Default.bodyMedium.copy(fontFamily = FontFamily.Monospace)

/**
 * Material 3's roles, sized from Tokkl's scale. Display, headline and title roles are headings
 * and numbers, in Anybody. Body and label roles keep the system font. The scale stops at the
 * clock's 36sp, so every display role is the clock's size.
 */
internal val TokklTypography = Typography(
    displayLarge = ClockTextStyle,
    displayMedium = ClockTextStyle,
    displaySmall = ClockTextStyle,
    headlineLarge = PageTitleTextStyle,
    headlineMedium = anybodyStyle(22, FontWeight.ExtraBold),
    headlineSmall = CardTitleTextStyle,
    titleLarge = CardTitleTextStyle,
    titleMedium = SectionHeadingTextStyle,
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLarge = Default.bodyLarge.copy(fontWeight = FontWeight.Normal),
    bodyMedium = Default.bodyMedium.copy(fontWeight = FontWeight.Normal),
    bodySmall = Default.bodySmall.copy(fontWeight = FontWeight.Normal),
    labelLarge = ButtonLabelTextStyle,
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Bold),
    labelSmall = Default.labelMedium.copy(fontWeight = FontWeight.Bold),
)
