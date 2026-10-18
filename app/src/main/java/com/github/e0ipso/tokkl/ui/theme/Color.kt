package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokkl's palette. Every value comes from the first :root block of
// ../tokkl/packages/web/src/styles.css, the source of truth for the app's colours
// (docs/PRD.md:75). styles.css writes each token once as light-dark(light, dark), and the
// comment on each value names its line there. res/values/colors.xml and
// res/values-night/colors.xml repeat --bg for the window behind Compose; change them with it.

/** One side, light or dark, of the styles.css colour tokens the app uses. */
private class Tokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accentPink: Color,
    val accentLime: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    val backdrop: Color,
    val stateRunning: Color,
    val stateSelected: Color,
    val stateBillable: Color,
)

private val Light = Tokens(
    bg = Color(0xFFF4E7CF), // styles.css:161 --bg
    surface = Color(0xFFFFFAF1), // styles.css:162 --surface
    surface2 = Color(0xFFECDCBF), // styles.css:163 --surface-2
    text = Color(0xFF1C1A2B), // styles.css:164 --text
    muted = Color(0xFF5F5242), // styles.css:165 --muted
    border = Color(0xFF8F7B5F), // styles.css:166 --border
    primary = Color(0xFFA8380A), // styles.css:167 --primary
    onPrimary = Color(0xFFFFFFFF), // styles.css:168 --on-primary
    secondary = Color(0xFF00727C), // styles.css:169 --secondary
    accentPink = Color(0xFFD81B72), // styles.css:170 --accent-pink
    accentLime = Color(0xFF4F7A00), // styles.css:171 --accent-lime
    danger = Color(0xFFB3261E), // styles.css:172 --danger
    onDanger = Color(0xFFFFFFFF), // styles.css:173 --on-danger
    dangerSoft = Color(0xFFFBE0D9), // styles.css:174 --danger-soft
    backdrop = Color(0xFF1C1A2B), // styles.css:178 --backdrop, rgb(28 26 43) without its 0.45 alpha
    stateRunning = Color(0xFFD81B72), // styles.css:187 --state-running, which is --accent-pink (:170)
    stateSelected = Color(0xFFCDEEEA), // styles.css:188 --state-selected
    stateBillable = Color(0xFF4A7C00), // styles.css:189 --state-billable
)

private val Dark = Tokens(
    bg = Color(0xFF0B1026), // styles.css:161 --bg
    surface = Color(0xFF141A36), // styles.css:162 --surface
    surface2 = Color(0xFF1D2547), // styles.css:163 --surface-2
    text = Color(0xFFF3F1FF), // styles.css:164 --text
    muted = Color(0xFFA9B0D6), // styles.css:165 --muted
    border = Color(0xFF6A76A8), // styles.css:166 --border
    primary = Color(0xFFFF8C42), // styles.css:167 --primary
    onPrimary = Color(0xFF0B1026), // styles.css:168 --on-primary
    secondary = Color(0xFF2EE6E6), // styles.css:169 --secondary
    accentPink = Color(0xFFFF4FA8), // styles.css:170 --accent-pink
    accentLime = Color(0xFFB6FF3B), // styles.css:171 --accent-lime
    danger = Color(0xFFFF6B6B), // styles.css:172 --danger
    onDanger = Color(0xFF0B1026), // styles.css:173 --on-danger
    dangerSoft = Color(0xFF3D1626), // styles.css:174 --danger-soft
    backdrop = Color(0xFF030512), // styles.css:178 --backdrop, rgb(3 5 18) without its 0.6 alpha
    stateRunning = Color(0xFFFF4FA8), // styles.css:187 --state-running, which is --accent-pink (:170)
    stateSelected = Color(0xFF1D3B52), // styles.css:188 --state-selected
    stateBillable = Color(0xFFB6FF3B), // styles.css:189 --state-billable
)

/**
 * Maps the Material 3 roles onto the tokens of one scheme. A role with no token of its own takes
 * the nearest one, and its comment says which.
 *
 * [other] is the opposite scheme, for inversePrimary. [surfaceBright] and [surfaceDim] are the
 * lightest and darkest surface tokens, which differ between light and dark.
 */
private fun colorScheme(t: Tokens, other: Tokens, surfaceBright: Color, surfaceDim: Color) =
    ColorScheme(
        primary = t.primary,
        onPrimary = t.onPrimary,
        // --state-selected is the web's fill for a selected control, with --text on it.
        primaryContainer = t.stateSelected,
        onPrimaryContainer = t.text,
        // The primary of the opposite scheme, as Material 3 defines it.
        inversePrimary = other.primary,
        secondary = t.secondary,
        // styles.css has no --on-secondary; --on-primary is its only colour for text on a fill.
        onSecondary = t.onPrimary,
        secondaryContainer = t.stateSelected,
        onSecondaryContainer = t.text,
        // The pink accent. It is also the running state, so it never marks a state alone.
        tertiary = t.accentPink,
        onTertiary = t.onPrimary,
        tertiaryContainer = t.stateSelected,
        onTertiaryContainer = t.text,
        background = t.bg,
        onBackground = t.text,
        surface = t.surface,
        onSurface = t.text,
        surfaceVariant = t.surface2,
        onSurfaceVariant = t.muted,
        // Surface itself, so tonal elevation adds no tint and every surface stays a tested colour.
        surfaceTint = t.surface,
        inverseSurface = t.text,
        inverseOnSurface = t.surface,
        error = t.danger,
        onError = t.onDanger,
        errorContainer = t.dangerSoft,
        onErrorContainer = t.danger,
        outline = t.border,
        outlineVariant = t.border,
        scrim = t.backdrop,
        surfaceBright = surfaceBright,
        surfaceContainer = t.surface,
        surfaceContainerHigh = t.surface,
        surfaceContainerHighest = t.surface2,
        surfaceContainerLow = t.surface,
        surfaceContainerLowest = t.surface,
        surfaceDim = surfaceDim,
        // Fixed roles stay the same in light and dark, so both schemes take the light
        // --state-selected with the light --text on it.
        primaryFixed = Light.stateSelected,
        primaryFixedDim = Light.stateSelected,
        onPrimaryFixed = Light.text,
        onPrimaryFixedVariant = Light.text,
        secondaryFixed = Light.stateSelected,
        secondaryFixedDim = Light.stateSelected,
        onSecondaryFixed = Light.text,
        onSecondaryFixedVariant = Light.text,
        tertiaryFixed = Light.stateSelected,
        tertiaryFixedDim = Light.stateSelected,
        onTertiaryFixed = Light.text,
        onTertiaryFixedVariant = Light.text,
    )

internal val TokklLightColorScheme =
    colorScheme(Light, other = Dark, surfaceBright = Light.surface, surfaceDim = Light.surface2)

internal val TokklDarkColorScheme =
    colorScheme(Dark, other = Light, surfaceBright = Dark.surface2, surfaceDim = Dark.bg)

/**
 * Tokkl colours with no Material 3 role. Screens show [running] and [billable] together with an
 * icon or a label, never as the only sign of the state.
 */
@Immutable
class TokklExtraColors(
    /** A running time entry. Readable as text on surface, and as a mark on any surface. */
    val running: Color,
    /** A billable time entry. A mark only, never text. */
    val billable: Color,
    /** The lime accent. A mark only, never text. */
    val accentLime: Color,
)

internal val TokklLightExtraColors = TokklExtraColors(
    running = Light.stateRunning,
    billable = Light.stateBillable,
    accentLime = Light.accentLime,
)

internal val TokklDarkExtraColors = TokklExtraColors(
    running = Dark.stateRunning,
    billable = Dark.stateBillable,
    accentLime = Dark.accentLime,
)
