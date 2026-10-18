package com.github.e0ipso.tokkl.ui.theme

import android.os.Build
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
 * [dynamicColor] takes the Material roles from the wallpaper instead, on Android 12 and later.
 * It stays off unless the user turns on the opt-in setting (docs/PRD.md, Visual direction,
 * Colour). The running and billable colours stay Tokkl's either way.
 */
@Composable
fun TokklTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TokklDarkColorScheme
        else -> TokklLightColorScheme
    }
    val extraColors = if (darkTheme) TokklDarkExtraColors else TokklLightExtraColors
    CompositionLocalProvider(LocalTokklExtraColors provides extraColors) {
        MaterialTheme(colorScheme = colorScheme, typography = TokklTypography, content = content)
    }
}

/** What [TokklTheme] provides beyond [MaterialTheme]. */
object TokklTheme {
    val extraColors: TokklExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTokklExtraColors.current

    /** The running clock, in Anybody with tabular digits. */
    val clockTextStyle: TextStyle get() = ClockTextStyle

    /** Day and report totals, in Anybody with tabular digits. */
    val totalTextStyle: TextStyle get() = TotalTextStyle
}
