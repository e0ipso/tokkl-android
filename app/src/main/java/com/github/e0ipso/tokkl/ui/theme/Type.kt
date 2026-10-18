package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.e0ipso.tokkl.R

/**
 * Anybody, Tokkl's display face (docs/PRD.md:76), from the variable font in res/font.
 * res/raw/anybody_font_source.txt records where the file comes from.
 *
 * Tokkl's web app sets it at 85% width and weight 800 (styles.css:306 and :308). The variable
 * font's default instance is Thin at 100% width, so each weight a style uses needs a Font entry
 * that sets both axes.
 */
private val Anybody = FontFamily(
    Font(
        R.font.anybody_wdth_wght,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(85f)),
    ),
)

private val Default = Typography()

private fun TextStyle.inAnybody() = copy(fontFamily = Anybody, fontWeight = FontWeight.ExtraBold)

/** Material 3's type scale with Anybody for display and headline styles; the rest is the system font. */
internal val TokklTypography = Typography(
    displayLarge = Default.displayLarge.inAnybody(),
    displayMedium = Default.displayMedium.inAnybody(),
    displaySmall = Default.displaySmall.inAnybody(),
    headlineLarge = Default.headlineLarge.inAnybody(),
    headlineMedium = Default.headlineMedium.inAnybody(),
    headlineSmall = Default.headlineSmall.inAnybody(),
)

/**
 * The running clock, 2.4rem in the web app (styles.css:562). Tabular digits keep it from
 * shifting sideways as it counts. The line height stays the font's own, so no scale clips it.
 */
internal val ClockTextStyle = TextStyle(
    fontFamily = Anybody,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 36.sp,
    fontFeatureSettings = "tnum",
)

/** Day and report totals, 1.5rem in the web app (styles.css:678), with tabular digits. */
internal val TotalTextStyle = TextStyle(
    fontFamily = Anybody,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp,
    fontFeatureSettings = "tnum",
)
