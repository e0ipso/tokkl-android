package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokkl's palette. Every value comes from the first :root block of
// ../tokkl/packages/web/src/styles.css, the source of truth for the app's colours
// (docs/PRD.md, Visual direction, Colour). styles.css writes each token once as
// light-dark(light, dark), and the comment on each value names its token there. Find a token by
// name, not by line. res/values/colors.xml and res/values-night/colors.xml repeat --bg for the
// window behind Compose; change them with it.

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
    val textureGlow: Color,
    val textureSwell: Color,
    val textureStripe: Color,
    val sceneGlow: Color,
    val sceneSwell: Color,
    val sceneStripe: Color,
    val wordmarkHalo: Color,
    val chartBillable: Color,
    val chartOther: Color,
    val chartOtherWash: Color,
    val chartBand: Color,
    val projectMix: Color,
)

private val Light = Tokens(
    bg = Color(0xFFF4E7CF), // --bg
    surface = Color(0xFFFFFAF1), // --surface
    surface2 = Color(0xFFECDCBF), // --surface-2
    text = Color(0xFF1C1A2B), // --text
    muted = Color(0xFF5F5242), // --muted
    border = Color(0xFF8F7B5F), // --border
    primary = Color(0xFFA8380A), // --primary
    onPrimary = Color(0xFFFFFFFF), // --on-primary
    secondary = Color(0xFF00727C), // --secondary
    accentPink = Color(0xFFD81B72), // --accent-pink
    accentLime = Color(0xFF4F7A00), // --accent-lime
    danger = Color(0xFFB3261E), // --danger
    onDanger = Color(0xFFFFFFFF), // --on-danger
    dangerSoft = Color(0xFFFBE0D9), // --danger-soft
    backdrop = Color(0xFF1C1A2B), // --backdrop, rgb(28 26 43) without its 0.45 alpha
    stateRunning = Color(0xFFD81B72), // --state-running, which is --accent-pink
    stateSelected = Color(0xFFCDEEEA), // --state-selected
    stateBillable = Color(0xFF4A7C00), // --state-billable
    textureGlow = Color(0xFFF3DFD1), // --texture-glow
    textureSwell = Color(0xFFE0EAE3), // --texture-swell
    textureStripe = Color(0xFFC7DCD7), // --texture-stripe
    sceneGlow = Color(0xFFDFB698), // --scene-glow
    sceneSwell = Color(0xFFB9CBBB), // --scene-swell
    sceneStripe = Color(0xFFE9CEB3), // --scene-stripe
    wordmarkHalo = Color.Transparent, // --wordmark-halo, transparent: only dark mode draws the halo
    chartBillable = Color(0xFF0091A8), // --chart-billable
    chartOther = Color(0xFFE0601D), // --chart-other
    chartOtherWash = Color(0xFFF6CCB1), // --chart-other-wash
    chartBand = Color(0xFFF6EBD8), // --chart-band
    projectMix = Color(0xFF000000), // --project-mix
)

private val Dark = Tokens(
    bg = Color(0xFF0B1026), // --bg
    surface = Color(0xFF141A36), // --surface
    surface2 = Color(0xFF1D2547), // --surface-2
    text = Color(0xFFF3F1FF), // --text
    muted = Color(0xFFA9B0D6), // --muted
    border = Color(0xFF6A76A8), // --border
    primary = Color(0xFFFF8C42), // --primary
    onPrimary = Color(0xFF0B1026), // --on-primary
    secondary = Color(0xFF2EE6E6), // --secondary
    accentPink = Color(0xFFFF4FA8), // --accent-pink
    accentLime = Color(0xFFB6FF3B), // --accent-lime
    danger = Color(0xFFFF6B6B), // --danger
    onDanger = Color(0xFF0B1026), // --on-danger
    dangerSoft = Color(0xFF3D1626), // --danger-soft
    backdrop = Color(0xFF030512), // --backdrop, rgb(3 5 18) without its 0.6 alpha
    stateRunning = Color(0xFFFF4FA8), // --state-running, which is --accent-pink
    stateSelected = Color(0xFF1D3B52), // --state-selected
    stateBillable = Color(0xFFB6FF3B), // --state-billable
    textureGlow = Color(0xFF352A38), // --texture-glow
    textureSwell = Color(0xFF17324B), // --texture-swell
    textureStripe = Color(0xFF1A475D), // --texture-stripe
    sceneGlow = Color(0xFF4F332E), // --scene-glow
    sceneSwell = Color(0xFF134354), // --scene-swell
    sceneStripe = Color(0xFF2D212A), // --scene-stripe
    wordmarkHalo = Color(0xFFA9B0D6), // --wordmark-halo
    chartBillable = Color(0xFF0F9BB3), // --chart-billable
    chartOther = Color(0xFFE46A2A), // --chart-other
    chartOtherWash = Color(0xFF523232), // --chart-other-wash
    chartBand = Color(0xFF19203F), // --chart-band
    projectMix = Color(0xFFFFFFFF), // --project-mix
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
 *
 * The texture and scene colours are the stops of gradients behind the top bar strip and the
 * sign-in scene. Draw them as gradients, never as images, and never behind an input or data.
 */
@Immutable
class TokklExtraColors(
    /** A running time entry. Readable as text on surface, and as a mark on any surface. */
    val running: Color,
    /** A billable time entry. A mark only, never text. */
    val billable: Color,
    /** The lime accent. A mark only, never text. */
    val accentLime: Color,
    /** `--texture-glow`, the sunset glow over surface behind the top bar strip. */
    val textureGlow: Color,
    /** `--texture-swell`, the striped swell's base over surface behind the top bar strip. */
    val textureSwell: Color,
    /** `--texture-stripe`, the swell's stripes over surface behind the top bar strip. */
    val textureStripe: Color,
    /** `--scene-glow`, the sunset glow over bg behind the sign-in scene and the splash. */
    val sceneGlow: Color,
    /** `--scene-swell`, the swell's base over bg behind the sign-in scene and the splash. */
    val sceneSwell: Color,
    /** `--scene-stripe`, the stripes over bg behind the sign-in scene and the splash. */
    val sceneStripe: Color,
    /** `--wordmark-halo`, the 1dp outline around the wordmark. Transparent in light. */
    val wordmarkHalo: Color,
    /** `--chart-billable`, a report chart's billable time, drawn solid. A mark only, never text. */
    val chartBillable: Color,
    /** `--chart-other`, the stripes of a report chart's non-billable time. A mark only, never text. */
    val chartOther: Color,
    /** `--chart-other-wash`, the wash behind [chartOther]'s stripes. */
    val chartOtherWash: Color,
    /**
     * `--chart-band`, the faint track behind a report chart's bars. [chartBillable] and
     * [chartOther] reach 3:1 on it in light and dark, which they do not on surface-2 in light.
     */
    val chartBand: Color,
    /**
     * `--project-mix`, what a project's colour is mixed with, 40% of it, for its dot, so every
     * colour of Tokkl's project palette reaches 3:1 as a mark. Black in light, white in dark.
     */
    val projectMix: Color,
)

internal val TokklLightExtraColors = TokklExtraColors(
    running = Light.stateRunning,
    billable = Light.stateBillable,
    accentLime = Light.accentLime,
    textureGlow = Light.textureGlow,
    textureSwell = Light.textureSwell,
    textureStripe = Light.textureStripe,
    sceneGlow = Light.sceneGlow,
    sceneSwell = Light.sceneSwell,
    sceneStripe = Light.sceneStripe,
    wordmarkHalo = Light.wordmarkHalo,
    chartBillable = Light.chartBillable,
    chartOther = Light.chartOther,
    chartOtherWash = Light.chartOtherWash,
    chartBand = Light.chartBand,
    projectMix = Light.projectMix,
)

internal val TokklDarkExtraColors = TokklExtraColors(
    running = Dark.stateRunning,
    billable = Dark.stateBillable,
    accentLime = Dark.accentLime,
    textureGlow = Dark.textureGlow,
    textureSwell = Dark.textureSwell,
    textureStripe = Dark.textureStripe,
    sceneGlow = Dark.sceneGlow,
    sceneSwell = Dark.sceneSwell,
    sceneStripe = Dark.sceneStripe,
    wordmarkHalo = Dark.wordmarkHalo,
    chartBillable = Dark.chartBillable,
    chartOther = Dark.chartOther,
    chartOtherWash = Dark.chartOtherWash,
    chartBand = Dark.chartBand,
    projectMix = Dark.projectMix,
)
